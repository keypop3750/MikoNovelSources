package yokai.extension.novel.en.allnovel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import yokai.extension.novel.lib.*

/**
 * Source for AllNovel (allnovel.org)
 * Large library of translated Asian novels.
 * 
 * NOTE: This site uses the same template/structure as NovelFull.
 * Covers in search results are thumbnails; full covers are fetched from details page.
 */
class AllNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6010L
    override val name: String = "AllNovel"
    override val baseUrl: String = "https://allnovel.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 300L
    
    override val selectors = SourceSelectors(
        // Search selectors - Same structure as NovelFull
        searchItemSelector = "#list-page > div.col-xs-12.col-sm-12.col-md-9.col-truyen-main.archive > div > div.row",
        searchTitleSelector = "h3.truyen-title > a",
        searchCoverSelector = "div.col-xs-3 > div > img",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.list>div.row",
        browseTitleSelector = "div > div > h3.truyen-title > a",
        browseCoverSelector = "div > div > img",
        
        // Novel details selectors
        detailTitleSelector = "h3.title",
        detailCoverSelector = "div.book > img",
        descriptionSelector = "div.desc-text",
        authorSelector = "div.info > div:nth-child(1) > a",
        genreSelector = "div.info > div:nth-child(3) a",
        statusSelector = "div.info > div:nth-child(5) > a",
        
        // Chapter list selectors
        chapterListSelector = ".list-chapter > li > a",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content",
        chapterContentSelectorAlt = "#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
        contentRemovePatterns = listOf(
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know",
            "allnovel.org"
        ),
        
        // URL patterns
        searchUrlPattern = "https://allnovel.org/search?keyword={query}&page={page}",
        popularUrlPattern = "https://allnovel.org/most-popular?page={page}",
        latestUrlPattern = "https://allnovel.org/latest-release-novel?page={page}",
        
        // Fetch full covers from detail pages (search thumbnails are cropped)
        fetchFullCoverFromDetails = true
    )
    
    // Override getChapterList to handle AllNovel's paginated chapter list
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> = coroutineScope {
        val firstPageUrl = "$novelUrl?page=1&per-page=50"
        val firstDocument = getDocument(firstPageUrl)
        
        // Find total pages from pagination
        val lastPageLink = firstDocument.selectFirst("li.last a")?.attr("href")
        val totalPages = lastPageLink?.let {
            Regex("page=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: run {
            val pageNumbers = firstDocument.select(".pagination li a")
                .mapNotNull { it.text().toIntOrNull() }
            pageNumbers.maxOrNull() ?: 1
        }
        
        // Parse first page chapters
        val firstPageChapters = parseChaptersFromDocument(firstDocument)
        
        if (totalPages <= 1) {
            return@coroutineScope firstPageChapters.mapIndexed { index, chapter ->
                chapter.copy(chapterNumber = (index + 1).toFloat())
            }
        }
        
        // Fetch remaining pages in parallel (batch of 5)
        val remainingPages = (2..totalPages.coerceAtMost(200)).toList()
        val batchSize = 5
        val allChapters = mutableListOf<NovelChapter>()
        allChapters.addAll(firstPageChapters)
        
        remainingPages.chunked(batchSize).forEach { batch ->
            val batchResults = batch.map { page ->
                async {
                    try {
                        val url = "$novelUrl?page=$page&per-page=50"
                        val doc = getDocument(url)
                        parseChaptersFromDocument(doc)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
            
            batchResults.forEach { chapters ->
                allChapters.addAll(chapters)
            }
        }
        
        // Remove duplicates and assign chapter numbers
        allChapters.distinctBy { it.url }.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
