package yokai.extension.novel.en.novelbin

import yokai.extension.novel.lib.*
import org.jsoup.nodes.Document

/**
 * Source for NovelBin (novelbin.com)
 * Large library of translated novels with multiple genres.
 * 
 * The website has changed to a new domain and HTML structure.
 * Uses custom parsing for the new grid layout.
 * 
 * Supported filters:
 * - Sort: Trending (hot), Latest, Completed
 * - Genres: Action, Adventure, Fantasy, Romance, etc.
 * - Status: Completed
 */
class NovelBin : ConfigurableNovelSource() {
    
    override val id: Long = 6003L
    override val name: String = "NovelBin"
    override val baseUrl: String = "https://novelbin.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("trending", "last_updated"),
        supportsSortDirection = false,
        supportedGenres = genreMap.keys.toList(),
        supportsGenreExclusion = false,
        supportedStatuses = listOf("completed"),
        supportedContentWarnings = emptyList(),
        supportsContentWarningExclusion = false,
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    // Genre mapping for NovelBin
    private val genreMap = mapOf(
        "action" to "action",
        "adventure" to "adventure",
        "adult" to "adult",
        "comedy" to "comedy",
        "drama" to "drama",
        "fantasy" to "fantasy",
        "harem" to "harem",
        "historical" to "historical",
        "horror" to "horror",
        "martial_arts" to "martial-arts",
        "mature" to "mature",
        "mystery" to "mystery",
        "reincarnation" to "reincarnation",
        "romance" to "romance",
        "sci_fi" to "sci-fi",
        "slice_of_life" to "slice-of-life",
        "sports" to "sports",
        "supernatural" to "supernatural",
        "tragedy" to "tragedy",
        "yaoi" to "yaoi",
        "magic" to "magic"
    )
    
    override val selectors = SourceSelectors(
        // Search selectors - list style with rows
        searchItemSelector = "div.list div.row",
        searchTitleSelector = "h3.novel-title a",
        searchCoverSelector = "div.col-xs-3 img",
        coverAttribute = "data-src",
        
        // Browse selectors - same row structure as search
        browseItemSelector = "div.list div.row",
        browseTitleSelector = "h3.novel-title a",
        browseCoverSelector = "div.col-xs-3 img",
        browseCoverAttribute = "data-src",
        
        // Novel details selectors
        detailTitleSelector = "h3.title, h1.title",
        detailCoverSelector = "div.book img, div.pic img",
        detailCoverAttribute = "data-src",
        descriptionSelector = "div.desc-text, div.desc",
        authorSelector = "ul.info > li:nth-child(1) > a, span.author",
        genreSelector = "ul.info > li a[href*='/genre/'], div.genres a",
        statusSelector = "ul.info > li:nth-child(3) > a, span.status",
        
        // Chapter list selectors
        chapterListSelector = "select > option[value], ul.list-chapter li a, .chapter-list li a",
        novelIdSelector = "#rating, input[name='novel_id']",
        novelIdAttribute = "data-novel-id",
        chapterAjaxUrl = "https://novelbin.com/ajax/chapter-archive?novelId={id}",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content, #chr-content, div.chapter-content",
        chapterContentSelectorAlt = "#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", ".ad-container"),
        contentRemovePatterns = listOf(
            "[Updated from F r e e w e b n o v e l. c o m]",
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know",
            "If you find any errors ( Ads popup, ads redirect, broken links, non-standard content, etc.. ), Please let us know",
            "novelbin.me",
            "novelbin.com"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelbin.com/search?keyword={query}",
        popularUrlPattern = "https://novelbin.com/sort/novelbin-hot?page={page}",
        latestUrlPattern = "https://novelbin.com/sort/new-novel?page={page}",
        
        // Transform thumbnail URLs to full cover URLs
        // NovelBin uses /novel_80_113/ for thumbnails and /novel/ for full covers
        coverTransformRegex = Regex("/novel_[0-9]+_[0-9]+/"),
        coverTransformReplacement = "/novel/",
        
        // Don't need to fetch from details since we can transform the URL
        fetchFullCoverFromDetails = false
    )
    
    /**
     * Browse novels with filtering support.
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"]
        val includedGenres = filters["genres"]
        val status = filters["status"]
        
        // Priority 1: Status filter - completed novels
        if (status == "completed") {
            return parseNovelListFromUrl("$baseUrl/sort/novelbin-complete?page=$page")
        }
        
        // Priority 2: Genre filter
        if (!includedGenres.isNullOrBlank()) {
            val firstGenre = includedGenres.split(",").firstOrNull()?.trim()
            val genreUrlName = genreMap[firstGenre] ?: firstGenre
            if (genreUrlName != null) {
                return parseNovelListFromUrl("$baseUrl/novelbin-genres/$genreUrlName?page=$page")
            }
        }
        
        // Priority 3: Sort filter
        val url = when (sort) {
            "trending" -> "$baseUrl/sort/novelbin-hot?page=$page"
            "last_updated" -> "$baseUrl/sort/novelbin-daily-update?page=$page"
            else -> "$baseUrl/sort/novelbin-hot?page=$page"
        }
        
        return parseNovelListFromUrl(url)
    }
    
    private suspend fun parseNovelListFromUrl(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select(selectors.browseItemSelector).mapNotNull { element ->
            try {
                val titleElement = element.selectFirst(selectors.browseTitleSelector) ?: return@mapNotNull null
                val title = titleElement.text().trim()
                val novelUrl = titleElement.attr("href").let { href ->
                    if (href.startsWith("http")) href else baseUrl + href 
                }
                
                val coverElement = element.selectFirst(selectors.browseCoverSelector)
                var coverUrl = coverElement?.attr(selectors.browseCoverAttribute ?: "src")?.let { cover ->
                    when {
                        cover.startsWith("http") -> cover
                        cover.startsWith("/") -> baseUrl + cover
                        else -> null
                    }
                }
                
                // Transform thumbnail to full cover URL
                if (coverUrl != null && selectors.coverTransformRegex != null) {
                    coverUrl = coverUrl.replace(selectors.coverTransformRegex!!, selectors.coverTransformReplacement ?: "")
                }
                
                NovelSearchResult(
                    title = title,
                    url = novelUrl,
                    coverUrl = coverUrl
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
