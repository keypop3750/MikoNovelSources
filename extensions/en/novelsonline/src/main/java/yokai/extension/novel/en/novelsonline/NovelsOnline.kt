package yokai.extension.novel.en.novelsonline

import yokai.extension.novel.lib.*

/**
 * Source for NovelsOnline (novelsonline.org)
 * Note: Uses Cloudflare protection - may require special handling.
 * 
 * Supported filters:
 * - Sort: Popular (top), Latest
 */
class NovelsOnline : ConfigurableNovelSource() {
    
    override val id: Long = 6013L
    override val name: String = "NovelsOnline"
    override val baseUrl: String = "https://novelsonline.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L
    
    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "last_updated"),
        supportsSortDirection = false,
        supportedGenres = emptyList(),  // Has genres but complex structure
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
     * NovelsOnline supports top novels and latest release.
     */
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        return parseNovelList(getDocument("$baseUrl/top-novel"))
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        val document = getDocument("$baseUrl/latest-updates")
        val seenUrls = mutableSetOf<String>()
        return document.select("div.list-by-word-body ul li a").mapNotNull { link ->
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null
            val fullUrl = fixUrl(href)
            // Extract novel URL from chapter URL: /novel-slug/chapter-N -> /novel-slug
            val novelUrl = fullUrl.substringBeforeLast("/chapter-")
                .substringBeforeLast("/volume-")
            if (novelUrl.isBlank() || novelUrl == fullUrl) return@mapNotNull null
            if (!seenUrls.add(novelUrl)) return@mapNotNull null // deduplicate
            val text = link.text()
            val title = text.substringBefore(" Ch. ")
                .substringBefore(" Vol. ")
                .trim()
                .ifBlank { return@mapNotNull null }
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = null
            )
        }
    }

    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"] ?: "popular"
        return when (sort) {
            "last_updated" -> getLatestUpdates(page)
            else -> getPopularNovels(page)
        }
    }
    
    /**
     * Parse novel list from browse pages.
     */
    private suspend fun parseNovelList(document: org.jsoup.nodes.Document): List<NovelSearchResult> {
        return document.select("div.top-novel-block").mapNotNull { block ->
            val titleElement = block.selectFirst("div.top-novel-header h2 a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val url = titleElement.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            val coverUrl = block.selectFirst("div.top-novel-cover img")
                ?.attr("src")
                ?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "div.top-novel-block",
        searchTitleSelector = "div.top-novel-header h2 a",
        searchCoverSelector = "div.top-novel-cover img",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.top-novel-block",
        browseTitleSelector = "div.top-novel-header h2 a",
        browseCoverSelector = "div.top-novel-cover img",
        
        // Novel details selectors
        detailTitleSelector = "div.novel-detail-header h1",
        detailCoverSelector = "div.novel-cover img",
        descriptionSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Description)) div.content",
        authorSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Author)) div.content a",
        genreSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Genre)) div.content a",
        statusSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Status)) div.content",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-chs li a",
        
        // Chapter content selectors
        chapterContentSelector = "#contentall",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.novel-detail-item"),
        contentRemovePatterns = listOf(
            "novelsonline.org",
            "Your browser does not support JavaScript"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelsonline.org/search-ajax?q={query}",
        popularUrlPattern = "https://novelsonline.org/top-novel/{page}",
        latestUrlPattern = "https://novelsonline.org/latest-updates",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search for POST-based search
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()  // Search doesn't support pagination
        
        val response = postForm(
            "$baseUrl/search-ajax",
            mapOf("q" to query)
        )
        val document = parseHtml(response)
        
        return document.select("li").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val title = link.text()
            val url = link.attr("href")
            
            if (title.isNotBlank() && url.isNotBlank()) {
                NovelSearchResult(
                    title = title,
                    url = fixUrl(url),
                    coverUrl = null
                )
            } else null
        }
    }
}
