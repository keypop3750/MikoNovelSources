package yokai.extension.novel.en.royalroad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import yokai.extension.novel.lib.*

/**
 * Source for Royal Road (royalroad.com)
 * One of the largest English web fiction platforms.
 * 
 * Supported filters:
 * - Sort: Popular, Last Updated, Newest, Rating, Views
 * - Sort Direction: Ascending/Descending
 * - Genres: Action, Adventure, Comedy, etc. (include/exclude via tagsAdd/tagsRemove)
 * - Content Warnings: AI-Assisted, AI-Generated, Graphic Violence, Profanity, Sensitive Content, Sexual Content
 * - Status: Ongoing, Completed, Hiatus, Dropped
 * - Min Chapters (minPages), Max Chapters (maxPages)
 * - Min Rating (minRating 0-50)
 * 
 * NOT SUPPORTED by Royal Road API:
 * - Most Chapters sort (orderby=pages doesn't work)
 * - Alphabetical sort (orderby=name doesn't work)
 * - Trending sort (only via dedicated /fictions/trending endpoint, not search API)
 */
class RoyalRoad : NovelSource() {
    
    override val id: Long = 6000L
    override val name: String = "Royal Road"
    override val baseUrl: String = "https://www.royalroad.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Declare this source's filtering capabilities.
     * The app will use this to show only supported options in the filter UI.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "last_updated", "newest", "rating", "views"),
        supportsSortDirection = true,
        supportedGenres = genreMap.keys.toList() + listOf("ai_assisted", "ai_generated"),
        supportsGenreExclusion = true,
        supportedStatuses = listOf("ongoing", "completed", "hiatus", "dropped"),
        supportedContentWarnings = listOf(
            "ai_assisted",
            "ai_generated",
            "graphic_violence",
            "profanity",
            "sensitive_content",
            "sexual_content"
        ),
        supportsContentWarningExclusion = true,
        supportsChapterCountFilter = true,
        supportsRatingFilter = true,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    // Genre mapping for Royal Road URLs
    private val genreMap = mapOf(
        "action" to "action",
        "adventure" to "adventure",
        "comedy" to "comedy",
        "contemporary" to "contemporary",
        "drama" to "drama",
        "fantasy" to "fantasy",
        "historical" to "historical",
        "horror" to "horror",
        "mystery" to "mystery",
        "psychological" to "psychological",
        "romance" to "romance",
        "satire" to "satire",
        "sci-fi" to "sci_fi",
        "sci_fi" to "sci_fi",
        "short_story" to "one_shot",
        "one_shot" to "one_shot",
        "slice_of_life" to "slice_of_life",
        "supernatural" to "supernatural",
        "thriller" to "thriller",
        "tragedy" to "tragedy",
        // AI Content Tags (also used as content warnings)
        "ai_assisted" to "ai_assisted",
        "ai_generated" to "ai_generated",
        // Popular Tags
        "anti-hero_lead" to "anti-hero_lead",
        "artificial_intelligence" to "artificial_intelligence",
        "attractive_lead" to "attractive_lead",
        "cultivation" to "cultivation",
        "cyberpunk" to "cyberpunk",
        "dungeon" to "dungeon",
        "dystopia" to "dystopia",
        "female_lead" to "female_lead",
        "first_contact" to "first_contact",
        "gamelit" to "gamelit",
        "gender_bender" to "gender_bender",
        "grimdark" to "grimdark",
        "hard_sci-fi" to "hard_sci-fi",
        "harem" to "harem",
        "high_fantasy" to "high_fantasy",
        "isekai" to "isekai",
        "litrpg" to "litrpg",
        "low_fantasy" to "low_fantasy",
        "magic" to "magic",
        "male_lead" to "male_lead",
        "martial_arts" to "martial_arts",
        "multiple_lead" to "multiple_lead",
        "mythos" to "mythos",
        "non-human_lead" to "non-human_lead",
        "portal_fantasy_isekai" to "portal_fantasy_isekai",
        "post_apocalyptic" to "post_apocalyptic",
        "progression" to "progression",
        "reincarnation" to "reincarnation",
        "ruling_class" to "ruling_class",
        "school_life" to "school_life",
        "secret_identity" to "secret_identity",
        "soft_sci-fi" to "soft_sci-fi",
        "space_opera" to "space_opera",
        "sports" to "sports",
        "steampunk" to "steampunk",
        "strategy" to "strategy",
        "strong_lead" to "strong_lead",
        "super_heroes" to "super_heroes",
        "survival" to "survival",
        "time_loop" to "loop",
        "loop" to "loop",
        "time_travel" to "time_travel",
        "urban_fantasy" to "urban_fantasy",
        "villainous_lead" to "villainous_lead",
        "virtual_reality" to "virtual_reality",
        "war_and_military" to "war_and_military",
        "wuxia" to "wuxia",
        "xianxia" to "xianxia",
        "young_adult" to "reader-young_adult"
    )
    
    // Content warning mapping - maps app values to Royal Road URL values
    private val contentWarningMap = mapOf(
        "ai_assisted" to "ai_assisted",
        "ai_generated" to "ai_generated",
        "graphic_violence" to "gore",  // Royal Road uses "gore" for graphic violence
        "profanity" to "profanity",
        "sensitive_content" to "sensitive",
        "sexual_content" to "sexual"
    )
    
    // Sort mapping - maps app sort values to Royal Road orderby parameter values
    // Royal Road search API ONLY supports: popularity, release_date, rating, readers, last_update
    // NOTE: pages and name orderby values do NOT work - they just return default sort
    // So we removed most_chapters and alphabetical as options (they're unsupported by RR)
    private val sortMap = mapOf(
        "popular" to "popularity",
        "last_updated" to "last_update",
        "newest" to "release_date",
        "rating" to "rating",
        "views" to "readers",
        "trending" to "popularity"  // Royal Road doesn't have trending via search, use popularity
        // most_chapters and alphabetical are NOT supported by Royal Road search API
    )
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/search?title=${query.encodeUrl()}&page=$page"
        val document = getDocument(url)
        
        return document.select("div.fiction-list-item").map { element ->
            val titleElement = element.selectFirst("h2.fiction-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            val latestChapter = element.selectFirst("span.chapter-title")?.text()
            val ratingText = element.selectFirst("span.star")?.attr("title")
            val rating = ratingText?.substringBefore(" ")?.toFloatOrNull()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter,
                rating = rating
            )
        }
    }
    
    /**
     * Browse novels with filters.
     * Uses the advanced search endpoint for full filter support.
     * Supports: sort, order, genres, exclude_genres, status, min_chapters, max_chapters, min_rating
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        // Use the search endpoint for proper filter/sort support
        val urlBuilder = StringBuilder("$baseUrl/fictions/search?")
        
        // Build query parameters
        val params = mutableListOf<String>()
        params.add("page=$page")
        
        // Sort order - use orderby parameter
        val sortValue = filters["sort"] ?: "popular"
        val orderByValue = sortMap[sortValue] ?: "popularity"
        params.add("orderby=$orderByValue")
        
        // Sort direction (ascending/descending)
        val orderDir = filters["order"] ?: "desc"
        params.add("dir=$orderDir")
        
        // Genre filters (tagsAdd=genre1,genre2)
        filters["genres"]?.let { genres ->
            val genreValues = genres.split(",").mapNotNull { genreMap[it.trim()] }
            if (genreValues.isNotEmpty()) {
                params.add("tagsAdd=${genreValues.joinToString(",")}")
            }
        }
        
        // Excluded genres (tagsRemove=genre1,genre2)
        filters["exclude_genres"]?.let { excludeGenres ->
            val excludeValues = excludeGenres.split(",").mapNotNull { genreMap[it.trim()] }
            if (excludeValues.isNotEmpty()) {
                params.add("tagsRemove=${excludeValues.joinToString(",")}")
            }
        }
        
        // Status filter
        filters["status"]?.let { status ->
            when (status.lowercase()) {
                "ongoing" -> params.add("status=ONGOING")
                "completed" -> params.add("status=COMPLETED")
                "hiatus" -> params.add("status=HIATUS")
                "dropped" -> params.add("status=DROPPED")
                else -> {} // Unknown status, ignore
            }
        }
        
        // Min/max pages (Royal Road uses pages not chapters)
        filters["min_chapters"]?.toIntOrNull()?.let { min ->
            params.add("minPages=$min")
        }
        filters["max_chapters"]?.toIntOrNull()?.let { max ->
            params.add("maxPages=$max")
        }
        
        // Min rating (Royal Road uses 0-5 scale, internally 0-50)
        filters["min_rating_(0-5)"]?.toFloatOrNull()?.let { rating ->
            val ratingInt = (rating * 10).toInt()
            params.add("minRating=$ratingInt")
        }
        
        // Content warnings - included (show only fictions with these warnings)
        // Royal Road uses tagsAdd for content warning tags too
        filters["include_content_warnings"]?.let { warnings ->
            val warningValues = warnings.split(",").mapNotNull { contentWarningMap[it.trim()] }
            if (warningValues.isNotEmpty()) {
                // Append to existing tagsAdd if any
                val existingTagsAdd = params.find { it.startsWith("tagsAdd=") }
                if (existingTagsAdd != null) {
                    params.remove(existingTagsAdd)
                    val existingTags = existingTagsAdd.removePrefix("tagsAdd=")
                    params.add("tagsAdd=$existingTags,${warningValues.joinToString(",")}")
                } else {
                    params.add("tagsAdd=${warningValues.joinToString(",")}")
                }
            }
        }
        
        // Content warnings - excluded (hide fictions with these warnings)
        // Royal Road uses tagsRemove for content warning exclusion
        filters["exclude_content_warnings"]?.let { warnings ->
            val warningValues = warnings.split(",").mapNotNull { contentWarningMap[it.trim()] }
            if (warningValues.isNotEmpty()) {
                // Append to existing tagsRemove if any
                val existingTagsRemove = params.find { it.startsWith("tagsRemove=") }
                if (existingTagsRemove != null) {
                    params.remove(existingTagsRemove)
                    val existingTags = existingTagsRemove.removePrefix("tagsRemove=")
                    params.add("tagsRemove=$existingTags,${warningValues.joinToString(",")}")
                } else {
                    params.add("tagsRemove=${warningValues.joinToString(",")}")
                }
            }
        }
        
        // Build final URL
        urlBuilder.append(params.joinToString("&"))
        
        val finalUrl = urlBuilder.toString()
        return parseNovelList(finalUrl)
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1.font-white")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.fic-header img.thumbnail")?.attr("src"))
        val description = document.selectFirst("div.description > div.hidden-content")?.text()
        val author = document.selectFirst("span.author > a")?.text()
        
        val genres = document.select("span.tags > a.fiction-tag").map { it.text() }
        
        val statusText = document.selectFirst("span.label-sm")?.text()
        val status = parseNovelStatus(statusText)
        
        val ratingText = document.selectFirst("span.overall-score")?.text()
        val rating = ratingText?.toFloatOrNull()
        
        val statsElements = document.select("div.stats > div.col-sm-6")
        var views: Int? = null
        statsElements.forEach { stat ->
            if (stat.text().contains("Views", ignoreCase = true)) {
                views = stat.selectFirst("span")?.text()
                    ?.replace(",", "")?.replace(".", "")?.toIntOrNull()
            }
        }
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status,
            rating = rating,
            views = views
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Royal Road loads chapters via AJAX, but they're also in a script tag
        val scriptContent = document.select("script").find { 
            it.data().contains("window.chapters") 
        }?.data()
        
        if (scriptContent != null) {
            // Parse from embedded JavaScript
            return parseChaptersFromScript(scriptContent, novelUrl)
        }
        
        // Fallback: parse from HTML table
        return document.select("table#chapters tbody tr").mapIndexed { index, row ->
            val linkElement = row.selectFirst("td:first-child a")
            val chapterTitle = linkElement?.text() ?: "Chapter ${index + 1}"
            val chapterUrl = fixUrl(linkElement?.attr("href") ?: "")
            val dateText = row.selectFirst("td:last-child time")?.attr("datetime")
            val dateUploaded = parseDate(dateText) ?: 0L
            
            NovelChapter(
                url = chapterUrl,
                name = chapterTitle,
                dateUpload = dateUploaded,
                chapterNumber = (index + 1).toFloat()
            )
        }.reversed() // Royal Road lists newest first
    }
    
    private fun parseChaptersFromScript(script: String, novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        
        try {
            // Extract the JSON array from the script
            val jsonStart = script.indexOf("window.chapters = ") + "window.chapters = ".length
            val jsonEnd = script.indexOf(";", jsonStart)
            val jsonStr = script.substring(jsonStart, jsonEnd)
            
            val jsonArray = json.parseToJsonElement(jsonStr).jsonArray
            
            jsonArray.forEachIndexed { index, element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                val url = obj["url"]?.jsonPrimitive?.content?.let { fixUrl(it) } ?: ""
                val date = obj["date"]?.jsonPrimitive?.content?.let { parseDate(it) } ?: 0L
                
                chapters.add(NovelChapter(
                    url = url,
                    name = title,
                    dateUpload = date,
                    chapterNumber = (index + 1).toFloat()
                ))
            }
        } catch (e: Exception) {
            // If parsing fails, return empty and let fallback handle it
        }
        
        return chapters
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val chapterContent = document.selectFirst("div.chapter-content")
        
        // Remove author notes, ads, etc.
        chapterContent?.select("div.author-note, div.advertisement, script, style")?.remove()
        
        return chapterContent?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/best-rated?page=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/latest-updates?page=$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.fiction-list-item").map { element ->
            val titleElement = element.selectFirst("h2.fiction-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            val latestChapter = element.selectFirst("span.chapter-title")?.text()
            val ratingText = element.selectFirst("span.star")?.attr("title")
            val rating = ratingText?.substringBefore(" ")?.toFloatOrNull()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter,
                rating = rating
            )
        }
    }
}
