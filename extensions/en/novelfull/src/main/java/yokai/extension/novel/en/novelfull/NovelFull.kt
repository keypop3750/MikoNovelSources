package yokai.extension.novel.en.novelfull

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import yokai.extension.novel.lib.*

/**
 * Source for NovelFull (novelfull.com)
 * Large library of translated Asian novels.
 * 
 * Uses the AllNovel-style template.
 * 
 * Supported filters:
 * - Sort: Hot, Popular, Latest, Completed (via dedicated pages)
 * - Genres: Action, Adventure, Comedy, etc. (via /genre/GenreName)
 * - Status: Completed only (via /completed-novel)
 * 
 * NOT SUPPORTED by NovelFull:
 * - Genre Exclusion (only inclusion)
 * - Content Warnings (no system for this)
 * - Chapter Count Filter
 * - Rating Filter
 * - Sort Direction (pages are pre-sorted)
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
    
    /**
     * Declare this source's filtering capabilities.
     * The app will use this to show only supported options in the filter UI.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        // Sort options - NovelFull uses dedicated pages for each sort type
        supportedSorts = listOf("trending", "popular", "last_updated"),
        supportsSortDirection = false,
        
        // Genres - all genres available on NovelFull
        supportedGenres = genreMap.keys.toList(),
        supportsGenreExclusion = false,  // NovelFull only supports including genres
        
        // Status - only completed filter via dedicated page
        supportedStatuses = listOf("completed"),
        
        // No content warnings system on NovelFull
        supportedContentWarnings = emptyList(),
        supportsContentWarningExclusion = false,
        
        // No chapter count or rating filters
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    // Genre mapping - maps app genre values to NovelFull URL segments
    private val genreMap = mapOf(
        // Primary genres
        "action" to "Action",
        "adventure" to "Adventure",
        "comedy" to "Comedy",
        "contemporary" to "Contemporary",
        "drama" to "Drama",
        "fantasy" to "Fantasy",
        "historical" to "Historical",
        "horror" to "Horror",
        "mystery" to "Mystery",
        "psychological" to "Psychological",
        "romance" to "Romance",
        "satire" to "Satire",
        "sci_fi" to "Sci-fi",
        "slice_of_life" to "Slice-of-life",
        "supernatural" to "Supernatural",
        "thriller" to "Thriller",
        "tragedy" to "Tragedy",
        
        // Asian novel specific genres
        "xianxia" to "Xianxia",
        "xuanhuan" to "Xuanhuan",
        "wuxia" to "Wuxia",
        "martial_arts" to "Martial-arts",
        "martial" to "Martial",
        
        // Special genres
        "eastern" to "Eastern",
        "mature" to "Mature",
        "yaoi" to "Yaoi",
        "shounen_ai" to "Shounen-ai",
        "josei" to "Josei",
        "shoujo" to "Shoujo",
        "harem" to "Harem",
        "smut" to "Smut",
        "mecha" to "Mecha",
        "seinen" to "Seinen",
        "lolicon" to "Lolicon",
        "adult" to "Adult",
        "ecchi" to "Ecchi",
        "gender_bender_novel" to "Gender-bender",
        "webtoons" to "Webtoons",
        "manhua" to "Manhua",
        
        // Additional genres that might exist
        "school_life" to "School-life",
        "sports" to "Sports",
        "mystery" to "Mystery"
    )
    
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
        
        // Chapter list selectors - NovelFull lists chapters directly on the novel page
        chapterListSelector = ".list-chapter li a",
        chapterListSelectorAlt = "#list-chapter .list-chapter li a",
        
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
    
    /**
     * Browse novels with filtering support.
     * 
     * NovelFull uses dedicated pages for different sorts/filters:
     * - /hot-novel - Hot novels
     * - /most-popular - Popular novels
     * - /latest-release-novel - Latest updates
     * - /completed-novel - Completed novels only
     * - /genre/GenreName - Browse by genre
     * 
     * Priority: Status (completed) > Genre > Sort
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        // Extract filter values
        val sort = filters["sort"]
        val includedGenres = filters["genres"]
        val status = filters["status"]
        
        // Priority 1: Status filter - completed novels
        if (status == "completed") {
            val url = "https://novelfull.com/completed-novel?page=$page"
            return parseNovelListFromUrl(url)
        }
        
        // Priority 2: Genre filter - browse by genre
        if (!includedGenres.isNullOrBlank()) {
            val firstGenre = includedGenres.split(",").firstOrNull()?.trim()
            val genreUrlName = genreMap[firstGenre] ?: firstGenre?.replaceFirstChar { it.uppercaseChar() }
            if (genreUrlName != null) {
                val url = "https://novelfull.com/genre/$genreUrlName?page=$page"
                return parseNovelListFromUrl(url)
            }
        }
        
        // Priority 3: Sort filter - use popularUrlPattern as default
        val defaultUrl = selectors.popularUrlPattern?.replace("{page}", page.toString()) 
            ?: "https://novelfull.com/most-popular?page=$page"
        
        val url = if (sort != null) {
            when (sort) {
                "trending" -> "https://novelfull.com/hot-novel?page=$page"
                "popular" -> "https://novelfull.com/most-popular?page=$page"
                "last_updated" -> "https://novelfull.com/latest-release-novel?page=$page"
                else -> defaultUrl
            }
        } else {
            defaultUrl
        }
        
        return parseNovelListFromUrl(url)
    }
    
    /**
     * Parse a list of novels from the given URL.
     */
    private suspend fun parseNovelListFromUrl(url: String): List<NovelSearchResult> = coroutineScope {
        val document = getDocument(url)
        
        // Try browse selectors first, then search selectors
        var items = document.select(selectors.browseItemSelector)
        if (items.isEmpty() && selectors.searchItemSelector != null) {
            items = document.select(selectors.searchItemSelector!!)
        }
        
        val novels = items.mapNotNull { element ->
            try {
                val titleElement = element.selectFirst(selectors.browseTitleSelector)
                    ?: element.selectFirst(selectors.searchTitleSelector ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                
                val title = titleElement.text().trim()
                val novelUrl = titleElement.attr("href").let { href ->
                    if (href.startsWith("http")) href else baseUrl + href 
                }
                
                // Get thumbnail
                val coverElement = element.selectFirst(selectors.browseCoverSelector)
                    ?: element.selectFirst(selectors.searchCoverSelector ?: "")
                val coverUrl = coverElement?.attr(selectors.coverAttribute)?.let { cover ->
                    when {
                        cover.startsWith("http") -> cover
                        cover.startsWith("/") -> baseUrl + cover
                        else -> null
                    }
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
        
        // Optionally fetch full covers from detail pages
        if (selectors.fetchFullCoverFromDetails && novels.isNotEmpty()) {
            novels.map { novel ->
                async {
                    try {
                        val detailDoc = getDocument(novel.url)
                        val fullCover = detailDoc.selectFirst(selectors.detailCoverSelector)
                            ?.attr(selectors.coverAttribute)
                            ?.let { if (it.startsWith("http")) it else baseUrl + it }
                        if (fullCover != null) novel.copy(coverUrl = fullCover) else novel
                    } catch (e: Exception) {
                        novel
                    }
                }
            }.awaitAll()
        } else {
            novels
        }
    }
}
