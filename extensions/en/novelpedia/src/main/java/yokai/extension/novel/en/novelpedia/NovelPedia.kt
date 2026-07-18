package yokai.extension.novel.en.novelpedia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

class NovelPedia : NovelSource() {

    override val id: Long = 6026L
    override val name: String = "NovelPedia"
    override val baseUrl: String = "https://novelpedia.co"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L

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
        val url = "$baseUrl/explore?search=${query.encodeUrl()}&page=$page"
        val doc = getDocument(url)
        return parseNovelListFromDoc(doc)
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/explore?page=$page"
        val doc = getDocument(url)
        return parseNovelListFromDoc(doc)
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/explore?sort=latest&page=$page"
        val doc = getDocument(url)
        return parseNovelListFromDoc(doc)
    }

    /**
     * NovelPedia lists novels as cards. Each card has multiple <a> tags with the same href:
     *  1. The cover <a> (class includes "aspect-...") — text is "GenreRating" e.g. "Comedy4.9"
     *  2. The title <a> (class "block") — text is the actual novel title
     *  3. A chapters <a> (class "block") — text is "N Chapters"
     * We want #2. The cover image is inside #1.
     */
    private fun parseNovelListFromDoc(doc: Document): List<NovelSearchResult> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<NovelSearchResult>()
        // Select all <a> with /novels/ in href, excluding /chapters/
        val allLinks = doc.select("a[href*=/novels/]").filterNot { it.attr("href").contains("/chapters/") }
        // Group by href and pick the title link (the one whose text is NOT "N Chapters" and NOT a genre+rating blob)
        val byHref = allLinks.groupBy { it.absUrl("href") }
        for ((href, anchors) in byHref) {
            if (href.isBlank() || href in seen) continue
            // The title link: class is exactly "block", text doesn't contain "Chapters" and isn't a short genre+rating string
            val titleLink = anchors.firstOrNull { a ->
                val text = a.text().trim()
                val cls = a.classNames()
                // Title link has class "block" only (no "aspect-..." etc), text is longer than a genre+rating blob
                cls.size == 1 && cls.contains("block") &&
                    !text.contains("Chapters") && text.length > 3 && !text.matches(Regex("""^[A-Za-z]+[\d.]+$"""))
            } ?: anchors.firstOrNull { a ->
                val text = a.text().trim()
                !text.contains("Chapters") && text.length > 3 && !text.matches(Regex("""^[A-Za-z]+[\d.]+$"""))
            }
            val title = titleLink?.text()?.trim() ?: continue
            // Cover image is inside the first <a> (the cover link)
            val coverUrl = anchors.firstNotNullOfOrNull { it.selectFirst("img") }?.absUrl("src")
            results.add(NovelSearchResult(title = title, url = href, coverUrl = coverUrl))
            seen.add(href)
        }
        return results
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""

        val coverUrl = doc.selectFirst("img[alt]")?.absUrl("src")

        val description = doc.selectFirst("p")?.text()?.trim()

        // Try JSON-LD structured data
        val jsonLd = doc.selectFirst("script[type=application/ld+json]")?.data()
        var author: String? = null
        var genres = emptyList<String>()
        var status = NovelStatus.UNKNOWN

        if (jsonLd != null) {
            try {
                val ldObj = json.parseToJsonElement(jsonLd).jsonObject
                author = ldObj["author"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                val genreVal = ldObj["genre"]
                if (genreVal is JsonArray) {
                    genres = genreVal.mapNotNull { it.jsonPrimitive.contentOrNull }
                } else if (genreVal != null) {
                    genres = listOf(genreVal.jsonPrimitive.content)
                }
            } catch (_: Exception) {}
        }

        // Fallback to HTML selectors
        if (author == null) {
            author = doc.selectFirst("[itemprop=author]")?.text()?.trim()
        }
        if (genres.isEmpty()) {
            genres = doc.select("a[href*=genre], a[href*=tag]").map { it.text().trim() }
                .filter { it.isNotBlank() }
        }

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres.distinct(),
            status = status
        )
    }

    // ===== Chapter List =====

    /**
     * NovelPedia has chapter list at /novels/<slug>/chapters (paginated).
     * Chapter URLs contain UUIDs: /novels/<slug>/chapters/<uuid>
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        while (true) {
            val url = if (page == 1) "$novelUrl/chapters" else "$novelUrl/chapters?page=$page"
            val doc = getDocument(url)
            val pageChapters = doc.select("a[href*=/chapters/]").mapNotNull { a ->
                val href = a.absUrl("href")
                val name = a.text().trim()
                if (href.contains("/chapters/") && name.isNotBlank()) {
                    NovelChapter(name = name, url = href)
                } else null
            }.distinctBy { it.url }
            if (pageChapters.isEmpty()) break
            chapters.addAll(pageChapters)
            // Check for next page
            val nextLink = doc.selectFirst("a[rel=next]")
            if (nextLink == null) break
            page++
        }
        return chapters.reversed()
    }

    // ===== Chapter Content =====

    /**
     * NovelPedia is a Next.js 14 app with React Server Components (RSC).
     * Chapter content is embedded in self.__next_f.push() chunks in the HTML.
     * We need to parse these RSC chunks to extract the chapter text.
     */
    override suspend fun getChapterContent(chapterUrl: String): String {
        val response = get(chapterUrl)
        val html = response.body?.string() ?: return ""

        // Try standard HTML parsing first (in case of client-side hydration)
        val doc = Jsoup.parse(html, chapterUrl)
        val contentEl = doc.selectFirst("div.chapter-content")
            ?: doc.selectFirst("article")
            ?: doc.selectFirst("div.prose")
        if (contentEl != null && contentEl.text().length > 100) {
            contentEl.select("script, style, .ads").remove()
            return contentEl.html()
        }

        // Parse RSC chunks: self.__next_f.push([1,"..."])
        val chunkRegex = Regex("""self\.__next_f\.push\(\[1,"((?:[^"\\]|\\.)*)"\]\)""")
        val chunks = chunkRegex.findAll(html).map { it.groupValues[1] }.toList()

        // Decode and search for chapter content in the RSC payload
        val fullText = chunks.joinToString("") { decodeRscChunk(it) }

        // The chapter content is typically in a <p> tag sequence within the RSC data
        // Look for paragraphs between the chapter heading and the comments section
        val contentRegex = Regex("""(?:(?:<p>|\\u003cp>)([^<\\]+)(?:</p>|\\u003c/p>))""")
        val paragraphs = contentRegex.findAll(fullText).map { it.groupValues[1] }.toList()
            .filter { it.length > 20 } // Filter out short fragments

        if (paragraphs.isNotEmpty()) {
            return paragraphs.joinToString("") { "<p>${it}</p>" }
        }

        // Fallback: extract text between known markers
        val startMarker = fullText.indexOf("chapterContent")
        if (startMarker > 0) {
            val segment = fullText.substring(startMarker, minOf(startMarker + 50000, fullText.length))
            val pRegex = Regex("""<p>([^<]+)</p>""")
            val ps = pRegex.findAll(segment).map { it.groupValues[1] }.toList()
            if (ps.isNotEmpty()) {
                return ps.joinToString("") { "<p>${it}</p>" }
            }
        }

        return ""
    }

    private fun decodeRscChunk(chunk: String): String {
        return try {
            // RSC chunks use unicode escape sequences
            chunk.replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\u0026", "&")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
        } catch (_: Exception) {
            chunk
        }
    }

    // ===== Chapter Comments =====

    /**
     * NovelPedia provides a JSON API for comments:
     * GET /api/v1/comments?fk_id={chapter_uuid}&fk_type=CHAPTER
     * Returns: [{ id, username, avatar_url, content, upvotes, created_at, replies: [...] }]
     *
     * The chapter UUID is extracted from the chapter URL.
     */
    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        val chapterUuid = chapterUrl.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$baseUrl/api/v1/comments?fk_id=$chapterUuid&fk_type=CHAPTER"

        val response = get(apiUrl)
        val body = response.body?.string() ?: return emptyList()

        return try {
            val commentsArray = json.parseToJsonElement(body).jsonArray
            commentsArray.map { parseComment(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseComment(obj: JsonObject): NovelComment {
        val id = obj["id"]?.jsonPrimitive?.content ?: ""
        val username = obj["username"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val avatarUrl = obj["avatar_url"]?.jsonPrimitive?.contentOrNull
        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val upvotes = obj["upvotes"]?.jsonPrimitive?.intOrNull ?: 0
        val dateStr = obj["created_at"]?.jsonPrimitive?.contentOrNull
        val repliesArray = obj["replies"] as? JsonArray
        val replies = repliesArray?.map { parseComment(it.jsonObject) } ?: emptyList()

        return NovelComment(
            id = id,
            userName = username,
            avatarUrl = avatarUrl,
            content = content,
            likes = upvotes,
            replyCount = replies.size,
            date = parseIsoDate(dateStr),
            replies = replies
        )
    }

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            // 2026-04-24T17:44:24.019830Z
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
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
