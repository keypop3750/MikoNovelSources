package yokai.extension.novel.en.webnovel

import yokai.extension.novel.lib.*
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Source for WebNovel (webnovel.com)
 *
 * Based on reverse-engineering from lightnovel-crawler.
 * Webnovel uses a JSON API with CSRF token authentication.
 * Requires Cloudflare bypass (handled by app's OkHttp client).
 *
 * API endpoints:
 * - Search:       GET /go/pcm/search/result
 * - Book info:    GET /go/pcm/chapter/getContent?chapterId=0
 * - Chapters:     Parsed from /catalog HTML
 * - Content:      GET /go/pcm/chapter/getContent
 * - Comments:     GET /go/pcm/comment/GetComments (TBD)
 */
class WebNovel : ConfigurableNovelSource() {

    override val id: Long = 6050L
    override val name: String = "WebNovel"
    override val baseUrl: String = "https://www.webnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L

    /** CSRF token fetched lazily from cookies */
    private var csrfToken: String? = null

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "last_updated"),
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

    override val selectors = SourceSelectors(
        // Search fallback HTML selectors
        searchItemSelector = ".search-result-container li",
        searchTitleSelector = "a[data-bookname]",
        searchCoverSelector = "img",

        detailTitleSelector = "h1",
        detailCoverSelector = "img._thumbnail",

        chapterListSelector = "li[data-report-cid] a[href]",
        chapterContentSelector = "div.cha-content",
        contentRemoveSelectors = listOf("script", "ins.adsbygoogle", ".pirate"),
        contentRemovePatterns = listOf(
            "Find authorized novels in Webnovel",
            "<pirate>.*?</pirate>"
        ),

        searchUrlPattern = "$baseUrl/search",

        fetchFullCoverFromDetails = false
    )

    // ===== Search =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return fetchRankings("pop", page)
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return fetchRankings("new", page)
    }

    private suspend fun fetchRankings(rankType: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()

        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/rank/getRank".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("categoryType", "0")
            ?.addQueryParameter("rankType", rankType)
            ?.addQueryParameter("periodType", "4")
            ?.addQueryParameter("pageIndex", page.toString())
            ?.addQueryParameter("pageSize", "50")
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.build()
            ?: return emptyList()

        return try {
            val response = get(url.toString()).body?.string() ?: return emptyList()
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()
            val items = data.optJSONArray("items") ?: return emptyList()

            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val bookId = item.optString("bookId", "")
                val bookName = item.optString("bookName", "")
                if (bookId.isBlank() || bookName.isBlank()) return@mapNotNull null

                NovelSearchResult(
                    title = bookName,
                    url = "$baseUrl/book/$bookId",
                    coverUrl = item.optString("cover", "").takeIf { it.isNotBlank() },
                    author = item.optString("authorName", "").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Ranking error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()

        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/search/result".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("pageIndex", "1")
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.addQueryParameter("keywords", query)
            ?.build()
            ?: return emptyList()

        return try {
            val response = get(url.toString()).body?.string() ?: return emptyList()
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()
            val bookInfo = data.optJSONObject("bookInfo") ?: return emptyList()
            val items = bookInfo.optJSONArray("bookItems") ?: return emptyList()

            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val bookId = item.optString("bookId", "")
                val bookName = item.optString("bookName", "")
                if (bookId.isBlank() || bookName.isBlank()) return@mapNotNull null

                NovelSearchResult(
                    title = bookName,
                    url = "$baseUrl/book/$bookId",
                    coverUrl = item.optString("cover", "").takeIf { it.isNotBlank() },
                    author = item.optString("authorName", "").takeIf { it.isNotBlank() },
                    rating = item.optString("totalScore", "").takeIf { it.isNotBlank() }?.toFloatOrNull()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Search error: ${e.message}")
            emptyList()
        }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val bookId = extractBookId(url)
        if (bookId.isBlank()) throw Exception("Invalid novel URL")

        val data = fetchBookInfo(bookId)
        val bookInfo = data.optJSONObject("bookInfo")
            ?: throw Exception("Get book info failed")

        val title = bookInfo.optString("bookName", "")
            .ifBlank { throw Exception("Get book title failed") }

        val coverUrl = "//book-pic.webnovel.com/bookcover/$bookId"

        val author = when {
            bookInfo.has("authorName") -> bookInfo.optString("authorName", "")
            bookInfo.has("authorItems") -> {
                val arr = bookInfo.optJSONArray("authorItems")
                (0 until (arr?.length() ?: 0))
                    .mapNotNull { arr?.getJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            }
            else -> null
        }

        // Parse catalog page for description (API doesn't return it directly)
        val description = try {
            val catalogDoc = getDocument("$url/catalog")
            catalogDoc.selectFirst("._detailed ._desc p, .g_intro p")?.text()
        } catch (e: Exception) { null }

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = fixUrl(coverUrl),
            description = description,
            status = NovelStatus.UNKNOWN
        )
    }

    // ===== Chapter List =====

    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        val bookId = extractBookId(novelUrl)

        // Fetch all catalog pages
        for (page in 1..100) {
            val catalogUrl = "$novelUrl/catalog?page=$page"
            val document = getDocument(catalogUrl)
            var pageChapters = 0

            // Try primary selector
            for (div in document.select(".j_catalog_list .volume-item")) {
                for (a in div.select("ol > li > a[href]")) {
                    val li = a.parent() ?: continue
                    val cid = li.attr("data-report-cid")
                    if (cid.isBlank()) continue
                    // Skip locked chapters (marked with svg icon)
                    if (a.selectFirst("svg._icon") != null) continue

                    val chapterUrl = fixUrl(a.attr("href"))
                    val chapterTitle = a.text().ifBlank { a.attr("title") }

                    chapters.add(NovelChapter(
                        url = chapterUrl,
                        name = chapterTitle,
                        chapterNumber = (chapters.size + 1).toFloat()
                    ))
                    pageChapters++
                }
            }

            // Fallback selector
            if (pageChapters == 0) {
                for (li in document.select(".j_catalog_list li[data-report-cid]")) {
                    val a = li.selectFirst("a[href]") ?: continue
                    val cid = li.attr("data-report-cid")
                    if (cid.isBlank()) continue
                    if (a.selectFirst("svg._icon") != null) continue
                    val chapterUrl = fixUrl(a.attr("href"))
                    val chapterTitle = a.text().ifBlank { a.attr("title") }

                    chapters.add(NovelChapter(
                        url = chapterUrl,
                        name = chapterTitle,
                        chapterNumber = (chapters.size + 1).toFloat()
                    ))
                    pageChapters++
                }
            }

            // If no chapters on this page, we've reached the end
            if (pageChapters == 0) break
        }

        return chapters
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val bookId = extractBookId(chapterUrl)
        val chapterId = extractChapterId(chapterUrl)
        if (bookId.isBlank() || chapterId.isBlank()) return ""

        val data = fetchChapterContent(bookId, chapterId)
        val chapterInfo = data.optJSONObject("chapterInfo") ?: return ""

        val content = when {
            chapterInfo.has("content") -> chapterInfo.optString("content", "")
            chapterInfo.has("contents") -> {
                val arr = chapterInfo.optJSONArray("contents")
                (0 until (arr?.length() ?: 0))
                    .mapNotNull { arr?.getJSONObject(it)?.optString("content") }
                    .filter { it.isNotBlank() }
                    .joinToString("")
            }
            else -> ""
        }

        return formatContent(content)
    }

    // ===== Comments =====

    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        val bookId = extractBookId(chapterUrl)
        val chapterId = extractChapterId(chapterUrl)
        if (bookId.isBlank() || chapterId.isBlank()) return emptyList()

        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/comment/GetComments".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("bookId", bookId)
            ?.addQueryParameter("chapterId", chapterId)
            ?.addQueryParameter("pageIndex", "1")
            ?.addQueryParameter("pageSize", "50")
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.build()
            ?: return emptyList()

        return try {
            val response = get(url.toString()).body?.string() ?: return emptyList()
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()
            val items = data.optJSONArray("commentItems") ?: data.optJSONArray("items") ?: return emptyList()

            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val commentId = item.optString("commentId", item.optString("id", ""))
                val userName = item.optString("userName", item.optString("nickName", "Anonymous"))
                val content = item.optString("content", item.optString("body", ""))
                if (commentId.isBlank() || content.isBlank()) return@mapNotNull null

                NovelComment(
                    id = commentId,
                    userName = userName,
                    avatarUrl = item.optString("userHead", item.optString("head", "")).takeIf { it.isNotBlank() },
                    content = content,
                    likes = item.optInt("likeCount", item.optInt("likeNum", 0)),
                    replyCount = item.optInt("replyCount", item.optInt("replyNum", 0)),
                    date = item.optLong("commentTime", item.optLong("time", 0L))
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Comments error: ${e.message}")
            emptyList()
        }
    }

    // ===== Internal API helpers =====

    private suspend fun getCsrfToken(): String {
        csrfToken?.let { return it }

        // Visit a page to get the _csrfToken cookie
        val response = get("$baseUrl/stories/novel")
        response.body?.close()

        // Extract from cookies if available via the response
        // The cookie jar should have stored it; we can't directly access cookies here
        // so we make another request that will include the cookie automatically
        val checkResponse = get("$baseUrl/")
        val body = checkResponse.body?.string() ?: ""
        checkResponse.body?.close()

        // Try to extract CSRF from page or use empty (API may still work)
        val extracted = Regex("_csrfToken['\"]?\\s*[:=]\\s*['\"]([^'\"]+)").find(body)?.groupValues?.get(1)
            ?: Regex("csrfToken['\"]?\\s*[:=]\\s*['\"]([^'\"]+)").find(body)?.groupValues?.get(1)
            ?: ""

        csrfToken = extracted
        return extracted
    }

    private suspend fun fetchBookInfo(bookId: String): JSONObject {
        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/chapter/getContent".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("bookId", bookId)
            ?.addQueryParameter("chapterId", "0")
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.build()
            ?: throw Exception("Failed to build API URL")

        val response = get(url.toString()).body?.string()
            ?: throw Exception("Empty response from book info API")

        val json = JSONObject(response)
        return json.optJSONObject("data") ?: throw Exception("No data in book info response")
    }

    private suspend fun fetchChapterContent(bookId: String, chapterId: String): JSONObject {
        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/chapter/getContent".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("bookId", bookId)
            ?.addQueryParameter("chapterId", chapterId)
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.build()
            ?: throw Exception("Failed to build API URL")

        val response = get(url.toString()).body?.string()
            ?: throw Exception("Empty response from chapter content API")

        val json = JSONObject(response)
        return json.optJSONObject("data") ?: throw Exception("No data in chapter content response")
    }

    // ===== Helpers =====

    private fun extractBookId(url: String): String {
        // Match /book/12345 or /book/title_12345
        return Regex("book/([0-9_]+)").find(url)?.groupValues?.get(1)
            ?: Regex("book/[^/]+_([0-9]+)").find(url)?.groupValues?.get(1)
            ?: ""
    }

    private fun extractChapterId(url: String): String {
        // Match /chapter/12345 or /chapter/title_12345
        return Regex("chapter/([0-9_]+)").find(url)?.groupValues?.get(1)
            ?: Regex("chapter/[^/]+_([0-9]+)").find(url)?.groupValues?.get(1)
            ?: ""
    }

    private fun formatContent(text: String): String {
        if (text.contains("<p>") && text.contains("</p>")) {
            return text
                .replace(Regex("<pirate>.*?</pirate>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("Find authorized novels in Webnovel.*?for visiting\\."), "")
                .trim()
        }

        // Plain text: wrap paragraphs
        val escaped = text
            .replace("\r", "")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val paragraphs = escaped.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("</p><p>") { it }

        return "<p>$paragraphs</p>"
            .replace(Regex("<pirate>.*?</pirate>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Find authorized novels in Webnovel.*?for visiting\\."), "")
            .trim()
    }
}

