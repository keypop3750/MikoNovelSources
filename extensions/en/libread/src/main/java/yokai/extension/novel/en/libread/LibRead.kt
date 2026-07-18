package yokai.extension.novel.en.libread

import yokai.extension.novel.lib.*
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LibRead source (libread.com)
 * Popular web novel aggregator with a clean interface.
 * 
 * This source includes FreeWebNovel (freewebnovel.com) as a fallback mirror.
 * FreeWebNovel is migrating to LibRead, so they share the same structure.
 * 
 * Structure:
 * - Popular: /sort/most-popular
 * - Latest: /sort/latest-release
 * - Novel items: div.li-row with div.pic img and h3.tit > a
 * - Novel URLs: /libread/{slug}-{id}
 * - Cover images: /files/article/image/{folder}/{id}/{id}s.jpg
 * 
 * Supported filters:
 * - Sort: Popular, Latest
 */
class LibRead : ConfigurableNovelSource() {
    
    override val id: Long = 6020L
    override val name: String = "LibRead"
    override val baseUrl: String = "https://libread.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    /**
     * Fallback mirror URLs. If primary fails, try these in order.
     * FreeWebNovel is merging into LibRead, so they share the same structure.
     *
     * TODO: Hook up FreeWebNovel as fallback, but avoid duplicate chapters.
     * The fallback was causing duplicate chapter issues — re-enable once
     * deduplication is robust enough to handle cross-site duplicates.
     */
    private val fallbackUrls = listOf<String>(
        // "https://freewebnovel.com"
    )
    
    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "last_updated"),
        supportsSortDirection = false,
        supportedGenres = emptyList(),  // No genre browse pages
        supportsGenreExclusion = false,
        supportedStatuses = emptyList(),
        supportedContentWarnings = emptyList(),
        supportsContentWarningExclusion = false,
        supportsChapterCountFilter = false,
        supportsRatingFilter = false,
        supportsSearch = true,
        supportsAuthorFilter = false
    )
    
    /**
     * Browse novels with applied filters.
     * LibRead supports basic sort options only.
     */
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"] ?: "popular"
        
        return when (sort) {
            "last_updated" -> getLatestUpdates(page)
            else -> getPopularNovels(page)  // Default to popular
        }
    }
    
    override val selectors = SourceSelectors(
        // Browse selectors
        browseItemSelector = "div.li-row",
        browseTitleSelector = "h3.tit > a",
        browseCoverSelector = "div.pic img",
        
        // Search selectors
        searchItemSelector = "div.li-row",
        searchTitleSelector = "h3.tit > a",
        searchCoverSelector = "div.pic img",
        
        // Novel details selectors
        detailTitleSelector = "h1.tit",
        detailCoverSelector = "div.pic img",
        descriptionSelector = "div.inner",
        authorSelector = "p:contains(Author) a, span.author a",
        genreSelector = "p:contains(Genre) a, div.right a.novel",
        statusSelector = "span.s2",
        
        // Chapter list
        chapterListSelector = "option",
        
        // Chapter content
        chapterContentSelector = "div.txt",
        contentRemoveSelectors = listOf("script", "div.ads", "ins", "div.chapter-warning", "p.report-tips"),
        contentRemovePatterns = listOf(
            "libread.com",
            "freewebnovel.com",
            "Please report us if you find any errors",
            "New novel chapters are published on Freewebnovel.com.",
            "The source of this content is Freewebnᴏvel.com.",
            "We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters!"
        ),
        
        // URL patterns
        searchUrlPattern = "$baseUrl/search",
        popularUrlPattern = "$baseUrl/sort/most-popular",
        latestUrlPattern = "$baseUrl/sort/latest-release",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search to use POST request with fallback support
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        return searchWithFallback(query)
    }
    
    private suspend fun searchWithFallback(query: String): List<NovelSearchResult> {
        // Try primary site first
        try {
            val response = postForm(
                url = "$baseUrl/search",
                data = mapOf("searchkey" to query),
                headerMap = mapOf(
                    "Referer" to baseUrl,
                    "Content-Type" to "application/x-www-form-urlencoded"
                )
            )
            val document = Jsoup.parse(response, baseUrl)
            val results = parseNovelList(document)
            if (results.isNotEmpty()) return results
        } catch (e: Exception) {
            android.util.Log.w("LibRead", "Primary search failed: ${e.message}")
        }
        
        // Try fallback mirrors
        for (fallbackBase in fallbackUrls) {
            try {
                val response = postForm(
                    url = "$fallbackBase/search",
                    data = mapOf("searchkey" to query),
                    headerMap = mapOf(
                        "Referer" to fallbackBase,
                        "Content-Type" to "application/x-www-form-urlencoded"
                    )
                )
                val document = Jsoup.parse(response, fallbackBase)
                val results = parseNovelList(document).map { result ->
                    // Rewrite URLs to use primary baseUrl
                    result.copy(
                        url = result.url.replace(fallbackBase, baseUrl),
                        coverUrl = result.coverUrl?.replace(fallbackBase, baseUrl)
                    )
                }
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                android.util.Log.w("LibRead", "Fallback search failed ($fallbackBase): ${e.message}")
            }
        }
        
        return emptyList()
    }
    
    // Override getPopularNovels - correct URL is /sort/most-popular
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/most-popular"
        } else {
            "$baseUrl/sort/most-popular/$page"
        }
        return parseNovelList(getDocument(url))
    }
    
    // Override getLatestUpdates - URL is /sort/latest-release
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/sort/latest-release"
        } else {
            "$baseUrl/sort/latest-release/$page"
        }
        return parseNovelList(getDocument(url))
    }
    
    /**
     * Parse novel list from browse/search pages.
     * Structure: div.li-row contains div.pic > a > img and div.txt > h3.tit > a
     */
    private fun parseNovelList(document: Document): List<NovelSearchResult> {
        return document.select("div.li-row").mapNotNull { row ->
            // Get title and URL from h3.tit > a
            val titleElement = row.selectFirst("h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifBlank { titleElement.text() }.trim()
            val url = titleElement.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            // Get cover from div.pic img
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
    
    // Override getNovelDetails
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1.tit")?.text()?.trim()
            ?: throw Exception("Could not find novel title")
        
        // Cover image in div.pic img
        val coverUrl = document.selectFirst("div.pic img, img[src*='/files/article/']")
            ?.attr("src")
            ?.let { fixUrl(it) }
        
        // Description in div.inner
        val description = document.selectFirst("div.inner")?.text()
        
        // Author
        val author = document.selectFirst("p:contains(Author) a, span.author a")?.text()?.trim()
        
        // Genres from the item description area
        val genres = document.select("div.right a.novel, p:contains(Genre) a").map { it.text().trim() }
        
        // Status - look for span.s2 with "Full" or "Ongoing"
        val statusText = document.selectFirst("span.s2")?.text()
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
    
    // Override getChapterList to use the AJAX chapter list endpoint.
    // The old chapterlist.php API returned malformed URLs with "-0" slug placeholder
    // and required error-prone URL reconstruction (wrong zero-padding).
    // The ?ajax=chapters endpoint returns correct, ready-to-use chapter URLs.
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        val pageSize = 40

        while (true) {
            val ajaxUrl = "$novelUrl?ajax=chapters&page=$page&pageSize=$pageSize"
            val response = get(ajaxUrl, headers).body?.string() ?: break

            // Parse JSON response: {"code":200,"html":"<li>...</li>","page":N,"totalChapters":M}
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
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

            // Check if we've fetched all chapters
            if (totalChapters > 0 && chapters.size >= totalChapters) break
            if (chapterLinks.size < pageSize) break

            page++
        }

        // Reverse to get first chapter first (AJAX returns newest first)
        return chapters.reversed()
            .distinctBy { it.url }
            .distinctBy { it.chapterNumber }
    }

    // Override getChapterContent — fetches chapter text from div#article (inside div.txt)
    //
    // libread.com chapter pages now 302 redirect to freewebnovel.com. OkHttp follows
    // the redirect by default, but the CloudflareInterceptor is an application interceptor
    // that sees the ORIGINAL request URL (libread.com), not the redirected URL
    // (freewebnovel.com). If freewebnovel.com returns a Cloudflare challenge, the
    // interceptor's WebView bypass loads the libread.com URL — not freewebnovel.com —
    // so the cf_clearance cookie is obtained for the wrong domain and the retry still
    // fails.
    //
    // Fix: disable redirect following for this request, detect the 302, and fetch
    // directly from the redirected freewebnovel.com URL. This way the
    // CloudflareInterceptor handles any challenge on the correct domain.
    override suspend fun getChapterContent(chapterUrl: String): String {
        val finalUrl = resolveChapterUrl(chapterUrl)
        val document = getDocument(finalUrl)

        // Prefer div#article (the actual content container), fall back to div.txt (outer wrapper)
        val content = (document.selectFirst("div#article") ?: document.selectFirst("div.txt"))?.let { element ->
            element.select("script, div.ads, ins, div.chapter-warning, p.report-tips, .reader-ad-skip, div[class*=bg-ssp]").remove()
            element.html()
        } ?: throw Exception("Could not find chapter content")

        // Clean up watermarks
        return content
            .replace("\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62", "", ignoreCase = true)
            .replace("libread.com", "", ignoreCase = true)
            .replace("freewebnovel.com", "", ignoreCase = true)
            .replace("☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜", "")
    }

    /**
     * Resolve a libread.com chapter URL, following a single 302 redirect manually.
     *
     * libread.com redirects chapter pages to freewebnovel.com. We follow the redirect
     * ourselves (instead of letting OkHttp do it) so that Cloudflare challenges on the
     * redirected domain are handled correctly by the CloudflareInterceptor.
     */
    private suspend fun resolveChapterUrl(chapterUrl: String): String {
        return try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(chapterUrl)
                .headers(headers)
                .build()

            val response = noRedirectClient.newCall(request).execute()
            response.use { resp ->
                if (resp.code in 301..308) {
                    val location = resp.header("Location")
                    if (!location.isNullOrBlank()) fixUrl(location) else chapterUrl
                } else {
                    chapterUrl
                }
            }
        } catch (_: Exception) {
            chapterUrl
        }
    }
}
