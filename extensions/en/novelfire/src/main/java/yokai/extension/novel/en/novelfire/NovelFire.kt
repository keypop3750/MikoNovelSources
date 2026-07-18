package yokai.extension.novel.en.novelfire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import yokai.extension.novel.lib.NovelChapter
import yokai.extension.novel.lib.NovelComment
import yokai.extension.novel.lib.NovelDetails
import yokai.extension.novel.lib.NovelSearchResult
import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelStatus
import yokai.extension.novel.lib.SourceCapabilities

class NovelFire : NovelSource() {

    override val id: Long = 6024L
    override val name: String = "NovelFire"
    override val baseUrl: String = "https://novelfire.net"
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
        return doc.select("li.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val img = el.selectFirst("img")
            val coverUrl = img?.let {
                it.absUrl("data-src").ifBlank { it.absUrl("src") }
            }
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
        return doc.select("li.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val img = el.selectFirst("img")
            val coverUrl = img?.let {
                // Lazy-loaded: real URL is in data-src, src is a placeholder
                it.absUrl("data-src").ifBlank { it.absUrl("src") }
            }
            NovelSearchResult(title = title, url = novelUrl, coverUrl = coverUrl)
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""

        val coverUrl = doc.selectFirst("div.fixed-img img")?.absUrl("src")
            ?: doc.selectFirst("img[alt]")?.absUrl("src")

        val description = doc.selectFirst("div.content")?.text()?.trim()
            ?: doc.selectFirst("div.description")?.text()?.trim()

        val author = doc.selectFirst("li:contains(Author) a")?.text()?.trim()
            ?: doc.selectFirst("div.author a")?.text()?.trim()

        val genres = doc.select("div.tags a, div.genres a").map { it.text().trim() }
            .filter { it.isNotBlank() }

        val statusText = doc.selectFirst("small:contains(Status)")?.parent()?.text()
            ?: doc.selectFirst("li:contains(Status)")?.text()
        // "Ongoing Status" -> extract just the status word
        val cleanedStatusText = statusText?.replace("(?i)Status".toRegex(), "")?.trim()
        val status = parseNovelStatus(cleanedStatusText)

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
     * NovelFire has paginated chapter list at /book/<slug>/chapters?page=N
     * Each page has ~110 chapters.
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        while (true) {
            val url = "$novelUrl/chapters?page=$page"
            val doc = getDocument(url)
            val pageChapters = doc.select("a[href*=chapter]").mapNotNull { a ->
                val href = a.absUrl("href")
                val name = a.text().trim()
                if (href.isNotBlank() && name.isNotBlank() && !name.contains("Chapters")) {
                    NovelChapter(name = name, url = href)
                } else null
            }
            if (pageChapters.isEmpty()) break
            chapters.addAll(pageChapters)
            // Check for next page
            val nextLink = doc.selectFirst("a[rel=next]")
            if (nextLink == null) break
            page++
        }
        // Reverse to get oldest first (pages are newest first)
        return chapters.reversed()
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        val contentEl = doc.selectFirst("div#content")
            ?: doc.selectFirst("div.chapter-content")
            ?: doc.selectFirst("div.text")
        contentEl?.select("script, .ads, ins.adsbygoogle, iframe, .ad-container")?.remove()
        return contentEl?.html() ?: ""
    }

    // ===== Chapter Comments =====

    /**
     * NovelFire loads comments via AJAX: GET /comment/show
     * Params: post_id, chapter_id, order_by, cursor
     * Returns JSON: { html, next_cursor, has_more_pages }
     * The HTML contains <li> elements with comment data.
     *
     * chapter_id and post_id are found in the chapter page's inline JS:
     *   chapter_id=parseInt("58592219"); post_id=parseInt("2196");
     */
    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        val doc = getDocument(chapterUrl)
        val html = doc.outerHtml()

        // Extract chapter_id and post_id from inline JS
        val chapterIdRegex = Regex("""chapter_id\s*=\s*parseInt\(["'](\d+)["']""")
        val postIdRegex = Regex("""post_id\s*=\s*parseInt\(["'](\d+)["']""")
        val chapterId = chapterIdRegex.find(html)?.groupValues?.get(1) ?: return emptyList()
        val postId = postIdRegex.find(html)?.groupValues?.get(1) ?: return emptyList()

        val comments = mutableListOf<NovelComment>()
        var cursor = "0"

        // Fetch up to 5 pages of comments
        for (i in 0 until 5) {
            val url = "$baseUrl/comment/show?post_id=$postId&chapter_id=$chapterId&order_by=newest&cursor=$cursor"
            val response = get(url)
            val body = response.body?.string() ?: break

            val jsonObj = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) { break }

            val htmlContent = jsonObj["html"]?.jsonPrimitive?.contentOrNull ?: break
            val commentDoc = Jsoup.parse(htmlContent)
            val pageComments = commentDoc.select("li").mapNotNull { parseCommentFromHtml(it) }
            comments.addAll(pageComments)

            val hasMore = jsonObj["has_more_pages"]?.jsonPrimitive?.contentOrNull
            val nextCursor = jsonObj["next_cursor"]?.jsonPrimitive?.contentOrNull
            if (hasMore != "true" && hasMore != "1") break
            if (nextCursor.isNullOrBlank()) break
            cursor = nextCursor
        }

        return comments
    }

    private fun parseCommentFromHtml(li: Element): NovelComment? {
        val commentItem = li.selectFirst("div.comment-item") ?: return null
        val commentId = commentItem.attr("data-comid")

        val username = commentItem.selectFirst(".username")?.text() ?: "Unknown"
        val avatarUrl = commentItem.selectFirst("img.avatar")?.absUrl("src")
        val content = commentItem.selectFirst(".comment-text")?.html()?.trim() ?: ""
        val likesText = commentItem.selectFirst(".like-button span")?.text()
        val likes = likesText?.toIntOrNull() ?: 0
        val dateStr = commentItem.selectFirst(".post-date")?.text()
        val dateTimestamp = parseDate(dateStr) ?: 0L

        return NovelComment(
            id = commentId,
            userName = username,
            avatarUrl = avatarUrl,
            content = content,
            likes = likes,
            date = dateTimestamp
        )
    }
}
