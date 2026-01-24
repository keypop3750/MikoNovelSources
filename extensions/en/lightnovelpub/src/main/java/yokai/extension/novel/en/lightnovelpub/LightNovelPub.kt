package yokai.extension.novel.en.lightnovelpub

import yokai.extension.novel.lib.*

/**
 * Source for LightNovelPub (lightnovelpub.com)
 * High-quality translations of light novels.
 * Note: This site has aggressive rate limiting.
 */
class LightNovelPub : ConfigurableNovelSource() {
    
    override val id: Long = 6012L
    override val name: String = "LightNovelPub"
    override val baseUrl: String = "https://www.lightnovelpub.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 3000L  // Aggressive rate limiting
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "div.novel-item",
        searchTitleSelector = "h4.novel-title a",
        searchCoverSelector = "figure.novel-cover img",
        coverAttribute = "data-src",
        
        // Browse selectors
        browseItemSelector = "div.novel-item",
        browseTitleSelector = "h4.novel-title a",
        browseCoverSelector = "figure.novel-cover img",
        browseCoverAttribute = "data-src",
        
        // Novel details selectors
        detailTitleSelector = "h1.novel-title",
        detailCoverSelector = "figure.cover img",
        detailCoverAttribute = "data-src",
        descriptionSelector = "div.summary div.content",
        authorSelector = "div.author a span",
        genreSelector = "div.categories ul li a",
        statusSelector = "div.header-stats span:contains(Status) strong",
        ratingSelector = "div.rating-star p strong",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-list li",
        
        // Chapter content selectors
        chapterContentSelector = "div#chapter-container",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "p[class]"),
        contentRemovePatterns = listOf(
            "lightnovelpub",
            "The source of this content is"
        ),
        
        // URL patterns
        searchUrlPattern = "https://www.lightnovelpub.com/search?keyword={query}",
        popularUrlPattern = "https://www.lightnovelpub.com/browse/genre-all-25060123/order-popular/status-all?page={page}",
        latestUrlPattern = "https://www.lightnovelpub.com/browse/genre-all-25060123/order-new/status-all?page={page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override getChapterList for LightNovelPub's paginated chapters
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        val allChapters = mutableListOf<NovelChapter>()
        
        // Parse chapters from the first page
        val chapterElements = document.select("ul.chapter-list li")
        chapterElements.forEach { element ->
            val link = element.selectFirst("a")
            val href = link?.attr("href") ?: return@forEach
            val title = link.selectFirst("strong.chapter-title")?.text() ?: link.text()
            
            allChapters.add(NovelChapter(
                url = fixUrl(href),
                name = title,
                chapterNumber = 0f
            ))
        }
        
        // Check for pagination
        val lastPage = document.select("ul.pagination li a")
            .mapNotNull { it.attr("href").substringAfter("page=").toIntOrNull() }
            .maxOrNull() ?: 1
        
        // Fetch remaining pages (limit to avoid rate limiting)
        val maxPages = minOf(lastPage, 10)
        for (page in 2..maxPages) {
            try {
                val pageDoc = getDocument("$novelUrl?page=$page")
                pageDoc.select("ul.chapter-list li").forEach { element ->
                    val link = element.selectFirst("a")
                    val href = link?.attr("href") ?: return@forEach
                    val title = link.selectFirst("strong.chapter-title")?.text() ?: link.text()
                    
                    allChapters.add(NovelChapter(
                        url = fixUrl(href),
                        name = title,
                        chapterNumber = 0f
                    ))
                }
            } catch (e: Exception) {
                break
            }
        }
        
        // Assign chapter numbers
        return allChapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
