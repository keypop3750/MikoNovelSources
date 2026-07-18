package yokai.extension.novel.en.ranobes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import yokai.extension.novel.lib.ChapterPageResult
import yokai.extension.novel.lib.NovelChapter
import yokai.extension.novel.lib.NovelComment
import yokai.extension.novel.lib.NovelDetails
import yokai.extension.novel.lib.NovelSearchResult
import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelStatus
import yokai.extension.novel.lib.SourceCapabilities
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Ranobes : NovelSource() {

    override val id: Long = 6022L
    override val name: String = "Ranobes"
    override val baseUrl: String = "https://ranobes.net"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 5000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "latest"),
        supportsSortDirection = false,
        supportedGenres = emptyList(),
        supportsGenreExclusion = false,
        supportedStatuses = emptyList(),
        supportedContentWarnings = emptyList(),
        supportsContentWarningExclusion = false,
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false,
        supportsComments = true
    )

    // ===== Search =====

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val postData = mapOf(
            "do" to "search",
            "subaction" to "search",
            "story" to query,
            "search_start" to page.toString()
        )
        val html = postForm("$baseUrl/index.php?do=search", postData)
        val doc = Jsoup.parse(html, baseUrl)

        return doc.select("article.shortstory").map { el ->
            val titleLink = el.selectFirst("h2.title a") ?: el.selectFirst(".title a")
            val title = titleLink?.text() ?: ""
            val url = titleLink?.absUrl("href") ?: ""
            val coverEl = el.selectFirst(".poster figure.cover")
            val coverUrl = coverEl?.attr("style")
                ?.substringAfter("background-image: url(")
                ?.substringBefore(");")
                ?.removeSurrounding("'")
                ?.removeSurrounding("\"")
            NovelSearchResult(
                title = title,
                url = url,
                coverUrl = coverUrl,
                author = null,
                latestChapter = null,
                rating = null,
                status = null
            )
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        // Ranobes doesn't have a clear "popular" sort, use the main novels listing
        return getBrowsePage("$baseUrl/novels/page/$page/")
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return getBrowsePage("$baseUrl/novels/page/$page/?sort=date")
    }

    private suspend fun getBrowsePage(url: String): List<NovelSearchResult> {
        val doc = getDocument(url)
        return doc.select("article.shortstory").map { el ->
            val titleLink = el.selectFirst("h2.title a") ?: el.selectFirst(".title a")
            val title = titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val coverEl = el.selectFirst(".poster figure.cover")
            val coverUrl = coverEl?.attr("style")
                ?.substringAfter("background-image: url(")
                ?.substringBefore(");")
                ?.removeSurrounding("'")
                ?.removeSurrounding("\"")
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1.title")?.text()?.substringBefore(" by")?.trim() ?: ""
        val author = doc.selectFirst("h1.title .subtitle")?.text()?.trim()
            ?: doc.selectFirst(".r-fullstory .subtitle")?.text()?.trim()
        val cleanAuthor = author?.removePrefix("by ")?.trim()?.takeIf { it.isNotBlank() }

        val coverEl = doc.selectFirst(".r-fullstory-poster figure.cover")
        val coverUrl = coverEl?.attr("style")
            ?.substringAfter("background-image: url(")
            ?.substringBefore(");")
            ?.removeSurrounding("'")
            ?.removeSurrounding("\"")

        // Description: the full text is loaded via JS, so use the meta description tag
        // which has more content than the truncated moreless__short div
        val description = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst(".r-desription .moreless__short")?.text()?.trim()
                ?.replace("Read more", "")?.trim()
            ?: doc.selectFirst(".r-desription .cont-text")?.text()?.trim()

        // Genres from the genre links section (not breadcrumbs which only show category)
        val genres = doc.select("#mc-fs-genre .links a, .fs-genre .links a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && it != "Ranobes" }

        // Status from the info section
        val statusText = doc.selectFirst(".r-fullstory-spec li:contains(Status)")?.text()
            ?: doc.selectFirst(".fs-tab-pane .info li:contains(Status)")?.text()
            ?: doc.selectFirst(".r-fullstory li:contains(Status)")?.text()
        val status = parseStatus(statusText)

        // Rating
        val ratingText = doc.selectFirst(".rate-stat-list .current-rating")?.attr("style")
        val rating = ratingText?.substringAfter("width:")?.substringBefore("%")?.trim()?.toFloatOrNull()?.let { it / 20f }

        return NovelDetails(
            url = url,
            title = title,
            author = cleanAuthor,
            coverUrl = coverUrl,
            description = description,
            genres = genres.distinct(),
            status = status,
            rating = rating
        )
    }

    private fun parseStatus(text: String?): NovelStatus {
        if (text == null) return NovelStatus.UNKNOWN
        val lower = text.lowercase()
        return when {
            "ongoing" in lower || "active" in lower -> NovelStatus.ONGOING
            "completed" in lower || "finished" in lower -> NovelStatus.COMPLETED
            "hiatus" in lower || "paused" in lower -> NovelStatus.ON_HIATUS
            "dropped" in lower || "cancelled" in lower -> NovelStatus.CANCELLED
            else -> NovelStatus.UNKNOWN
        }
    }

    // ===== Chapter List =====

    /**
     * Ranobes embeds chapter data as JSON in the /chapters/{id}/ page.
     * Each page contains 25 chapters. We need to fetch all pages.
     * The JSON is embedded in a <script> tag before the Vue app init.
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val novelId = extractNovelId(novelUrl) ?: return emptyList()
        val chapters = mutableListOf<RanobesChapterJson>()

        // Fetch first page to get total page count (with captcha retry)
        val firstPageHtml = fetchPageWithCaptchaRetry("$baseUrl/chapters/$novelId/")
        val firstPageChapters = parseEmbeddedChapters(firstPageHtml)
        chapters.addAll(firstPageChapters.chapters)

        // Fetch remaining pages with captcha retry logic
        for (page in 2..firstPageChapters.pagesCount) {
            try {
                val pageHtml = fetchPageWithCaptchaRetry("$baseUrl/chapters/$novelId/page/$page/")
                val pageData = parseEmbeddedChapters(pageHtml)
                chapters.addAll(pageData.chapters)
            } catch (_: Exception) {
                // Skip this page after exhausting retries
            }
        }

        // Sort by chapter number, reverse to get first chapter first (Ranobes shows newest first)
        return chapters
            .sortedByDescending { it.id.toLongOrNull() ?: 0L }
            .map { ch ->
                NovelChapter(
                    name = ch.title,
                    url = fixUrl(ch.link),
                    dateUpload = parseRanobesDate(ch.date),
                    chapterNumber = extractChapterNumber(ch.title)
                )
            }
    }

    /**
     * Incremental chapter fetch: fetch the next batch of older chapters beyond what the user already has.
     *
     * Ranobes shows newest chapters first (page 1 = newest, page N = oldest).
     * If the user already has 175 chapters (pages 1-7), we fetch pages 8-14 (the next ~175 older chapters).
     * This avoids re-fetching the entire chapter list when the user just wants more chapters.
     *
     * We also include page 1 to pick up any genuinely new chapters that were published since the last fetch.
     */
    override suspend fun getLatestChapters(novelUrl: String, existingCount: Int): List<NovelChapter> {
        val novelId = extractNovelId(novelUrl) ?: return emptyList()
        val chapters = mutableListOf<RanobesChapterJson>()

        // Fetch first page to get total page count (also picks up any newly published chapters)
        val firstPageHtml = fetchPageWithCaptchaRetry("$baseUrl/chapters/$novelId/")
        val firstPageChapters = parseEmbeddedChapters(firstPageHtml)
        chapters.addAll(firstPageChapters.chapters)

        val totalPages = firstPageChapters.pagesCount
        val chaptersPerPage = 25

        // The user already has existingCount chapters (the newest ones, pages 1..pagesAlreadyHave).
        // We want to fetch the NEXT batch of older chapters: pages (pagesAlreadyHave+1)..(pagesAlreadyHave+batchPages).
        // Fetch the same number of pages as the user already has, to double their library in one go.
        val pagesAlreadyHave = (existingCount + chaptersPerPage - 1) / chaptersPerPage // ceil division
        val batchPages = pagesAlreadyHave.coerceAtLeast(1) // fetch at least 1 page of new content
        val startPage = 2 // we already fetched page 1
        val endPage = minOf(totalPages, pagesAlreadyHave + batchPages)

        // Fetch pages 2..endPage, which includes:
        // - Pages 2..pagesAlreadyHave (re-fetch existing, ensures we don't miss any)
        // - Pages (pagesAlreadyHave+1)..endPage (the new older chapters)
        for (page in startPage..endPage) {
            try {
                val pageHtml = fetchPageWithCaptchaRetry("$baseUrl/chapters/$novelId/page/$page/")
                val pageData = parseEmbeddedChapters(pageHtml)
                chapters.addAll(pageData.chapters)
            } catch (_: Exception) {
                // Skip this page after exhausting retries
            }
        }

        return chapters
            .sortedByDescending { it.id.toLongOrNull() ?: 0L }
            .map { ch ->
                NovelChapter(
                    name = ch.title,
                    url = fixUrl(ch.link),
                    dateUpload = parseRanobesDate(ch.date),
                    chapterNumber = extractChapterNumber(ch.title)
                )
            }
    }

    /**
     * Paginated chapter fetch: get a single page of chapters.
     * Page 1 = newest chapters, page N = oldest chapters.
     */
    override suspend fun getChapterListPage(novelUrl: String, page: Int): ChapterPageResult {
        val novelId = extractNovelId(novelUrl) ?: return ChapterPageResult(emptyList(), 1, page)

        val url = if (page == 1) {
            "$baseUrl/chapters/$novelId/"
        } else {
            "$baseUrl/chapters/$novelId/page/$page/"
        }

        val html = fetchPageWithCaptchaRetry(url)
        val pageData = parseEmbeddedChapters(html)

        val chapters = pageData.chapters.map { ch ->
            NovelChapter(
                name = ch.title,
                url = fixUrl(ch.link),
                dateUpload = parseRanobesDate(ch.date),
                chapterNumber = extractChapterNumber(ch.title)
            )
        }

        return ChapterPageResult(chapters, pageData.pagesCount, page)
    }

    private suspend fun fetchPageWithCaptchaRetry(url: String, maxRetries: Int = 3): String {
        var lastHtml = ""
        for (attempt in 0 until maxRetries) {
            lastHtml = get(url).body?.string() ?: ""
            // Check if we got a captcha page instead of real content
            if (!isCaptchaPage(lastHtml)) {
                return lastHtml
            }
            // Wait longer on each retry: 10s, 20s, 30s
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(10_000L * (attempt + 1))
            }
        }
        return lastHtml // Return whatever we got (may be captcha page with no data)
    }

    private fun isCaptchaPage(html: String): Boolean {
        // If the page has chapter data, it's NOT a captcha page regardless of other strings
        if (html.contains("window.__DATA__")) return false
        // Ranobes captcha pages have title "Just a moment..." and are small (~14KB)
        return html.contains("Just a moment") || html.contains("vb-custom-captcha-shell")
    }

    private fun extractNovelId(url: String): String? {
        // URL format: https://ranobes.net/novels/1205249-shadow-slave-v741610.html
        // Chapter list URL: https://ranobes.net/chapters/1205249/
        val regex = Regex("""novels/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun parseEmbeddedChapters(html: String): RanobesChapterPage {
        // The chapter data is embedded as: window.__DATA__ = {"book_title":"...","chapters":[...],"pages_count":N,...}
        // Extract the JSON between "window.__DATA__ = " and the closing "</script>" tag
        val dataMarker = "window.__DATA__ = "
        val dataStart = html.indexOf(dataMarker)
        if (dataStart < 0) return RanobesChapterPage(emptyList(), 1)

        val jsonStart = dataStart + dataMarker.length
        val scriptEnd = html.indexOf("</script>", jsonStart)
        val jsonEnd = if (scriptEnd > jsonStart) scriptEnd else html.length

        // Trim trailing whitespace/semicolons that may follow the JSON
        var rawJson = html.substring(jsonStart, jsonEnd).trim().trimEnd(';').trim()
        // Ensure we have a balanced JSON object — find the matching closing brace
        if (rawJson.startsWith("{")) {
            var depth = 0
            var endIdx = -1
            for (i in rawJson.indices) {
                when (rawJson[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { endIdx = i; break } }
                }
            }
            if (endIdx > 0) rawJson = rawJson.substring(0, endIdx + 1)
        }

        return try {
            val jsonObj = json.parseToJsonElement(rawJson).jsonObject
            val chapters = jsonObj["chapters"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                RanobesChapterJson(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    date = obj["date"]?.jsonPrimitive?.content ?: "",
                    link = obj["link"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()
            // Try multiple possible field names for page count
            val pagesCount = jsonObj["pages_count"]?.jsonPrimitive?.intOrNull
                ?: jsonObj["pagesCount"]?.jsonPrimitive?.intOrNull
                ?: jsonObj["total_pages"]?.jsonPrimitive?.intOrNull
                ?: jsonObj["totalPages"]?.jsonPrimitive?.intOrNull
                ?: run {
                    // Fallback: parse pagination links from HTML
                    val doc = Jsoup.parse(html)
                    val lastPageLink = doc.select("div.navigation a, .pagination a, nav.pagination a").lastOrNull()
                    val pageRegex = Regex("""/page/(\d+)/""")
                    lastPageLink?.attr("href")?.let { href ->
                        pageRegex.find(href)?.groupValues?.get(1)?.toIntOrNull()
                    } ?: 1
                }
            RanobesChapterPage(chapters, pagesCount)
        } catch (_: Exception) {
            RanobesChapterPage(emptyList(), 1)
        }
    }

    private fun extractChapterNumber(title: String): Float {
        // "Chapter 3104: Broken Sword" -> 3104.0
        val regex = Regex("""Chapter\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        return regex.find(title)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }

    private fun parseRanobesDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        val contentEl = doc.selectFirst("div.text#arrticle") ?: doc.selectFirst("div.text")
            ?: doc.selectFirst("[itemprop=articleBody]")
        // Remove ads and scripts
        contentEl?.select("script, .free-support-top, .free-support-bottom, ins, .ads")?.remove()
        return contentEl?.html() ?: ""
    }

    // ===== Chapter Comments =====

    /**
     * Ranobes embeds comments directly in the chapter page HTML using DLE (DataLife Engine)
     * comment system with schema.org/Comment markup.
     *
     * Structure:
     *   <div class="comment" id="comment-id-{id}" itemscope itemtype="https://schema.org/Comment">
     *     <div class="com_info">
     *       <div class="avatar">... <span class="cover" style="background-image:url(...)">...</span></div>
     *       <div class="com_user">
     *         <strong class="name" itemprop="author"><a ...>USERNAME</a></strong>
     *         <time itemprop="dateCreated" datetime="2026-07-16T19:42:43+01:00">...</time>
     *       </div>
     *       <div class="rate">
     *         <span itemprop="upvoteCount"><span id="comments-likes-id-{id}">N</span></span>
     *       </div>
     *     </div>
     *     <div class="com_content">
     *       <div class="cont-text" itemprop="text">
     *         <div id='comm-id-{id}'>COMMENT TEXT</div>
     *       </div>
     *     </div>
     *   </div>
     *
     * Replies are nested in <ol class="comments-tree-list"> inside the parent <li>.
     */
    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        val doc = getDocument(chapterUrl)
        val commentsList = doc.selectFirst("ol.comments-tree-list") ?: return emptyList()
        return parseCommentTree(commentsList)
    }

    private fun parseCommentTree(listEl: Element): List<NovelComment> {
        val comments = mutableListOf<NovelComment>()
        // Direct children <li> are top-level comments
        for (li in listEl.select("> li.comments-tree-item")) {
            val comment = parseSingleComment(li)
            if (comment != null) {
                // Check for nested replies
                val nestedList = li.selectFirst("> ol.comments-tree-list")
                val replies = if (nestedList != null) parseCommentTree(nestedList) else emptyList()
                comments.add(comment.copy(replyCount = replies.size, replies = replies))
            }
        }
        return comments
    }

    private fun parseSingleComment(li: Element): NovelComment? {
        val commentDiv = li.selectFirst("> div > div.comment") ?: li.selectFirst("div.comment") ?: return null

        val commentId = commentDiv.attr("id").removePrefix("comment-id-")

        // Author name
        val authorEl = commentDiv.selectFirst("[itemprop=name] a")
        val authorName = authorEl?.text() ?: "Unknown"

        // Avatar URL from background-image style
        val avatarEl = commentDiv.selectFirst(".avatar .cover")
        val avatarUrl = avatarEl?.attr("style")
            ?.substringAfter("background-image:url(")
            ?.substringBefore(");")
            ?.removeSurrounding("'")
            ?.removeSurrounding("\"")
            ?.let { if (it.startsWith("//")) "https:$it" else it }

        // Date from datetime attribute
        val dateEl = commentDiv.selectFirst("time[itemprop=dateCreated]")
        val dateStr = dateEl?.attr("datetime") ?: ""
        val dateTimestamp = parseIsoDate(dateStr)

        // Likes from upvoteCount
        val likesEl = commentDiv.selectFirst("[itemprop=upvoteCount] span")
        val likes = likesEl?.text()?.toIntOrNull() ?: 0

        // Comment text from comm-id-{id} div
        val textEl = commentDiv.selectFirst("div[id^=comm-id-]")
        val content = textEl?.html()?.trim() ?: ""

        return NovelComment(
            id = commentId,
            userName = authorName,
            avatarUrl = avatarUrl,
            content = content,
            likes = likes,
            date = dateTimestamp
        )
    }

    private fun parseIsoDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            // 2026-07-16T19:42:43+01:00
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            fmt.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                fmt.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }

    // ===== Internal data classes =====

    @Serializable
    private data class RanobesChapterJson(
        val id: String = "",
        val title: String = "",
        val date: String = "",
        val link: String = ""
    )

    private data class RanobesChapterPage(
        val chapters: List<RanobesChapterJson>,
        val pagesCount: Int
    )
}
