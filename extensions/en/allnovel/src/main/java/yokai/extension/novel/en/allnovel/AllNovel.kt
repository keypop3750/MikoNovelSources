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
 * 
 * Supported filters:
 * - Sort: Trending (hot), Popular, Latest
 * - Genres: Action, Adventure, Fantasy, etc. (via /genre/GenreName)
 * - Status: Completed (via /completed-novel)
 */
class AllNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6010L
    override val name: String = "AllNovel"
    override val baseUrl: String = "https://allnovel.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 300L
    
    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("trending", "popular", "last_updated"),
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
    
    // Genre mapping - maps app genre values to AllNovel URL segments
    private val genreMap = mapOf(
        "action" to "Action",
        "adventure" to "Adventure",
        "comedy" to "Comedy",
        "drama" to "Drama",
        "fantasy" to "Fantasy",
        "harem" to "Harem",
        "historical" to "Historical",
        "horror" to "Horror",
        "martial_arts" to "Martial-arts",
        "mature" to "Mature",
        "mystery" to "Mystery",
        "psychological" to "Psychological",
        "reincarnation" to "Reincarnation",
        "romance" to "Romance",
        "sci_fi" to "Sci-fi",
        "slice_of_life" to "Slice-of-life",
        "supernatural" to "Supernatural",
        "tragedy" to "Tragedy",
        "wuxia" to "Wuxia",
        "xianxia" to "Xianxia",
        "xuanhuan" to "Xuanhuan"
    )
    
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
    
    /**
     * Browse novels with filtering support.
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"]
        val includedGenres = filters["genres"]
        val status = filters["status"]
        
        // Priority 1: Status filter - completed novels
        if (status == "completed") {
            return parseNovelListFromUrl("$baseUrl/completed-novel?page=$page")
        }
        
        // Priority 2: Genre filter
        if (!includedGenres.isNullOrBlank()) {
            val firstGenre = includedGenres.split(",").firstOrNull()?.trim()
            val genreUrlName = genreMap[firstGenre] ?: firstGenre?.replaceFirstChar { it.uppercaseChar() }
            if (genreUrlName != null) {
                return parseNovelListFromUrl("$baseUrl/genre/$genreUrlName?page=$page")
            }
        }
        
        // Priority 3: Sort filter
        val url = when (sort) {
            "trending" -> "$baseUrl/hot-novel?page=$page"
            "popular" -> "$baseUrl/most-popular?page=$page"
            "last_updated" -> "$baseUrl/latest-release-novel?page=$page"
            else -> "$baseUrl/most-popular?page=$page"
        }
        
        return parseNovelListFromUrl(url)
    }
    
    private suspend fun parseNovelListFromUrl(url: String): List<NovelSearchResult> = coroutineScope {
        val document = getDocument(url)
        
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
        
        // Fetch full covers from detail pages if configured
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
