package yokai.extension.novel.en.lightnovelpub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

class LightNovelPub : NovelSource() {

    override val id: Long = 6023L
    override val name: String = "LightNovelPub"
    override val baseUrl: String = "https://lightnovelpub.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 2500L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "latest"),
        supportsSortDirection = false,
        supportsSearch = true,
        supportsComments = true
    )

    // ===== Search =====

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?title=${query.encodeUrl()}&page=$page"
        val doc = getDocument(url)
        return doc.select("div.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val coverUrl = el.selectFirst("img")?.absUrl("src")
            NovelSearchResult(title = title, url = novelUrl, coverUrl = coverUrl)
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/genre-all/sort-popular/status-all/all-novel?page=$page"
        return getBrowsePage(url)
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/genre-all/sort-new/status-all/all-novel?page=$page"
        return getBrowsePage(url)
    }

    private suspend fun getBrowsePage(url: String): List<NovelSearchResult> {
        val doc = getDocument(url)
        return doc.select("div.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val coverUrl = el.selectFirst("img")?.absUrl("src")
            NovelSearchResult(title = title, url = novelUrl, coverUrl = coverUrl)
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1.title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim() ?: ""

        val coverUrl = doc.selectFirst("div.fixed-img img")?.absUrl("src")
            ?: doc.selectFirst(".novel-cover img")?.absUrl("src")

        val description = doc.selectFirst("div.content")?.text()?.trim()
            ?: doc.selectFirst("div.description")?.text()?.trim()
            ?: doc.selectFirst("[itemprop=description]")?.text()?.trim()

        val author = doc.selectFirst("li:contains(Author) a")?.text()?.trim()
            ?: doc.selectFirst("div.author a")?.text()?.trim()
            ?: doc.selectFirst("[itemprop=author]")?.text()?.trim()

        val genres = doc.select("div.tags a, div.genres a").map { it.text().trim() }
            .filter { it.isNotBlank() }

        val statusText = doc.selectFirst("div.header-stats small:contains(Status)")?.parent()?.text()
            ?: doc.selectFirst("li:contains(Status)")?.text()
        val status = parseNovelStatus(statusText)

        val ratingText = doc.selectFirst("div.rating")?.text()?.substringBefore("/")?.trim()
        val rating = ratingText?.toFloatOrNull()

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres.distinct(),
            status = status,
            rating = rating
        )
    }

    // ===== Chapter List =====

    /**
     * LightNovelPub provides a JSON API for chapter lists.
     * URL: /api/novel/{slug}/chapters
     * Returns: [{ chapterId, title, slug, date, ... }]
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val slug = novelUrl.removeSuffix("/").substringAfterLast("/")
        val apiUrl = "$baseUrl/api/novel/$slug/chapters"

        val response = get(apiUrl)
        val body = response.body?.string() ?: return emptyList()

        return try {
            val chapterArray = json.parseToJsonElement(body).jsonArray
            chapterArray.mapIndexed { index, element ->
                val obj = element.jsonObject
                val chapterTitle = obj["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                val chapterSlug = obj["slug"]?.jsonPrimitive?.content
                    ?: obj["chapterId"]?.jsonPrimitive?.content ?: (index + 1).toString()
                val dateStr = obj["date"]?.jsonPrimitive?.contentOrNull
                val chapterUrl = "$baseUrl/novel/$slug/chapter/$chapterSlug"
                NovelChapter(
                    name = chapterTitle,
                    url = chapterUrl,
                    dateUpload = parseDate(dateStr) ?: 0L,
                    chapterNumber = (index + 1).toFloat()
                )
            }.reversed() // API returns newest first, we want oldest first
        } catch (_: Exception) {
            // Fallback: scrape from HTML
            val doc = getDocument("$novelUrl/chapters")
            doc.select("ul.chapter-list li a").map { a ->
                val name = a.selectFirst(".chapter-title")?.text() ?: a.text()
                val chapterUrl = a.absUrl("href")
                val dateStr = a.selectFirst(".chapter-date")?.text()
                NovelChapter(
                    name = name,
                    url = chapterUrl,
                    dateUpload = parseDate(dateStr) ?: 0L
                )
            }
        }
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        val contentEl = doc.selectFirst("div.chapter-content")
            ?: doc.selectFirst("div#content")
            ?: doc.selectFirst("div.text")
        contentEl?.select("script, .ads, ins.adsbygoogle, iframe, .ad-container")?.remove()
        return contentEl?.html() ?: ""
    }

    // ===== Chapter Comments =====

    /**
     * LightNovelPub provides a JSON API for comments.
     * URL: /api/comments/?comment_type=chapter&commentable_id={id}&sort=newest&page=1&parent_only=true
     * Returns: { comments: [{ id, username, avatar, content, likes, created_at, replies: [...] }] }
     *
     * The chapter_id is found in the chapter page's meta tags or inline JS.
     */
    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        val chapterId = extractChapterId(chapterUrl) ?: return emptyList()
        return fetchComments(chapterId, true)
    }

    private suspend fun extractChapterId(chapterUrl: String): String? {
        val doc = getDocument(chapterUrl)
        // Try meta tag first
        doc.selectFirst("meta[name=chapter-id]")?.attr("content")?.let { return it }
        // Try inline JS: chapter_id = parseInt("12345")
        val html = doc.outerHtml()
        val regex = Regex("""chapter_id\s*=\s*parseInt\(["'](\d+)["']""")
        regex.find(html)?.groupValues?.get(1)?.let { return it }
        // Try data attribute
        doc.selectFirst("[data-chapter-id]")?.attr("data-chapter-id")?.let { return it }
        return null
    }

    private suspend fun fetchComments(chapterId: String, parentOnly: Boolean): List<NovelComment> {
        val params = mutableListOf(
            "comment_type" to "chapter",
            "commentable_id" to chapterId,
            "sort" to "newest",
            "page" to "1"
        )
        if (parentOnly) params.add("parent_only" to "true")

        val urlBuilder = StringBuilder("$baseUrl/api/comments/?")
        params.forEachIndexed { i, (k, v) ->
            if (i > 0) urlBuilder.append("&")
            urlBuilder.append(k).append("=").append(v)
        }

        val response = get(urlBuilder.toString())
        val body = response.body?.string() ?: return emptyList()

        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val commentsArray = jsonObj["comments"]?.jsonArray ?: jsonObj["data"]?.jsonArray ?: return emptyList()
            commentsArray.map { parseComment(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseComment(obj: JsonObject): NovelComment {
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        val username = obj["username"]?.jsonPrimitive?.content
            ?: obj["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown"
        val avatar = obj["avatar"]?.jsonPrimitive?.contentOrNull
            ?: obj["avatar_url"]?.jsonPrimitive?.contentOrNull
        val content = obj["content"]?.jsonPrimitive?.content ?: ""
        val likes = obj["likes"]?.jsonPrimitive?.intOrNull
            ?: obj["like_count"]?.jsonPrimitive?.intOrNull ?: 0
        val dateStr = obj["created_at"]?.jsonPrimitive?.contentOrNull
        val repliesArray = obj["replies"]?.jsonArray
        val replies = repliesArray?.map { parseComment(it.jsonObject) } ?: emptyList()

        return NovelComment(
            id = id,
            userName = username,
            avatarUrl = avatar,
            content = content,
            likes = likes,
            replyCount = replies.size,
            date = parseIsoDate(dateStr),
            replies = replies
        )
    }

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
                try {
                    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    fmt.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) { 0L }
            }
        }
    }
}
