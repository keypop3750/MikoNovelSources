package yokai.extension.novel.en.lightnovelpub

import yokai.extension.novel.lib.*

/**
 * Source for LightNovelPub (lightnovelpub.com)
 * High-quality translations of light novels.
 * 
 * WARNING: This site has aggressive Cloudflare protection.
 * May require WebView to bypass protection.
 * 
 * The site's search uses AJAX with CSRF tokens which makes it complex.
 * Browse pages work better than search.
 */
class LightNovelPub : ConfigurableNovelSource() {
    
    override val id: Long = 6012L
    override val name: String = "LightNovelPub"
    override val baseUrl: String = "https://www.lightnovelpub.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 3000L  // Heavy rate limiting due to Cloudflare
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "li.novel-item",
        searchTitleSelector = "a",
        searchCoverSelector = "img",
        coverAttribute = "data-src",
        
        // Browse selectors for main page
        browseItemSelector = "li.novel-item",
        browseTitleSelector = "a[title]",
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
        chapterListSelector = "ul.chapter-list li a",
        
        // Chapter content selectors
        chapterContentSelector = "div#chapter-container",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
        contentRemovePatterns = listOf(
            "lightnovelpub",
            "The source of this content is"
        ),
        
        // URL patterns - Browse pages work better than search
        searchUrlPattern = "", // Search requires complex AJAX with tokens
        popularUrlPattern = "https://www.lightnovelpub.com/browse/genre-all-25060123/order-popular/status-all?page={page}",
        latestUrlPattern = "https://www.lightnovelpub.com/browse/genre-all-25060123/order-new/status-all?page={page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search to use browse and filter locally
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        // Use popular page and filter by query
        val document = getDocument("$baseUrl/browse/genre-all-25060123/order-popular/status-all?page=1")
        
        val queryLower = query.lowercase()
        
        return document.select("li.novel-item").mapNotNull { element ->
            val link = element.selectFirst("a[title]") ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }
            val url = link.attr("href")
            
            // Filter by query
            if (!title.lowercase().contains(queryLower)) {
                return@mapNotNull null
            }
            
            val coverUrl = element.selectFirst("figure.novel-cover img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getChapterList for LightNovelPub's paginated chapters
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        // Chapters are on a separate /chapters page
        val chaptersUrl = "$novelUrl/chapters"
        val document = getDocument(chaptersUrl)
        val allChapters = mutableListOf<NovelChapter>()
        
        // Parse chapters from the first page
        document.select("ul.chapter-list li a").forEach { element ->
            val href = element.attr("href")
            val title = element.selectFirst("strong.chapter-title")?.text() 
                ?: element.text().trim()
            
            if (href.isNotBlank() && title.isNotBlank()) {
                allChapters.add(NovelChapter(
                    url = fixUrl(href),
                    name = title,
                    chapterNumber = 0f
                ))
            }
        }
        
        // Check for pagination and fetch more pages (limited to avoid Cloudflare blocks)
        val lastPage = document.select("ul.pagination li a")
            .mapNotNull { it.attr("href").substringAfter("page=").toIntOrNull() }
            .maxOrNull() ?: 1
        
        val maxPages = minOf(lastPage, 5) // Limit to 5 pages to avoid rate limiting
        for (page in 2..maxPages) {
            try {
                val pageDoc = getDocument("$chaptersUrl?page=$page")
                pageDoc.select("ul.chapter-list li a").forEach { element ->
                    val href = element.attr("href")
                    val title = element.selectFirst("strong.chapter-title")?.text() 
                        ?: element.text().trim()
                    
                    if (href.isNotBlank() && title.isNotBlank()) {
                        allChapters.add(NovelChapter(
                            url = fixUrl(href),
                            name = title,
                            chapterNumber = 0f
                        ))
                    }
                }
            } catch (e: Exception) {
                break // Stop on error (likely Cloudflare)
            }
        }
        
        // Assign chapter numbers
        return allChapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
