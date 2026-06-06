package yokai.extension.novel.en.boxnovel

import yokai.extension.novel.lib.*

/**
 * Source for BoxNovel / NovLove (novlove.com - formerly boxnovel.com)
 * 
 * NOTE: This site has completely changed its structure from the old Madara theme.
 * The new structure uses a simpler layout with different URL patterns.
 * 
 * Cover images are at https://images.novlove.com/novel_200_89/{slug}.jpg
 * The image appears BEFORE the novel title link in the HTML structure.
 * 
 * Supported filters:
 * - Sort: Trending (hot), Latest
 */
class BoxNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6011L
    override val name: String = "BoxNovel"
    override val baseUrl: String = "https://novlove.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("trending", "last_updated"),
        supportsSortDirection = false,
        supportedGenres = emptyList(),  // No genre browse available
        supportsGenreExclusion = false,
        supportedStatuses = emptyList(),
        supportedContentWarnings = emptyList(),
        supportsContentWarningExclusion = false,
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    /**
     * Browse novels with applied filters.
     * BoxNovel/NovLove supports basic sort options only.
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"] ?: "trending"
        
        return when (sort) {
            "last_updated" -> getLatestUpdates(page)
            else -> getPopularNovels(page)  // Default to trending/hot
        }
    }
    
    // Thumbnail: https://images.novlove.com/novel_200_89/{slug}.jpg
    // Full cover: https://images.novlove.com/novel/{slug}.jpg
    private val thumbnailBaseUrl = "https://images.novlove.com/novel_200_89"
    private val fullCoverBaseUrl = "https://images.novlove.com/novel"
    
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
        
        // URL patterns - NovLove has a search endpoint
        searchUrlPattern = "https://novlove.com/search?keyword={query}",
        popularUrlPattern = "https://novlove.com/sort/nov-love-hot",
        latestUrlPattern = "https://novlove.com/sort/nov-love-daily-update",
        
        // No need to fetch from details - we construct full cover URLs directly
        fetchFullCoverFromDetails = false
    )
    
    /**
     * Build thumbnail cover URL from novel slug (for quick browse display).
     * Pattern: https://images.novlove.com/novel_200_89/{slug}.jpg
     */
    private fun buildThumbnailUrl(slug: String): String {
        return "$thumbnailBaseUrl/$slug.jpg"
    }
    
    /**
     * Build full cover URL from novel slug.
     * Pattern: https://images.novlove.com/novel/{slug}.jpg
     */
    private fun buildFullCoverUrl(slug: String): String {
        return "$fullCoverBaseUrl/$slug.jpg"
    }
    
    /**
     * Extract slug from novel URL.
     * Example: https://novlove.com/novel/shadow-slave -> shadow-slave
     */
    private fun extractSlug(url: String): String {
        return url.removeSuffix("/")
            .substringAfterLast("/novel/")
            .substringBefore("?")
            .substringBefore("/")
    }
    
    // Override getPopularNovels to browse hot novels
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/nov-love-hot"
        } else {
            "$baseUrl/sort/nov-love-hot/$page"
        }
        
        return parseNovelList(url)
    }
    
    // Override getLatestUpdates to browse latest updates
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/nov-love-daily-update"
        } else {
            "$baseUrl/sort/nov-love-daily-update/$page"
        }
        
        return parseNovelList(url)
    }
    
    /**
     * Parse novel list from browse/search pages.
     * The structure is: h3 > a[href*='/novel/'] for title links
     * Cover images are constructed from the slug - using FULL cover URL directly
     * to avoid the 200x89 cropped thumbnails.
     */
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        // Find all novel links (h3 > a pattern)
        return document.select("h3 > a[href*='/novel/']").mapNotNull { element ->
            val title = element.text().trim()
            val novelUrl = element.attr("href")
            
            if (title.isBlank() || novelUrl.isBlank()) {
                return@mapNotNull null
            }
            
            // Build FULL cover URL from slug (not thumbnail) for proper 2:3 display
            // Full covers are at: https://images.novlove.com/novel/{slug}.jpg
            // Thumbnails would be: https://images.novlove.com/novel_200_89/{slug}.jpg
            val slug = extractSlug(novelUrl)
            val coverUrl = buildFullCoverUrl(slug)
            
            NovelSearchResult(
                title = title,
                url = fixUrl(novelUrl),
                coverUrl = coverUrl
            )
        }.distinctBy { it.url }
    }
    
    // Override search to use NovLove's search page
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/search?keyword=${query.encodeUrl()}"
        } else {
            "$baseUrl/search?keyword=${query.encodeUrl()}&page=$page"
        }
        
        return parseNovelList(url)
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
        
        // Build full cover URL from the novel slug
        // Full covers are at: https://images.novlove.com/novel/{slug}.jpg
        val slug = extractSlug(url)
        val coverUrl = buildFullCoverUrl(slug)
        
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
