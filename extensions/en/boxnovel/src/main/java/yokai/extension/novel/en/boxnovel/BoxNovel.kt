package yokai.extension.novel.en.boxnovel

import yokai.extension.novel.lib.*

/**
 * Source for BoxNovel / NovLove (novlove.com - formerly boxnovel.com)
 * 
 * NOTE: This site has completely changed its structure from the old Madara theme.
 * The new structure uses a simpler layout with different URL patterns.
 * Search uses /novel/{slug} pattern directly, no traditional search API.
 */
class BoxNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6011L
    override val name: String = "BoxNovel"
    override val baseUrl: String = "https://novlove.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors - Required but we override search anyway
        searchItemSelector = "a[href*='/novel/']",
        searchTitleSelector = "a",
        
        // Browse selectors for main page (LATEST RELEASE section)
        browseItemSelector = "div.nov-box",
        browseTitleSelector = "h3 > a, div.title > a",
        browseCoverSelector = "img",
        browseCoverAttribute = "src",
        
        // Novel details selectors
        detailTitleSelector = "h1.title, div.novel-title",
        detailCoverSelector = "div.novel-cover img, div.cover img",
        descriptionSelector = "div.novel-desc, div.summary",
        authorSelector = "div.novel-info span:contains(Author) + a, span.author",
        genreSelector = "div.genres a, span.genre a",
        statusSelector = "div.novel-info span:contains(Status) + span",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-list li a, div.chapter-item a",
        
        // Chapter content selectors
        chapterContentSelector = "div.chapter-content, div.text-left, div.reading-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.code-block"),
        contentRemovePatterns = listOf(
            "boxnovel.com",
            "novlove.com",
            "If you find any errors"
        ),
        
        // URL patterns - Note: NovLove doesn't have a traditional search API
        searchUrlPattern = "", // Search requires custom implementation
        popularUrlPattern = "https://novlove.com/sort/nov-love-hot",
        latestUrlPattern = "https://novlove.com/sort/nov-love-daily-update",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search since NovLove doesn't have a traditional search API
    // We'll scrape the hot novels page and filter by query
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        // Use the hot novels page and filter
        val document = getDocument("$baseUrl/sort/nov-love-hot")
        
        val queryLower = query.lowercase()
        
        return document.select("a[href*='/novel/']").mapNotNull { element ->
            val title = element.text().trim()
            val url = element.attr("href")
            
            // Filter by query
            if (title.isBlank() || !title.lowercase().contains(queryLower)) {
                return@mapNotNull null
            }
            
            // Skip non-novel links
            if (!url.contains("/novel/")) {
                return@mapNotNull null
            }
            
            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = null
            )
        }.distinctBy { it.url }.take(20)
    }
    
    // Override getChapterList for NovLove's structure
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Find all chapter links on the novel page
        val chapters = document.select("a[href*='/chapter']").mapNotNull { element ->
            val url = element.attr("href")
            val name = element.text().trim()
            
            // Filter out non-chapter links
            if (url.isBlank() || name.isBlank() || !url.contains(novelUrl.substringAfterLast("/"))) {
                return@mapNotNull null
            }
            
            NovelChapter(
                url = fixUrl(url),
                name = name,
                chapterNumber = 0f
            )
        }.distinctBy { it.url }
        
        return chapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
    
    // Override getNovelDetails for NovLove's structure  
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1")?.text()?.trim() 
            ?: throw Exception("Could not find novel title")
        
        // Find cover image
        val coverUrl = document.selectFirst("div.novel-cover img, img.novel-img, img[alt*='cover']")
            ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
            ?.let { fixUrl(it) }
        
        // Find description
        val description = document.select("div.novel-desc p, div.summary p")
            .joinToString("\n\n") { it.text() }
            .ifBlank { null }
        
        // Find author
        val author = document.selectFirst("span:contains(Author) + a, a[href*='author']")?.text()?.trim()
        
        // Find genres
        val genres = document.select("a[href*='genres'], a[href*='genre']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        
        // Find status
        val statusText = document.selectFirst("span:contains(Status) + span, span.status")?.text()?.trim()
        val status = when {
            statusText?.contains("Ongoing", ignoreCase = true) == true -> NovelStatus.ONGOING
            statusText?.contains("Completed", ignoreCase = true) == true -> NovelStatus.COMPLETED
            else -> NovelStatus.UNKNOWN
        }
        
        return NovelDetails(
            url = url,
            title = title,
            coverUrl = coverUrl,
            description = description,
            author = author,
            genres = genres,
            status = status
        )
    }
}
