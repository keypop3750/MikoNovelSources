package yokai.extension.novel.en.novelfull

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import yokai.extension.novel.lib.*

/**
 * Source for NovelFull (novelfull.com)
 * Large library of translated Asian novels.
 * 
 * Uses the AllNovel-style template (shared with NovelBin).
 * 
 * IMPORTANT: NovelFull search results show cropped thumbnail images.
 * The detail page has full covers, so we fetch covers from there during browse.
 */
class NovelFull : ConfigurableNovelSource() {
    
    override val id: Long = 6002L
    override val name: String = "NovelFull"
    override val baseUrl: String = "https://novelfull.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 100L
    
    override val selectors = SourceSelectors(
        // Search selectors - AllNovel style
        searchItemSelector = "#list-page > div.col-xs-12.col-sm-12.col-md-9.col-truyen-main.archive > div > div.row",
        searchTitleSelector = "h3.truyen-title > a",
        searchCoverSelector = "div.col-xs-3 > div > img",
        coverAttribute = "src",
        
        // Browse selectors - uses same structure as AllNovelProvider
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
        
        // Chapter list selectors - uses allnovel.org AJAX endpoint
        chapterListSelector = "select > option[value], .list-chapter>li>a",
        novelIdSelector = "#rating",
        novelIdAttribute = "data-novel-id",
        chapterAjaxUrl = "https://allnovel.org/ajax-chapter-option?novelId={id}",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content",
        chapterContentSelectorAlt = "#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
        contentRemovePatterns = listOf(
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know < report chapter > so we can fix it as soon as possible.",
            "If you find any errors ( Ads popup, ads redirect, broken links, non-standard content, etc.. ), Please let us know < report chapter > so we can fix it as soon as possible.",
            "[Updated from F r e e w e b n o v e l. c o m]"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelfull.com/search?keyword={query}&page={page}",
        popularUrlPattern = "https://novelfull.com/most-popular?page={page}",
        latestUrlPattern = "https://novelfull.com/latest-release-novel?page={page}",
        
        // Fetch full covers from detail pages (search/browse thumbnails are cropped)
        fetchFullCoverFromDetails = true
    )
    
    // Override getChapterList to handle NovelFull's paginated chapter list
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
