package yokai.extension.novel.en.annasarchive.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import yokai.extension.novel.en.annasarchive.model.AnnasBookDetails
import yokai.extension.novel.en.annasarchive.model.AnnasBookResult
import yokai.extension.novel.en.annasarchive.model.AnnasDownloadLink
import yokai.extension.novel.en.annasarchive.model.DownloadLinkType
import java.io.IOException

/**
 * Client for interacting with Anna's Archive website.
 * Uses HTML scraping for search and book details retrieval.
 */
class AnnasArchiveApi(private val client: OkHttpClient) {

    companion object {
        private val BASE_URLS = listOf(
            "https://annas-archive.li",
            "https://annas-archive.gs",
            "https://annas-archive.vg",
            "https://annas-archive.org",
            "https://annas-archive.gl",
            "https://annas-archive.gd",
            "https://annas-archive.pk",
            "https://annas-archive.se",
        )
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val DIRECT_DOWNLOAD_PATTERN =
            Regex(".*\\.(epub|pdf|mobi|azw3|fb2|djvu|txt)(\\?.*)?$", RegexOption.IGNORE_CASE)
    }

    @Volatile
    private var workingBaseUrl: String? = null

    /**
     * Search Anna's Archive for EPUB books matching [query].
     * Uses the HTML search page with ext=epub filter.
     */
    suspend fun search(query: String, page: Int = 1): List<AnnasBookResult> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val path = "/search?ext=epub&sort=&q=$encodedQuery&page=$page"

        val (html, baseUrl) = fetchHtmlWithFallback(path)
        // Anna's Archive wraps some content in HTML comments — unwrap them
        val unwrapped = html.replace(Regex("<!--([\\W\\w]*?)-->")) { it.groupValues[1] }
        val document = Jsoup.parse(unwrapped, baseUrl + path)

        return parseSearchResults(document)
    }

    /**
     * Browse newest EPUB books (no search query, sorted by newest).
     */
    suspend fun searchNewest(page: Int = 1): List<AnnasBookResult> {
        val path = "/search?ext=epub&sort=newest&page=$page"

        val (html, baseUrl) = fetchHtmlWithFallback(path)
        val unwrapped = html.replace(Regex("<!--([\\W\\w]*?)-->")) { it.groupValues[1] }
        val document = Jsoup.parse(unwrapped, baseUrl + path)

        return parseSearchResults(document)
    }

    /**
     * Get detailed book information from the book page at /md5/{md5}.
     */
    suspend fun getBookDetails(md5: String): AnnasBookDetails {
        val path = "/md5/$md5"
        val (html, baseUrl) = fetchHtmlWithFallback(path)
        // Unwrap HTML comments
        val unwrapped = html.replace(Regex("<!--([\\W\\w]*?)-->")) { it.groupValues[1] }
        val url = baseUrl + path
        val document = Jsoup.parse(unwrapped, url)

        return parseBookDetails(document, md5, url)
    }

    /**
     * Get book details by full URL (e.g., https://annas-archive.org/md5/abc123).
     */
    suspend fun getBookDetailsByUrl(bookUrl: String): AnnasBookDetails {
        val md5 = extractMd5FromUrl(bookUrl)
            ?: throw IOException("Cannot extract MD5 from URL: $bookUrl")
        return getBookDetails(md5)
    }

    /**
     * Extract MD5 hash from an Anna's Archive URL.
     * URLs look like: https://annas-archive.org/md5/{md5}
     */
    fun extractMd5FromUrl(url: String): String? {
        val regex = Regex("/md5/([a-fA-F0-9]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    // ===== Private parsing methods =====

    private fun parseSearchResults(document: Document): List<AnnasBookResult> {
        // QuickNovel uses: #aarecord-list > div > div > div > a
        // The existing extension uses: div[class*='mb-3'] with a[href*='/md5/']
        // Try multiple selectors for resilience

        val items = document.select("a[href*='/md5/']")
            .filter { element ->
                // Filter out download links and navigation, keep only result entries
                val href = element.attr("href")
                href.matches(Regex(".*/md5/[a-fA-F0-9]+$"))
            }

        // If the specific selector works, use it; otherwise fall back
        val resultElements = if (items.isNotEmpty()) {
            // Deduplicate by href (multiple links may point to same book)
            items.distinctBy { it.attr("href") }
        } else {
            document.select("div[class*='mb-3']").mapNotNull { div ->
                div.selectFirst("a[href*='/md5/']")
            }.distinctBy { it.attr("href") }
        }

        return resultElements.mapNotNull { element -> parseSearchResultItem(element) }
    }

    private fun parseSearchResultItem(element: Element): AnnasBookResult? {
        val href = element.attr("href") ?: return null
        val md5 = extractMd5FromUrl(href) ?: return null

        // Try to find title — QuickNovel uses: div.relative > h3
        val title = element.selectFirst("h3")?.text()?.trim()
            ?: element.selectFirst("div[class*='text-']")?.text()?.trim()
            ?: element.text()?.trim()
            ?: return null

        // Skip empty or very short titles
        if (title.isBlank() || title.length < 2) return null

        // Try to find cover image
        val coverUrl = element.selectFirst("img")?.let { img ->
            val src = img.attr("src").ifBlank { img.attr("data-src") }
            if (src.isNotBlank()) fixUrl(src) else null
        }

        // Try to find author — look for "by" text or italic text
        val author = element.selectFirst("div:containsOwn(by)")?.text()
            ?.replace(Regex("^by\\s+", RegexOption.IGNORE_CASE), "")?.trim()
            ?: element.selectFirst("div.italic")?.text()?.trim()

        // Try to find file size and format from the result text
        val fullText = element.text()
        val format = "epub" // We filter by ext=epub, so all results should be EPUB
        val filesize = extractFilesize(fullText)
        val language = extractLanguage(fullText)
        val year = extractYear(fullText)

        return AnnasBookResult(
            title = title,
            author = author?.takeIf { it.isNotBlank() },
            md5 = md5,
            coverUrl = coverUrl,
            filesize = filesize,
            format = format,
            language = language,
            year = year,
            url = fixUrl(href)
        )
    }

    private fun parseBookDetails(document: Document, md5: String, pageUrl: String): AnnasBookDetails {
        // QuickNovel uses: main > div > div.text-3xl for title
        val title = document.selectFirst("div.text-3xl")?.ownText()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("h3")?.text()?.trim()
            ?: "Unknown Title"

        // QuickNovel uses: main > div > div.italic for author
        val author = document.selectFirst("div.italic")?.ownText()?.trim()
            ?: document.selectFirst("div:containsOwn(Author)")?.text()
                ?.replace(Regex("^Author:?\\s*", RegexOption.IGNORE_CASE), "")?.trim()

        // QuickNovel uses: main > div > div.js-md5-top-box-description for description
        val description = document.selectFirst("div.js-md5-top-box-description")?.text()?.trim()
            ?: document.selectFirst("div[id^='description']")?.text()?.trim()

        // QuickNovel uses: main > div > div > img for cover
        val coverUrl = document.selectFirst("main img")?.let { img ->
            val src = img.attr("src").ifBlank { img.attr("data-src") }
            if (src.isNotBlank()) fixUrl(src) else null
        }

        // Extract metadata from the page text
        val fullText = document.text()
        val format = extractFormat(fullText) ?: "epub"
        val filesize = extractFilesize(fullText)
        val language = extractLanguage(fullText)
        val year = extractYear(fullText)
        val publisher = extractPublisher(fullText)

        // Extract download links — QuickNovel uses: ul.mb-4 > li > a.js-download-link
        val downloadLinks = extractDownloadLinks(document)

        return AnnasBookDetails(
            title = title,
            author = author?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            coverUrl = coverUrl,
            md5 = md5,
            format = format,
            filesize = filesize,
            language = language,
            publisher = publisher,
            year = year,
            downloadLinks = downloadLinks
        )
    }

    private fun extractDownloadLinks(document: Document): List<AnnasDownloadLink> {
        // Try QuickNovel's selector first: ul.mb-4 > li > a.js-download-link
        var links = document.select("a.js-download-link").mapNotNull { element ->
            parseDownloadLink(element)
        }

        // Fallback: any anchor with download-related href
        if (links.isEmpty()) {
            links = document.select("a[href*='download']").mapNotNull { element ->
                parseDownloadLink(element)
            }
        }

        // Also look for slow_download links specifically
        val slowDownloadLinks = document.select("a[href*='slow_download']").mapNotNull { element ->
            parseDownloadLink(element)
        }

        // Merge and deduplicate by URL
        return (links + slowDownloadLinks).distinctBy { it.url }
    }

    private fun parseDownloadLink(element: Element): AnnasDownloadLink? {
        val href = element.attr("href") ?: return null
        if (href.isBlank()) return null

        val url = fixUrl(href)
        val label = element.text()?.trim()?.ifBlank { "Download" } ?: "Download"

        val type = when {
            href.contains("fast_download") -> DownloadLinkType.FAST_DOWNLOAD
            href.contains("slow_download") -> DownloadLinkType.SLOW_DOWNLOAD
            href.contains("ipfs") -> DownloadLinkType.IPFS
            DIRECT_DOWNLOAD_PATTERN.matches(url) -> DownloadLinkType.DIRECT
            href.contains("libgen") || href.contains("z-lib") || href.contains("zlibrary") -> DownloadLinkType.PARTNER
            href.endsWith("/datasets") -> return null // Skip non-download links
            else -> DownloadLinkType.UNKNOWN
        }

        // Skip member-only fast_download links
        if (type == DownloadLinkType.FAST_DOWNLOAD) return null

        return AnnasDownloadLink(url = url, label = label, type = type)
    }

    // ===== Utility methods =====

    private fun fixUrl(url: String): String {
        val base = workingBaseUrl ?: BASE_URLS.first()
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$base$url"
            else -> "$base/$url"
        }
    }

    private suspend fun fetchHtmlWithFallback(path: String): Pair<String, String> {
        // Try cached working domain first
        val cached = workingBaseUrl
        if (cached != null) {
            try {
                val html = fetchHtml(cached + path)
                return Pair(html, cached)
            } catch (e: Exception) {
                // Cached domain failed, clear cache and try all
                workingBaseUrl = null
            }
        }

        // Try all domains in order
        var lastError: Exception? = null
        for (baseUrl in BASE_URLS) {
            try {
                val html = fetchHtml(baseUrl + path)
                workingBaseUrl = baseUrl
                return Pair(html, baseUrl)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("All Anna's Archive domains failed: ${lastError?.message}", lastError)
    }

    private suspend fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $url")
                }
                response.body?.string() ?: throw IOException("Empty response body for $url")
            }
        } catch (e: Exception) {
            throw IOException("Failed to fetch $url: ${e.message}", e)
        }
    }

    private fun extractFilesize(text: String): String? {
        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*(KB|MB|GB)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value
    }

    private fun extractLanguage(text: String): String? {
        // Look for language codes or common language names
        val langRegex = Regex("\\b(English|en|Spanish|es|French|fr|German|de|Italian|it|Portuguese|pt|Russian|ru|Chinese|zh|Japanese|ja|Korean|ko|Arabic|ar)\\b", RegexOption.IGNORE_CASE)
        return langRegex.find(text)?.value?.lowercase()
    }

    private fun extractYear(text: String): String? {
        val regex = Regex("\\b(19|20)\\d{2}\\b")
        return regex.find(text)?.value
    }

    private fun extractFormat(text: String): String? {
        val regex = Regex("\\b(epub|pdf|mobi|azw3|fb2|djvu|txt)\\b", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value?.lowercase()
    }

    private fun extractPublisher(text: String): String? {
        val regex = Regex("Publisher:?\\s*(.+?)(?:\\s{2,}|$)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }
}
