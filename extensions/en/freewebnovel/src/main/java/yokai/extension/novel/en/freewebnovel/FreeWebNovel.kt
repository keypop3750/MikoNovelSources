package yokai.extension.novel.en.freewebnovel

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FreeWebNovel source (freewebnovel.com)
 *
 * FreeWebNovel is a popular web novel aggregator. LibRead (libread.com) is an
 * intermediary mirror that redirects chapter pages back here, so this source
 * hits FreeWebNovel directly for reliability.
 *
 * Structure:
 * - Popular:   /sort/most-popular/{page}
 * - Latest:    /sort/latest-release/{page}
 * - Completed: /sort/completed-novels/{page}
 * - Novel URLs: /novel/{slug}
 * - Chapter URLs: /novel/{slug}/chapter-{num}
 * - Cover images: /files/article/image/{folder}/{id}/{id}s.jpg
 *
 * Chapter list: GET /novel/{slug}?ajax=chapters&page=N&pageSize=40
 *   Returns JSON: {"code":200,"html":"<li>...</li>","page":N,"totalChapters":M}
 *
 * Chapter content: div#article (inside div.txt)
 *
 * Search: POST /search with form field "searchkey"
 *
 * Supported filters:
 * - Sort: Popular, Latest
 */
class FreeWebNovel : ConfigurableNovelSource() {

    override val id: Long = 6004L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L

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
        supportsAuthorFilter = false
    )

    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"] ?: "popular"
        return when (sort) {
            "last_updated" -> getLatestUpdates(page)
            else -> getPopularNovels(page)
        }
    }

    override val selectors = SourceSelectors(
        // Browse selectors
        browseItemSelector = "div.li-row",
        browseTitleSelector = "h3.tit > a",
        browseCoverSelector = "div.pic img",

        // Search selectors (POST /search returns same div.li-row structure)
        searchItemSelector = "div.li-row",
        searchTitleSelector = "h3.tit > a",
        searchCoverSelector = "div.pic img",

        // Novel details selectors
        detailTitleSelector = "h1.tit",
        detailCoverSelector = "div.pic img",
        descriptionSelector = "div.inner",
        // Author: span.glyphicon-user ~ div.right a (sibling combinator — a is inside div.right)
        authorSelector = "span.glyphicon-user ~ div.right a",
        // Genres: span.glyphicon-th-list ~ div.right a (scoped to info section via sibling)
        genreSelector = "span.glyphicon-th-list ~ div.right a",
        // Status: span.glyphicon-time ~ div.right (text: "OnGoing" or "Full")
        statusSelector = "span.glyphicon-time ~ div.right",

        // Chapter list — overridden with AJAX endpoint
        chapterListSelector = "option",

        // Chapter content
        chapterContentSelector = "div#article",
        contentRemoveSelectors = listOf(
            "script", "div.ads", "ins", "div.chapter-warning",
            "p.report-tips", ".reader-ad-skip", "div[class*=bg-ssp]"
        ),
        contentRemovePatterns = listOf(
            "freewebnovel.com",
            "libread.com",
            "New novel chapters are published on Freewebnovel.com.",
            "The source of this content is Freewebnᴏvel.com.",
            "We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters!"
        ),

        // URL patterns
        searchUrlPattern = "$baseUrl/search",
        popularUrlPattern = "$baseUrl/sort/most-popular/{page}",
        latestUrlPattern = "$baseUrl/sort/latest-release/{page}",

        fetchFullCoverFromDetails = false
    )

    // ===== Search =====

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()

        val response = postForm(
            url = "$baseUrl/search",
            data = mapOf("searchkey" to query),
            headerMap = mapOf(
                "Referer" to baseUrl,
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        )
        val document = Jsoup.parse(response, baseUrl)
        return parseNovelList(document)
    }

    // ===== Browse =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/most-popular"
        } else {
            "$baseUrl/sort/most-popular/$page"
        }
        return parseNovelList(getDocument(url))
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/latest-release"
        } else {
            "$baseUrl/sort/latest-release/$page"
        }
        return parseNovelList(getDocument(url))
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)

        val title = document.selectFirst("h1.tit")?.text()?.trim()
            ?: throw Exception("Could not find novel title")

        val coverUrl = document.selectFirst("div.pic img, img[src*='/files/article/']")
            ?.attr("src")
            ?.let { fixUrl(it) }

        val description = document.selectFirst("div.inner")?.text()

        // Author: a[href*="/author/"] in the info section
        val author = document.selectFirst("span.glyphicon-user ~ div.right a, a[href*='/author/']")?.text()?.trim()

        // Genres: links to /genre/ within the info section (span.glyphicon-th-list ~ div.right a)
        val genres = document.select("span.glyphicon-th-list ~ div.right a").map { it.text().trim() }
            .ifEmpty { document.select("div.m-info a[href*='/genre/']").map { it.text().trim() } }

        // Status: "OnGoing" or "Full" in the status item
        val statusText = document.selectFirst("span.glyphicon-time ~ div.right")?.text()
            ?: document.selectFirst("span.s2")?.text()
        val status = when {
            statusText?.contains("Full", ignoreCase = true) == true -> NovelStatus.COMPLETED
            statusText?.contains("Ongoing", ignoreCase = true) == true -> NovelStatus.ONGOING
            else -> NovelStatus.UNKNOWN
        }

        return NovelDetails(
            url = url,
            title = title,
            coverUrl = coverUrl,
            description = description,
            author = author,
            genres = genres,
            status = status
        )
    }

    // ===== Chapter List =====

    // Use the AJAX chapter list endpoint — returns JSON with HTML fragment.
    // GET /novel/{slug}?ajax=chapters&page=N&pageSize=40
    // Response: {"code":200,"html":"<li><a href="...">...</a></li>","totalChapters":M}
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        val pageSize = 40

        while (true) {
            val ajaxUrl = "$novelUrl?ajax=chapters&page=$page&pageSize=$pageSize"
            val response = get(ajaxUrl, headers).body?.string() ?: break

            val json = Json { ignoreUnknownKeys = true }
            val parsed = try {
                json.parseToJsonElement(response).jsonObject
            } catch (_: Exception) { break }

            val htmlStr = parsed["html"]?.jsonPrimitive?.contentOrNull ?: break
            if (htmlStr.isBlank()) break

            val totalChapters = parsed["totalChapters"]?.jsonPrimitive?.intOrNull ?: 0

            val doc = Jsoup.parse(htmlStr, baseUrl)
            val chapterLinks = doc.select("li > a[href]")
            if (chapterLinks.isEmpty()) break

            chapterLinks.forEach { link ->
                val href = link.attr("href")
                if (href.isBlank()) return@forEach

                val chapterUrl = fixUrl(href)
                val name = link.selectFirst(".title, span.title")?.text()
                    ?: link.attr("title").takeIf { it.isNotBlank() }
                    ?: link.text().takeIf { it.isNotBlank() }
                    ?: "Chapter ${chapters.size + 1}"

                val chNum = Regex("(?:chapter\\s*)?(\\d+)", RegexOption.IGNORE_CASE)
                    .find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f

                chapters.add(
                    NovelChapter(
                        url = chapterUrl,
                        name = name,
                        chapterNumber = chNum
                    )
                )
            }

            if (totalChapters > 0 && chapters.size >= totalChapters) break
            if (chapterLinks.size < pageSize) break

            page++
        }

        // AJAX returns newest-first; reverse to get first chapter first
        return chapters.reversed()
            .distinctBy { it.url }
            .distinctBy { it.chapterNumber }
    }

    // ===== Chapter Content =====

    // Fetches chapter text from div#article (inside div.txt).
    // FreeWebNovel serves content server-side — no JS needed.
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)

        // Prefer div#article (the actual content container), fall back to div.txt
        val content = (document.selectFirst("div#article") ?: document.selectFirst("div.txt"))?.let { element ->
            element.select("script, div.ads, ins, div.chapter-warning, p.report-tips, .reader-ad-skip, div[class*=bg-ssp]").remove()
            element.html()
        } ?: throw Exception("Could not find chapter content")

        // Clean up watermarks
        return content
            .replace("\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62", "", ignoreCase = true)
            .replace("freewebnovel.com", "", ignoreCase = true)
            .replace("libread.com", "", ignoreCase = true)
            .replace("☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜", "")
    }

    // ===== Helpers =====

    /**
     * Parse novel list from browse/search pages.
     * Structure: div.li-row contains div.pic > a > img and div.txt > h3.tit > a
     */
    private fun parseNovelList(document: Document): List<NovelSearchResult> {
        return document.select("div.li-row").mapNotNull { row ->
            val titleElement = row.selectFirst("h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifBlank { titleElement.text() }.trim()
            val url = titleElement.attr("href")

            if (title.isBlank() || url.isBlank()) return@mapNotNull null

            val coverUrl = row.selectFirst("div.pic img")
                ?.attr("src")
                ?.let { fixUrl(it) }

            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }.distinctBy { it.url }
    }
}
