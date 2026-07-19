package yokai.extension.novel.en.scribblehub

import yokai.extension.novel.lib.*

/**
 * Source for Scribble Hub (scribblehub.com)
 * Popular platform for original web fiction and fan translations.
 *
 * CLOUDFLARE PROTECTION: This site is behind Cloudflare and requires a valid
 * cf_clearance cookie to access pages. The host app's CloudflareInterceptor
 * (an OkHttp application interceptor) handles the WebView-based challenge bypass
 * automatically — no special handling is needed in this extension as long as
 * the interceptor is registered on the shared HTTP client.
 *
 * Scribblehub has ADVANCED filter support:
 * - Genres with include/exclude
 * - Content warnings with include/exclude (Gore, Sexual Content, Strong Language)
 * - Multiple sort options
 * - Status filter
 */
class Scribblehub : NovelSource() {
    
    override val id: Long = 6001L
    override val name: String = "Scribblehub"
    override val baseUrl: String = "https://www.scribblehub.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    /**
     * Declare this source's filtering capabilities.
     * Scribblehub has the most advanced filter support of all sources!
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "views", "rating", "last_updated", "newest", "alphabetical"),
        supportsSortDirection = true,
        supportedGenres = genreList,
        supportsGenreExclusion = true,  // Scribblehub supports genre exclusion!
        supportedStatuses = listOf("ongoing", "completed", "hiatus"),
        supportedContentWarnings = listOf("graphic_violence", "sexual_content", "profanity"),
        supportsContentWarningExclusion = true,  // Can exclude content warnings!
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    // Scribblehub genre IDs
    private val genreList = listOf(
        "action", "adult", "adventure", "boys_love", "comedy", "drama", "ecchi",
        "fanfiction", "fantasy", "gender_bender", "girls_love", "harem", "historical",
        "horror", "isekai", "josei", "litrpg", "martial_arts", "mature", "mecha",
        "mystery", "psychological", "romance", "school_life", "sci_fi", "seinen",
        "slice_of_life", "smut", "sports", "supernatural", "tragedy"
    )
    
    // Map genre query values to Scribblehub IDs
    private val genreIdMap = mapOf(
        "action" to 1, "adult" to 2, "adventure" to 3, "boys_love" to 4, "comedy" to 5,
        "drama" to 6, "ecchi" to 7, "fanfiction" to 8, "fantasy" to 9, "gender_bender" to 10,
        "girls_love" to 11, "harem" to 12, "historical" to 13, "horror" to 14, "isekai" to 15,
        "josei" to 16, "litrpg" to 17, "martial_arts" to 18, "mature" to 19, "mecha" to 20,
        "mystery" to 21, "psychological" to 22, "romance" to 23, "school_life" to 24, "sci_fi" to 25,
        "seinen" to 26, "slice_of_life" to 27, "smut" to 28, "sports" to 29, "supernatural" to 30,
        "tragedy" to 31
    )
    
    // Sort mapping
    private val sortMap = mapOf(
        "popular" to 5,      // Total Ranking
        "views" to 1,        // Page Views
        "rating" to 6,       // Readers
        "last_updated" to 2, // Last Update
        "newest" to 3,       // Date Added
        "alphabetical" to 4  // Alphabetical
    )
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=fictionposts&paged=$page"
        val document = getDocument(url)
        
        return document.select("div.search_main_box").map { element ->
            val titleElement = element.selectFirst("div.search_title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = titleElement?.attr("href") ?: ""
            val coverUrl = fixUrlOrNull(element.selectFirst("div.search_img img")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("div.fic_title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.fic_image img")?.attr("src"))
        val description = document.selectFirst("div.wi_fic_desc")?.text()
        val author = document.selectFirst("span.auth_name_fic")?.text()
        
        val genres = document.select("span.wi_fic_genre a, span.wi_fic_showtags a").map { it.text() }
        
        val statusText = document.selectFirst("span.rnd_stats")?.text()
        val status = parseNovelStatus(statusText)
        
        val ratingText = document.selectFirst("span.cnt_rte")?.text()
        val rating = ratingText?.toFloatOrNull()
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status,
            rating = rating
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)

        // Find the story ID for chapter list API - try multiple selectors
        val storyId = document.selectFirst("input#mypostid")?.attr("value")
            ?: document.selectFirst("input[name=mypostid]")?.attr("value")
            ?: document.selectFirst("[data-story-id]")?.attr("data-story-id")
            ?: return emptyList()

        // Scribblehub uses AJAX to load chapters.
        // The correct action is "sf_get_fic_chapters" with "storyID" param.
        // Some older themes use "wi_gettocchp" with "strSID" — try both.
        val chapterListUrl = "$baseUrl/wp-admin/admin-ajax.php"
        val chapters = mutableListOf<NovelChapter>()

        // Try the primary AJAX action: sf_get_fic_chapters
        try {
            val response = postForm(
                chapterListUrl,
                mapOf(
                    "action" to "sf_get_fic_chapters",
                    "storyID" to storyId
                ),
                mapOf(
                    "Referer" to novelUrl,
                    "X-Requested-With" to "XMLHttpRequest"
                )
            )

            val chapterDoc = parseHtml(response)
            // The response contains <a href*='/read/'> links
            val chapterLinks = chapterDoc.select("a[href*='/read/']")

            if (chapterLinks.isNotEmpty()) {
                chapterLinks.forEachIndexed { index, linkElement ->
                    val chapterTitle = linkElement.text().ifBlank { "Chapter ${index + 1}" }
                    val chapterUrl = linkElement.attr("href")

                    // Try to extract date from the link's parent or title attribute
                    val dateText = linkElement.attr("title")?.ifBlank { null }
                        ?: linkElement.parent()?.selectFirst("time")?.attr("datetime")?.ifBlank { null }
                    val dateUpload = parseDate(dateText) ?: 0L

                    chapters.add(NovelChapter(
                        url = chapterUrl,
                        name = chapterTitle,
                        dateUpload = dateUpload,
                        chapterNumber = (index + 1).toFloat()
                    ))
                }
                return chapters.reversed()
            }
        } catch (e: Exception) {
            android.util.Log.w("Scribblehub", "sf_get_fic_chapters AJAX failed: ${e.message}")
        }

        // Fallback: try the legacy AJAX action with pagination: wi_gettocchp
        var page = 1
        while (true) {
            val response = try {
                postForm(
                    chapterListUrl,
                    mapOf(
                        "action" to "wi_gettocchp",
                        "strSID" to storyId,
                        "page" to page.toString()
                    ),
                    mapOf(
                        "Referer" to novelUrl,
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                )
            } catch (e: Exception) {
                break
            }

            val chapterDoc = parseHtml(response)
            val chapterElements = chapterDoc.select("li.li_toc")

            if (chapterElements.isEmpty()) break

            chapterElements.forEach { element ->
                val linkElement = element.selectFirst("a")
                val chapterTitle = linkElement?.text() ?: "Chapter ${chapters.size + 1}"
                val chapterUrl = linkElement?.attr("href") ?: ""

                val dateText = element.selectFirst("time")?.attr("datetime")?.ifBlank { null }
                    ?: element.selectFirst(".toc_wat, [title]")?.attr("title")?.ifBlank { null }
                    ?: element.selectFirst(".date, .chapter-date")?.text()?.ifBlank { null }
                val dateUpload = parseDate(dateText) ?: 0L

                chapters.add(NovelChapter(
                    url = chapterUrl,
                    name = chapterTitle,
                    dateUpload = dateUpload,
                    chapterNumber = (chapters.size + 1).toFloat()
                ))
            }

            page++
            if (page > 100) break // Safety limit
        }

        return chapters.reversed()
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val chapterContent = document.selectFirst("div.chp_raw")
        
        // Remove author notes and ads
        chapterContent?.select("div.wi_authornotes, div.code-block, script, style")?.remove()
        
        return chapterContent?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/series-ranking/?sort=5&order=1&pg=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/series-ranking/?sort=1&order=1&pg=$page"
        return parseNovelList(url)
    }
    
    /**
     * Browse novels with advanced filtering support.
     * Scribblehub's series-finder supports:
     * - Genre include/exclude (gi=1,2,3 / ge=4,5)
     * - Content warnings include/exclude
     * - Multiple sort options with direction
     * - Status filter
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val params = mutableListOf<String>()
        params.add("sf=1")  // Enable series finder
        
        // Sort option
        val sort = filters["sort"] ?: "popular"
        val sortValue = sortMap[sort] ?: 5
        params.add("sort=$sortValue")
        
        // Sort direction (1=desc, 2=asc)
        val order = if (filters["order"] == "asc") 2 else 1
        params.add("order=$order")
        
        // Status filter
        filters["status"]?.let { status ->
            val statusValue = when (status.lowercase()) {
                "ongoing" -> 1
                "hiatus" -> 2
                "completed" -> 3
                else -> null
            }
            statusValue?.let { params.add("status=$it") }
        }
        
        // Included genres
        filters["genres"]?.let { genres ->
            val genreIds = genres.split(",")
                .mapNotNull { genreIdMap[it.trim()] }
            if (genreIds.isNotEmpty()) {
                params.add("gi=${genreIds.joinToString(",")}")
                params.add("mgi=and")  // All genres must match
            }
        }
        
        // Excluded genres
        filters["exclude_genres"]?.let { excludeGenres ->
            val excludeIds = excludeGenres.split(",")
                .mapNotNull { genreIdMap[it.trim()] }
            if (excludeIds.isNotEmpty()) {
                params.add("ge=${excludeIds.joinToString(",")}")
            }
        }
        
        // Content warnings - included
        filters["include_content_warnings"]?.let { warnings ->
            warnings.split(",").forEach { warning ->
                when (warning.trim()) {
                    "graphic_violence" -> params.add("cp=1")  // Gore
                    "sexual_content" -> params.add("cp=2")    // Sexual Content
                    "profanity" -> params.add("cp=3")         // Strong Language
                }
            }
        }
        
        // Content warnings - excluded (Scribblehub uses different params for exclude)
        filters["exclude_content_warnings"]?.let { warnings ->
            warnings.split(",").forEach { warning ->
                when (warning.trim()) {
                    "graphic_violence" -> params.add("cpe=1")
                    "sexual_content" -> params.add("cpe=2")
                    "profanity" -> params.add("cpe=3")
                }
            }
        }
        
        // Page
        params.add("pg=$page")
        
        val url = "$baseUrl/series-finder/?${params.joinToString("&")}"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.search_main_box").map { element ->
            val titleElement = element.selectFirst("div.search_title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = titleElement?.attr("href") ?: ""
            val coverUrl = fixUrlOrNull(element.selectFirst("div.search_img img")?.attr("src"))
            val ratingText = element.selectFirst("span.search_ratings")?.text()
            val rating = ratingText?.toFloatOrNull()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                rating = rating
            )
        }
    }
}
