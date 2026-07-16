package yokai.extension.novel.en.webnovel

import yokai.extension.novel.lib.*
import org.json.JSONArray
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
        // Ranking API is dead (404) — use HTML scraping directly with pagination
        return fetchRankingsFromHtml(rankType, page)
    }

    private suspend fun fetchRankingsFromHtml(rankType: String, page: Int): List<NovelSearchResult> {
        return try {
            val path = when (rankType) {
                "pop" -> "$baseUrl/ranking/hot"
                "new" -> "$baseUrl/ranking/latest"
                else -> "$baseUrl/ranking/hot"
            }
            // WebNovel rankings use pageIndex for pagination (1-based)
            val pageIndex = (page - 1).coerceAtLeast(0)
            val url = if (pageIndex > 0) "$path?pageIndex=$pageIndex" else path

            val doc = getDocument(url)
            val results = mutableListOf<NovelSearchResult>()
            val seenIds = mutableSetOf<String>()

            // Strategy 1: Extract from anchor tags with title attribute (modern WebNovel)
            val rankingContainers = doc.select(".rank-list, .ranking-list, [class*='ranking'] ul, [class*='rank'] ul, ol[class*='rank'], ul[class*='list']")
            val linksToProcess = if (rankingContainers.isNotEmpty()) {
                rankingContainers.select("a[href*=/book/]")
            } else {
                doc.select("a[href*=/book/]")
            }

            for (a in linksToProcess) {
                val href = a.attr("href")
                if (!href.contains("/book/")) continue
                val bookId = extractBookId(href)
                if (bookId.isBlank() || seenIds.contains(bookId)) continue
                seenIds.add(bookId)

                var title = a.text().trim()
                if (title.isBlank()) title = a.attr("title").trim()
                if (title.isBlank()) {
                    title = a.closest("h3, h4, .book-title, [class*='title'], [class*='bookName']")?.text()?.trim() ?: ""
                }
                if (title.isBlank()) continue

                // Find a cover image nearby
                var cover: String? = null
                val container = a.closest("li, .rank-item, .book-item, [class*='item'], [class*='card']")
                if (container != null) {
                    cover = container.selectFirst("img[src*=bookcover], img[src*=webnovel], img[src*=yueimg]")?.attr("src")?.takeIf { it.isNotBlank() }
                        ?: container.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
                }
                if (cover == null) {
                    cover = doc.select("img[src*=$bookId]").firstOrNull()?.attr("src")?.takeIf { it.isNotBlank() }
                }

                results.add(NovelSearchResult(
                    title = title,
                    url = fixUrl(href),
                    coverUrl = cover,
                    author = null
                ))
            }

            // Strategy 2: Extract from JSON-LD structured data
            if (results.isEmpty()) {
                for (script in doc.select("script[type=application/ld+json]")) {
                    val jsonStr = script.data()
                    if (!jsonStr.contains("ListItem")) continue
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val itemList = json.optJSONArray("itemListElement")
                            ?: continue
                        for (i in 0 until itemList.length()) {
                            val item = itemList.optJSONObject(i) ?: continue
                            val bookUrl = item.optString("url", "")
                            if (!bookUrl.contains("/book/")) continue
                            val bookId = extractBookId(bookUrl)
                            if (bookId.isBlank() || seenIds.contains(bookId)) continue
                            seenIds.add(bookId)
                            // Try to get title from the page or URL slug
                            val slug = bookUrl.substringAfterLast("/").substringBeforeLast("_")
                            val title = slug.replace("-", " ").replace("%5B", "[").replace("%5D", "]").capitalizeWords()
                            results.add(NovelSearchResult(
                                title = title,
                                url = fixUrl(bookUrl),
                                coverUrl = null,
                                author = null
                            ))
                        }
                    } catch (e: Exception) {
                        // Ignore JSON parse errors
                    }
                }
            }

            android.util.Log.d("WebNovel", "HTML browse found ${results.size} items on page $page")

            // Strategy 3: If ranking page yields nothing, fall back to homepage for popular
            if (results.isEmpty() && rankType == "pop" && page == 1) {
                android.util.Log.d("WebNovel", "Rankings empty, falling back to homepage")
                return fetchRankingsFromHomepage()
            }

            results
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "HTML browse error on page $page: ${e.message}")
            // Fallback to homepage for popular on first page
            if (rankType == "pop" && page == 1) {
                return fetchRankingsFromHomepage()
            }
            emptyList()
        }
    }

    private suspend fun fetchRankingsFromHomepage(): List<NovelSearchResult> {
        return try {
            val doc = getDocument("$baseUrl/")
            val results = mutableListOf<NovelSearchResult>()
            val seenIds = mutableSetOf<String>()

            for (a in doc.select("a[href*=/book/]")) {
                val href = a.attr("href")
                if (!href.contains("/book/")) continue
                val bookId = extractBookId(href)
                if (bookId.isBlank() || seenIds.contains(bookId)) continue
                seenIds.add(bookId)

                var title = a.text().trim()
                if (title.isBlank()) title = a.attr("title").trim()
                if (title.isBlank()) {
                    title = a.closest("h3, h4, .book-title, [class*='title'], [class*='bookName']")?.text()?.trim() ?: ""
                }
                if (title.isBlank()) continue

                var cover: String? = a.closest("li, .rank-item, .book-item, [class*='item'], [class*='card']")
                    ?.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }

                results.add(NovelSearchResult(
                    title = title,
                    url = fixUrl(href),
                    coverUrl = cover,
                    author = null
                ))
            }

            android.util.Log.d("WebNovel", "Homepage fallback found ${results.size} items")
            results
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Homepage fallback error: ${e.message}")
            emptyList()
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val token = getCsrfToken()
        val url = "$baseUrl/go/pcm/search/result".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("_csrfToken", token)
            ?.addQueryParameter("pageIndex", page.toString())
            ?.addQueryParameter("encryptType", "3")
            ?.addQueryParameter("_fsae", "0")
            ?.addQueryParameter("keywords", query)
            ?.build()
            ?: return emptyList()

        return try {
            val responseBody = get(url.toString()).body?.string() ?: return emptyList()
            android.util.Log.d("WebNovel", "Search raw response: ${responseBody.take(1000)}")
            val json = JSONObject(responseBody)
            val data = json.optJSONObject("data") ?: return emptyList()

            // Try multiple possible paths for search results
            val items = data.optJSONObject("bookInfo")?.optJSONArray("bookItems")
                ?: data.optJSONArray("bookItems")
                ?: data.optJSONArray("items")
                ?: data.optJSONObject("searchResult")?.optJSONArray("bookItems")
                ?: data.optJSONObject("searchResult")?.optJSONArray("items")
                ?: return emptyList()

            (0 until items.length()).mapNotNull { i ->
                val item = items.optJSONObject(i) ?: return@mapNotNull null
                val bookId = item.optString("bookId", "")
                    .ifBlank { item.optString("id", "") }
                val bookName = item.optString("bookName", "")
                    .ifBlank { item.optString("name", "") }
                    .ifBlank { item.optString("title", "") }
                if (bookId.isBlank() || bookName.isBlank()) return@mapNotNull null

                NovelSearchResult(
                    title = bookName,
                    url = "$baseUrl/book/$bookId",
                    coverUrl = item.optString("cover", "").takeIf { it.isNotBlank() }
                        ?: item.optString("coverUrl", "").takeIf { it.isNotBlank() },
                    author = item.optString("authorName", "").takeIf { it.isNotBlank() }
                        ?: item.optString("author", "").takeIf { it.isNotBlank() },
                    rating = item.optString("totalScore", "").takeIf { it.isNotBlank() }?.toFloatOrNull()
                        ?: item.optString("score", "").takeIf { it.isNotBlank() }?.toFloatOrNull()
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

        android.util.Log.d("WebNovel", "bookInfo keys: ${bookInfo.keys().asSequence().toList()}")

        val title = bookInfo.optString("bookName", "")
            .ifBlank { throw Exception("Get book title failed") }

        // Cover: prefer API field, fallback to hardcoded pattern
        val coverUrl = bookInfo.optString("cover", "")
            .takeIf { it.isNotBlank() }
            ?: "//book-pic.webnovel.com/bookcover/$bookId"

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

        // Description: try multiple API fields, then fall back to HTML catalog
        val description = bookInfo.optString("description", "")
            .takeIf { it.isNotBlank() }
            ?: bookInfo.optString("synopsis", "")
                .takeIf { it.isNotBlank() }
            ?: bookInfo.optString("intro", "")
                .takeIf { it.isNotBlank() }
            ?: bookInfo.optString("bookDesc", "")
                .takeIf { it.isNotBlank() }
            ?: try {
                val catalogDoc = getDocument("$url/catalog")
                catalogDoc.selectFirst("._detailed ._desc p, .g_intro p, .detailed .desc p, [class*='desc'] p")?.text()
            } catch (e: Exception) {
                android.util.Log.d("WebNovel", "Catalog description fetch failed: ${e.message}")
                null
            }

        // Status: try multiple API field names
        val statusText = bookInfo.optString("bookStatus", "")
            .takeIf { it.isNotBlank() }
            ?: bookInfo.optString("status", "")
                .takeIf { it.isNotBlank() }
            ?: bookInfo.optString("bookStatusName", "")
                .takeIf { it.isNotBlank() }
        val status = parseWebnovelStatus(statusText)
        android.util.Log.d("WebNovel", "statusText='$statusText' -> parsed=$status")

        // Genres / tags: try multiple API fields
        val genres = mutableListOf<String>()
        bookInfo.optString("categoryName", "")
            .takeIf { it.isNotBlank() }?.let { genres.add(it) }
        if (genres.isEmpty()) {
            bookInfo.optJSONArray("tagInfo")?.let { arr ->
                (0 until arr.length())
                    .mapNotNull { arr.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }
                    .let { genres.addAll(it) }
            }
        }
        if (genres.isEmpty()) {
            bookInfo.optJSONArray("tags")?.let { arr ->
                (0 until arr.length())
                    .mapNotNull { arr.optString(it) }
                    .filter { it.isNotBlank() }
                    .let { genres.addAll(it) }
            }
        }

        // Tags: same source as genres for WebNovel (they don't separate them)
        val tags = genres.toList()

        // Rating
        val rating = bookInfo.optString("totalScore", "")
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
            ?: bookInfo.optString("score", "")
                .takeIf { it.isNotBlank() }
                ?.toFloatOrNull()

        android.util.Log.d("WebNovel", "Parsed details: title='$title', author='$author', desc=${description != null}, genres=$genres, status=$status")

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = fixUrl(coverUrl),
            description = description,
            genres = genres,
            tags = tags,
            status = status,
            rating = rating,
            ratingCount = null,
            views = null,
            alternativeTitles = emptyList()
        )
    }

    // ===== Chapter List =====

    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val bookId = extractBookId(novelUrl)
        if (bookId.isBlank()) {
            android.util.Log.e("WebNovel", "Could not extract bookId from $novelUrl")
            return emptyList()
        }

        // Endpoint 1: bookInfo (chapterId=0) — often contains free chapters only
        val bookInfoChapters = try {
            fetchChapterListFromBookInfo(bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "BookInfo chapter list failed: ${e.message}")
            emptyList()
        }
        if (bookInfoChapters.isNotEmpty()) {
            android.util.Log.d("WebNovel", "Using bookInfo chapter list with ${bookInfoChapters.size} chapters")
            // Continue to try catalog API in case it has MORE chapters
        }

        // Endpoint 2: Dedicated catalog API — may contain ALL chapters
        val catalogChapters = try {
            fetchChapterListFromCatalogApi(bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Catalog API chapter list failed: ${e.message}")
            emptyList()
        }
        if (catalogChapters.isNotEmpty()) {
            android.util.Log.d("WebNovel", "Using catalog API chapter list with ${catalogChapters.size} chapters")
            return catalogChapters
        }

        // Endpoint 3: Alternative chapter list API
        val listChapters = try {
            fetchChapterListFromCatalogApiV2(bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "ChapterList API failed: ${e.message}")
            emptyList()
        }
        if (listChapters.isNotEmpty()) {
            android.util.Log.d("WebNovel", "Using chapterList API with ${listChapters.size} chapters")
            return listChapters
        }

        // If bookInfo had chapters but catalog APIs were empty, return bookInfo chapters
        if (bookInfoChapters.isNotEmpty()) {
            return bookInfoChapters
        }

        // Fall back to HTML catalog scraping
        android.util.Log.d("WebNovel", "All API chapter lists empty, falling back to HTML catalog")
        return fetchChapterListFromHtml(novelUrl)
    }

    private suspend fun fetchChapterListFromBookInfo(bookId: String): List<NovelChapter> {
        return try {
            val data = fetchBookInfo(bookId)
            android.util.Log.d("WebNovel", "Book info keys: ${data.keys().asSequence().toList()}")

            if (data.has("bookInfo")) {
                val bi = data.optJSONObject("bookInfo")
                android.util.Log.d("WebNovel", "bookInfo keys: ${bi?.keys()?.asSequence()?.toList()}")
                val biChapterKeys = bi?.keys()?.asSequence()?.filter { it.contains("chapter", true) || it.contains("volume", true) || it.contains("catalog", true) }?.toList()
                android.util.Log.d("WebNovel", "Chapter-related keys in bookInfo: $biChapterKeys")
            }
            val dataChapterKeys = data.keys().asSequence().filter { it.contains("chapter", true) || it.contains("volume", true) || it.contains("catalog", true) }.toList()
            android.util.Log.d("WebNovel", "Chapter-related keys in data: $dataChapterKeys")

            // Try multiple possible field names for chapters in book info
            val chapters = data.optJSONArray("chapterItems")
                ?: data.optJSONArray("chapterList")
                ?: data.optJSONArray("chapters")
                ?: data.optJSONArray("volumeItems")
                ?: data.optJSONArray("groupItems")
                ?: data.optJSONObject("catalog")?.optJSONArray("items")
                ?: data.optJSONObject("catalog")?.optJSONArray("chapterItems")
                ?: data.optJSONObject("bookInfo")?.optJSONArray("chapterItems")
                ?: data.optJSONObject("bookInfo")?.optJSONArray("chapterList")
                ?: data.optJSONObject("bookInfo")?.optJSONArray("volumeItems")
                ?: data.optJSONObject("bookInfo")?.optJSONArray("groupItems")
                ?: return emptyList()

            android.util.Log.d("WebNovel", "Found chapters array with ${chapters.length()} items")

            (0 until chapters.length()).mapNotNull { i ->
                val item = chapters.optJSONObject(i) ?: return@mapNotNull null
                val chapterId = item.optString("chapterId", "")
                    .ifBlank { item.optString("id", "") }
                val rawName = item.optString("chapterName", "")
                    .ifBlank { item.optString("name", "") }
                    .ifBlank { item.optString("chapterIndex", "") }
                val chapterName = cleanChapterName(rawName)
                if (chapterId.isBlank() || chapterName.isBlank()) return@mapNotNull null

                val dateUpload = readApiDateUpload(item)

                NovelChapter(
                    url = "$baseUrl/book/$bookId/chapter/$chapterId",
                    name = chapterName,
                    dateUpload = dateUpload,
                    chapterNumber = item.optString("chapterIndex", "0").toFloatOrNull() ?: (i + 1).toFloat()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Book info chapter extraction error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchChapterListFromCatalogApi(bookId: String): List<NovelChapter> {
        return try {
            val token = getCsrfToken()
            val url = "$baseUrl/go/pcm/chapter/getCatalog".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("_csrfToken", token)
                ?.addQueryParameter("bookId", bookId)
                ?.addQueryParameter("encryptType", "3")
                ?.addQueryParameter("_fsae", "0")
                ?.build()
                ?: throw Exception("Failed to build catalog API URL")

            val response = get(url.toString()).body?.string()
                ?: throw Exception("Empty response from catalog API")

            android.util.Log.d("WebNovel", "Catalog API raw (first 2000 chars): ${response.take(2000)}")
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()

            // Log all keys to help diagnose
            val keys = data.keys().asSequence().filter { it.contains("chapter", true) || it.contains("volume", true) || it.contains("catalog", true) }.toList()
            android.util.Log.d("WebNovel", "Catalog API chapter-related keys: $keys")

            // Try multiple possible structures
            val chapters = data.optJSONArray("chapterItems")
                ?: data.optJSONArray("chapterList")
                ?: data.optJSONArray("chapters")
                ?: data.optJSONArray("volumeItems")
                ?: data.optJSONArray("groupItems")
                ?: data.optJSONObject("catalog")?.optJSONArray("items")
                ?: data.optJSONObject("catalog")?.optJSONArray("chapterItems")
                ?: return emptyList()

            android.util.Log.d("WebNovel", "Catalog API found ${chapters.length()} chapters")
            parseChapterJsonArray(chapters, bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Catalog API error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchChapterListFromCatalogApiV2(bookId: String): List<NovelChapter> {
        return try {
            val token = getCsrfToken()
            val url = "$baseUrl/go/pcm/chapter/getChapterList".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("_csrfToken", token)
                ?.addQueryParameter("bookId", bookId)
                ?.addQueryParameter("encryptType", "3")
                ?.addQueryParameter("_fsae", "0")
                ?.build()
                ?: throw Exception("Failed to build chapter list API URL")

            val response = get(url.toString()).body?.string()
                ?: throw Exception("Empty response from chapter list API")

            android.util.Log.d("WebNovel", "ChapterList API raw (first 2000 chars): ${response.take(2000)}")
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()

            val keys = data.keys().asSequence().filter { it.contains("chapter", true) || it.contains("volume", true) || it.contains("catalog", true) }.toList()
            android.util.Log.d("WebNovel", "ChapterList API chapter-related keys: $keys")

            val chapters = data.optJSONArray("chapterItems")
                ?: data.optJSONArray("chapterList")
                ?: data.optJSONArray("chapters")
                ?: data.optJSONArray("volumeItems")
                ?: data.optJSONArray("groupItems")
                ?: data.optJSONObject("catalog")?.optJSONArray("items")
                ?: data.optJSONObject("catalog")?.optJSONArray("chapterItems")
                ?: return emptyList()

            android.util.Log.d("WebNovel", "ChapterList API found ${chapters.length()} chapters")
            parseChapterJsonArray(chapters, bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "ChapterList API error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchChapterListFromHtml(novelUrl: String): List<NovelChapter> {
        val bookId = extractBookId(novelUrl)
        val catalogUrl = "$novelUrl/catalog"
        android.util.Log.d("WebNovel", "Fetching catalog HTML: $catalogUrl")
        val document = try {
            getDocument(catalogUrl)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Catalog fetch failed: ${e.message}")
            return emptyList()
        }
        val html = document.html()
        android.util.Log.d("WebNovel", "Catalog HTML length: ${html.length}")

        // Strategy 1: Extract ALL chapter links by URL pattern.
        // WebNovel catalog page contains every chapter as an <a> with href:
        //   /book/{slug}_{bookId}/{chapterSlug}_{chapterId}
        val chapters = mutableListOf<NovelChapter>()
        val seenIds = mutableSetOf<String>()
        val chapterPattern = Regex("/book/[^/]+_$bookId/[^/]+_([0-9]+)")

        for (a in document.select("a[href*=/book/][href*=_$bookId]")) {
            val href = a.attr("href")
            val match = chapterPattern.find(href)
            if (match == null) continue

            val chapterId = match.groupValues[1]
            if (seenIds.contains(chapterId)) continue
            seenIds.add(chapterId)

            // Skip locked chapters (VIP/paywalled)
            val parent = a.parent()
            if (parent != null) {
                if (parent.selectFirst("svg, i[class*='lock'], span[class*='lock'], .icon-lock, [class*='locked']") != null) continue
            }
            if (a.selectFirst("svg, i[class*='lock'], span[class*='lock'], .icon-lock") != null) continue

            // Clean chapter name
            val rawName = a.text().trim().ifBlank { a.attr("title").trim() }
            val name = cleanChapterName(rawName)
            if (name.isBlank()) continue

            chapters.add(NovelChapter(
                url = fixUrl(href),
                name = name,
                chapterNumber = chapters.size + 1f
            ))
        }

        android.util.Log.d("WebNovel", "HTML link extraction found ${chapters.size} chapters")
        if (chapters.isNotEmpty()) return chapters

        // Strategy 2: Legacy DOM selector fallback
        val fallback = mutableListOf<NovelChapter>()
        for (div in document.select("[class*='catalog'] [class*='volume'], [class*='catalog'] [class*='group']")) {
            for (a in div.select("ol > li > a[href], ul > li > a[href], li > a[href]")) {
                val href = a.attr("href")
                val match = chapterPattern.find(href)
                if (match == null) continue
                // Skip locked/premium chapters more robustly
                if (a.selectFirst("svg, i[class*='lock'], span[class*='lock'], .icon-lock, [class*='locked']") != null) continue
                val name = cleanChapterName(a.text().trim())
                if (name.isBlank()) continue
                fallback.add(NovelChapter(
                    url = fixUrl(href),
                    name = name,
                    chapterNumber = fallback.size + 1f
                ))
            }
        }
        android.util.Log.d("WebNovel", "Legacy DOM fallback found ${fallback.size} chapters")
        if (fallback.isNotEmpty()) return fallback

        // Strategy 3: Extract chapter data from embedded <script> JSON
        // WebNovel frequently stores catalog data in window.__INITIAL_STATE__ or similar
        val scriptChapters = try {
            extractChaptersFromScripts(document, bookId)
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "Script JSON extraction failed: ${e.message}")
            emptyList()
        }
        android.util.Log.d("WebNovel", "Script JSON extraction found ${scriptChapters.size} chapters")
        return scriptChapters
    }

    /**
     * Attempts to extract chapter arrays from <script> tags containing JSON state data.
     */
    private fun extractChaptersFromScripts(document: org.jsoup.nodes.Document, bookId: String): List<NovelChapter> {
        val scripts = document.select("script")
        for (script in scripts) {
            val text = script.data()
            if (text.isBlank()) continue

            // Look for window.__INITIAL_STATE__ or similar state variables
            val stateVars = listOf("window.__INITIAL_STATE__", "window.__DATA__", "window.__INITIAL__", "window.__STORE__")
            for (varName in stateVars) {
                val json = extractJsonByBraces(text, varName)
                    ?: continue
                try {
                    val obj = JSONObject(json)
                    // Deep search for chapter arrays within the state object
                    val found = deepFindChapters(obj, bookId)
                    if (found.isNotEmpty()) return found
                } catch (_: Exception) {
                    continue
                }
            }

            // Also look for raw chapter JSON arrays in scripts
            val chapterArray = findJsonArrayContaining(text, "\"chapterId\"")
                ?: findJsonArrayContaining(text, "\"chapterName\"")
                ?: continue
            try {
                val arr = JSONArray(chapterArray)
                val parsed = parseChapterJsonArray(arr, bookId)
                if (parsed.isNotEmpty()) return parsed
            } catch (_: Exception) {
                continue
            }
        }
        return emptyList()
    }

    /**
     * Extracts a JSON object/array from script text by counting braces/brackets.
     * This handles deeply nested JSON that regex cannot capture.
     */
    private fun extractJsonByBraces(scriptText: String, varName: String): String? {
        // Allow optional whitespace around equals sign
        val assignmentIdx = scriptText.indexOf("$varName = ").let {
            if (it != -1) it else scriptText.indexOf("$varName=")
        }
        if (assignmentIdx == -1) return null

        val startIdx = scriptText.indexOfAny(charArrayOf('{', '['), assignmentIdx)
        if (startIdx == -1) return null

        val openChar = scriptText[startIdx]
        val closeChar = if (openChar == '{') '}' else ']'
        var depth = 1
        var inString = false
        var escapeNext = false
        var pos = startIdx + 1

        while (pos < scriptText.length && depth > 0) {
            val c = scriptText[pos]
            when {
                escapeNext -> escapeNext = false
                c == '\\' -> escapeNext = true
                c == '"' && !inString -> inString = true
                c == '"' && inString -> inString = false
                !inString && c == openChar -> depth++
                !inString && c == closeChar -> depth--
            }
            pos++
        }

        return if (depth == 0) scriptText.substring(startIdx, pos) else null
    }

    /**
     * Find a JSON array in text that contains a specific key pattern.
     */
    private fun findJsonArrayContaining(text: String, keyPattern: String): String? {
        var idx = text.indexOf(keyPattern)
        while (idx != -1) {
            // Walk backwards to find the opening [
            var start = idx
            var bracketDepth = 0
            while (start > 0) {
                when (text[start]) {
                    '[' -> {
                        if (bracketDepth == 0) break
                        bracketDepth--
                    }
                    ']' -> bracketDepth++
                }
                start--
            }
            if (start >= 0 && text[start] == '[') {
                // Walk forwards to find the closing ]
                var end = idx
                bracketDepth = 1
                var inString = false
                var escapeNext = false
                end++
                while (end < text.length && bracketDepth > 0) {
                    val c = text[end]
                    when {
                        escapeNext -> escapeNext = false
                        c == '\\' -> escapeNext = true
                        c == '"' && !inString -> inString = true
                        c == '"' && inString -> inString = false
                        !inString && c == '[' -> bracketDepth++
                        !inString && c == ']' -> bracketDepth--
                    }
                    end++
                }
                if (bracketDepth == 0) {
                    return text.substring(start, end)
                }
            }
            idx = text.indexOf(keyPattern, idx + 1)
        }
        return null
    }

    /**
     * Recursively search a JSONObject for chapter arrays.
     */
    private fun deepFindChapters(obj: JSONObject, bookId: String): List<NovelChapter> {
        val result = mutableListOf<NovelChapter>()
        val keys = obj.keys().asSequence().toList()
        android.util.Log.d("WebNovel", "deepFindChapters keys at this level: $keys")

        // Direct chapter arrays
        for (key in listOf("chapterItems", "chapterList", "chapters", "volumeItems", "groupItems")) {
            val arr = obj.optJSONArray(key)
            if (arr != null) {
                android.util.Log.d("WebNovel", "Found array at key '$key' with ${arr.length()} items")
                if (key == "volumeItems" || key == "groupItems") {
                    // Volume/group items may contain nested chapter arrays
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val nested = deepFindChapters(item, bookId)
                        if (nested.isNotEmpty()) result.addAll(nested)
                    }
                } else {
                    result.addAll(parseChapterJsonArray(arr, bookId))
                }
                if (result.isNotEmpty()) return result
            }
        }

        // Search nested objects for catalog-like structures
        for (key in keys) {
            if (key in listOf("chapterItems", "chapterList", "chapters", "volumeItems", "groupItems")) continue
            val nested = obj.optJSONObject(key) ?: continue
            val nestedResult = deepFindChapters(nested, bookId)
            if (nestedResult.isNotEmpty()) return nestedResult
        }

        return result
    }

    private fun extractChaptersFromCatalog(catalog: JSONObject, bookId: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()

        // Try different structures
        val chapterItems = catalog.optJSONArray("chapterItems")
            ?: catalog.optJSONArray("chapterList")
            ?: catalog.optJSONArray("chapters")
        if (chapterItems != null) {
            chapters.addAll(parseChapterJsonArray(chapterItems, bookId))
        }

        val volumeItems = catalog.optJSONArray("volumeItems")
            ?: catalog.optJSONArray("volumes")
        if (volumeItems != null && chapters.isEmpty()) {
            for (i in 0 until volumeItems.length()) {
                val vol = volumeItems.optJSONObject(i) ?: continue
                val volChapters = vol.optJSONArray("chapterItems")
                    ?: vol.optJSONArray("chapters")
                    ?: vol.optJSONArray("chapterList")
                if (volChapters != null) {
                    chapters.addAll(parseChapterJsonArray(volChapters, bookId))
                }
            }
        }

        return chapters
    }

    private fun parseChapterJsonArray(array: JSONArray, bookId: String): List<NovelChapter> {
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val chapterId = item.optString("chapterId", "")
                .ifBlank { item.optString("id", "") }
                .ifBlank { item.optString("cid", "") }
            val rawName = item.optString("chapterName", "")
                .ifBlank { item.optString("name", "") }
                .ifBlank { item.optString("chapterIndex", "") }
                .ifBlank { item.optString("title", "") }
            val chapterName = cleanChapterName(rawName)
            if (chapterId.isBlank() || chapterName.isBlank()) return@mapNotNull null
            val dateUpload = readApiDateUpload(item)
            NovelChapter(
                url = "$baseUrl/book/$bookId/chapter/$chapterId",
                name = chapterName,
                dateUpload = dateUpload,
                chapterNumber = item.optString("chapterIndex", "0").toFloatOrNull() ?: (i + 1).toFloat()
            )
        }
    }

    private fun readApiDateUpload(item: org.json.JSONObject): Long {
        val dateFields = listOf(
            "publishTime",
            "pubTime",
            "updateTime",
            "createTime",
            "releaseTime",
            "time",
            "publishDate",
            "updateDate",
        )
        for (field in dateFields) {
            val value = item.opt(field) ?: continue
            when (value) {
                is Long -> return value
                is Number -> return value.toLong()
                is String -> parseDate(value)?.let { return it }
            }
        }
        return 0L
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val bookId = extractBookId(chapterUrl)
        val chapterId = extractChapterId(chapterUrl)
        if (bookId.isBlank() || chapterId.isBlank()) return ""

        android.util.Log.d("WebNovel", "Fetching content for bookId=$bookId chapterId=$chapterId")

        // Try API first, but fall back to HTML on any failure
        val apiContent = try {
            val data = fetchChapterContent(bookId, chapterId)
            android.util.Log.d("WebNovel", "Content response keys: ${data.keys().asSequence().toList()}")

            // Check if chapter is locked/paywalled
            val isLocked = data.optJSONObject("chapterInfo")?.optBoolean("isLocked", false) == true
                || data.optJSONObject("chapterInfo")?.optBoolean("isVip", false) == true
                || data.optBoolean("isLocked", false) == true
            if (isLocked) {
                android.util.Log.w("WebNovel", "Chapter is locked/paywalled, content unavailable")
            }

            val chapterInfo = data.optJSONObject("chapterInfo")
            var content: String? = null

            if (chapterInfo != null) {
                android.util.Log.d("WebNovel", "chapterInfo keys: ${chapterInfo.keys().asSequence().toList()}")
                content = when {
                    chapterInfo.has("content") -> chapterInfo.optString("content", "")
                    chapterInfo.has("contents") -> {
                        val arr = chapterInfo.optJSONArray("contents")
                        (0 until (arr?.length() ?: 0))
                            .mapNotNull { arr?.getJSONObject(it)?.optString("content") }
                            .filter { it.isNotBlank() }
                            .joinToString("")
                    }
                    chapterInfo.has("chapterContent") -> chapterInfo.optString("chapterContent", "")
                    chapterInfo.has("textContent") -> chapterInfo.optString("textContent", "")
                    else -> null
                }
            }

            // If chapterInfo didn't have content, try top-level data fields
            if (content.isNullOrBlank()) {
                content = when {
                    data.has("content") -> data.optString("content", "")
                    data.has("chapterContent") -> data.optString("chapterContent", "")
                    data.has("textContent") -> data.optString("textContent", "")
                    else -> null
                }
                if (!content.isNullOrBlank()) {
                    android.util.Log.d("WebNovel", "Found content at data level")
                }
            }

            if (content.isNullOrBlank()) {
                android.util.Log.e("WebNovel", "No content in API response, trying HTML fallback")
            }
            content
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "API content fetch failed: ${e.message}, trying HTML fallback")
            null
        }

        if (!apiContent.isNullOrBlank()) {
            android.util.Log.d("WebNovel", "API content length: ${apiContent.length}")
            return formatContent(apiContent)
        }

        return fetchChapterContentFromHtml(chapterUrl)
    }

    private suspend fun fetchChapterContentFromHtml(chapterUrl: String): String {
        return try {
            android.util.Log.d("WebNovel", "Fetching chapter HTML: $chapterUrl")
            val doc = getDocument(chapterUrl)

            // Strategy 1: Try known WebNovel content container selectors
            // .cha-words is where the actual story paragraphs live.
            // ._content only contains the chapter title, not the body text.
            val selectors = listOf(
                ".cha-words",           // Main story text container (confirmed from scrape)
                ".chapter_content",     // Outer chapter wrapper
                ".cha-content",         // Inner content wrapper
                ".chapter-content",
                "[class*='chapterContent']",
                "[class*='chapter-content']",
                "._content",            // Often just the title, try after real content selectors
                ".content",
                "[class*='content']",
                "article",
                ".read",
                ".text",
                "#content",
                ".p-directory",
            )
            for (selector in selectors) {
                val el = doc.selectFirst(selector)
                if (el != null) {
                    val html = el.html()
                    // Must contain substantial paragraph tags to be real content
                    // ._content passes html.length>200 but only has the title.
                    // Real chapter content is typically 5000+ chars with many <p> tags.
                    val paragraphCount = html.split("<p").size - 1
                    if (html.isNotBlank() && paragraphCount >= 3 && html.length > 1000) {
                        android.util.Log.d("WebNovel", "Found HTML content with selector '$selector' (len=${html.length}, paragraphs=$paragraphCount)")
                        return formatContent(html)
                    }
                }
            }

            // Strategy 2: Find the largest cluster of <p> tags (the chapter body)
            val allParagraphs = doc.select("p").toList()
                .filter { it.text().isNotBlank() && it.text().length > 30 }
            if (allParagraphs.isNotEmpty()) {
                // Group paragraphs by their closest common parent to find the main chapter block
                val parentGroups = allParagraphs.groupBy { it.parent() }
                val bestGroup = parentGroups.maxByOrNull { it.value.size }
                if (bestGroup != null && bestGroup.value.size >= 3) {
                    val contentHtml = bestGroup.value.joinToString("\n") { it.outerHtml() }
                    android.util.Log.d("WebNovel", "HTML content from paragraph cluster (len=${contentHtml.length})")
                    return formatContent(contentHtml)
                }
            }

            // Strategy 3: Last resort — all paragraphs
            val paragraphs = doc.select("p").toList()
                .map { it.text() }
                .filter { it.isNotBlank() && it.length > 20 }
                .joinToString("\n\n")
            android.util.Log.d("WebNovel", "HTML fallback paragraphs length: ${paragraphs.length}")
            if (paragraphs.isNotBlank()) formatContent(paragraphs) else ""
        } catch (e: Exception) {
            android.util.Log.e("WebNovel", "HTML content fallback failed: ${e.message}")
            ""
        }
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

        // Visit the homepage to get cookies, then extract CSRF token
        val response = get("$baseUrl/")
        val body = response.body?.string() ?: ""
        response.body?.close()

        // Try multiple patterns to extract CSRF token
        val patterns = listOf(
            Regex("_csrfToken['\"]?\\s*[:=]\\s*['\"]([^'\"]+)"),
            Regex("csrfToken['\"]?\\s*[:=]\\s*['\"]([^'\"]+)"),
            Regex("window\\._csrfToken\\s*=\\s*['\"]([^'\"]+)"),
            Regex("_csrfToken\\s*:\\s*['\"]([^'\"]+)"),
        )

        var extracted = ""
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                extracted = match.groupValues[1]
                android.util.Log.d("WebNovel", "CSRF extracted with pattern: ${pattern.pattern}")
                break
            }
        }

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

        android.util.Log.d("WebNovel", "BookInfo raw response (first 2000 chars): ${response.take(2000)}")
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

    /**
     * Cleans up chapter names by removing leading index numbers and trailing timestamps.
     * Handles patterns like "1. Chapter Name", "01 Chapter Name", and "Chapter Name 4 years ago".
     */
    private fun cleanChapterName(name: String): String {
        var cleaned = name.trim()
        if (cleaned.isBlank()) return ""

        // Remove leading chapter index like "1. " or "1     " or "01 " or "Chapter 1: "
        cleaned = cleaned.replace(Regex("^\\d+\\s*[:\\.]?\\s*"), "").trim()
        cleaned = cleaned.replace(Regex("^(Chapter|Ch\\.?|Vol\\.?|Volume)\\s*\\d+\\s*[:\\.]?\\s*", RegexOption.IGNORE_CASE), "").trim()

        // Remove trailing relative date like "4 years ago", "2 months ago", "1 day ago"
        // Also handles Unicode non-breaking spaces and common variants
        cleaned = cleaned.replace(Regex("\\s+[\\d\\s]+\\s*(years?|months?|weeks?|days?|hours?|mins?|minutes?)\\s*ago\\s*$", RegexOption.IGNORE_CASE), "").trim()

        // Remove trailing "ago" patterns without explicit time unit (e.g., "Just now", "Yesterday")
        cleaned = cleaned.replace(Regex("\\s+(Just now|Yesterday|Today)\\s*$", RegexOption.IGNORE_CASE), "").trim()

        return cleaned
    }

    private fun parseWebnovelStatus(statusText: String?): NovelStatus {
        if (statusText.isNullOrBlank()) return NovelStatus.UNKNOWN
        return when {
            statusText.contains("ongoing", ignoreCase = true) -> NovelStatus.ONGOING
            statusText.contains("completed", ignoreCase = true) || statusText.contains("finished", ignoreCase = true) -> NovelStatus.COMPLETED
            statusText.contains("hiatus", ignoreCase = true) -> NovelStatus.ON_HIATUS
            statusText.contains("dropped", ignoreCase = true) || statusText.contains("cancelled", ignoreCase = true) -> NovelStatus.CANCELLED
            statusText.contains("licensed", ignoreCase = true) -> NovelStatus.LICENSED
            statusText == "0" || statusText == "1" -> {
                // Numeric status codes sometimes used by WebNovel API
                when (statusText) {
                    "0" -> NovelStatus.ONGOING
                    "1" -> NovelStatus.COMPLETED
                    else -> NovelStatus.UNKNOWN
                }
            }
            else -> NovelStatus.UNKNOWN
        }
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

