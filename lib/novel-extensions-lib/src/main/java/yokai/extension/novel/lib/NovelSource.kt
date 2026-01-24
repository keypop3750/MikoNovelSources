package yokai.extension.novel.lib

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Base abstract class for novel source extensions.
 * All novel sources must extend this class and implement the required methods.
 * 
 * Source ID ranges:
 * - 6000-6099: English sources
 * - 6100-6199: Indonesian sources  
 * - 6200-6299: Turkish sources
 * - 6300-6399: Portuguese sources
 * - 6400-6499: Multi-language sources
 * - 6500+: Reserved for future use
 */
abstract class NovelSource {
    
    /**
     * Unique identifier for this source. Must be stable across versions.
     * Use the ID ranges defined above.
     */
    abstract val id: Long
    
    /**
     * Display name of the source shown to users.
     */
    abstract val name: String
    
    /**
     * Base URL of the source website.
     */
    abstract val baseUrl: String
    
    /**
     * Language code (ISO 639-1). Examples: "en", "id", "tr", "pt-BR"
     */
    open val lang: String = "en"
    
    /**
     * Whether this source has a browseable main/popular page.
     */
    open val hasMainPage: Boolean = false
    
    /**
     * Whether this source contains NSFW content.
     */
    open val isNsfw: Boolean = false
    
    /**
     * Version name for display (e.g., "1.0.0").
     */
    open val versionName: String = "1.0.0"
    
    /**
     * Version code for update checking.
     */
    open val versionCode: Int = 1
    
    /**
     * Minimum delay between requests in milliseconds.
     */
    open val rateLimitMs: Long = 1000L
    
    /**
     * HTTP client for making requests. Injected by the app.
     */
    lateinit var client: OkHttpClient
    
    /**
     * Default headers to include with all requests.
     */
    open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", DEFAULT_USER_AGENT)
    
    protected val headers: Headers by lazy { headersBuilder().build() }
    
    // ===== Core Methods (Must Implement) =====
    
    /**
     * Search for novels matching the query.
     * @param query Search string entered by user
     * @param page Page number (1-indexed)
     * @return List of search results
     */
    abstract suspend fun search(query: String, page: Int = 1): List<NovelSearchResult>
    
    /**
     * Get detailed information about a novel.
     * @param url URL of the novel's main page
     * @return Novel details including metadata
     */
    abstract suspend fun getNovelDetails(url: String): NovelDetails
    
    /**
     * Get list of chapters for a novel.
     * @param novelUrl URL of the novel's main page
     * @return List of chapters in reading order (first chapter first)
     */
    abstract suspend fun getChapterList(novelUrl: String): List<NovelChapter>
    
    /**
     * Get the content of a chapter.
     * @param chapterUrl URL of the chapter page
     * @return Chapter content as HTML or plain text
     */
    abstract suspend fun getChapterContent(chapterUrl: String): String
    
    // ===== Optional Methods =====
    
    /**
     * Get latest updated novels.
     */
    open suspend fun getLatestUpdates(page: Int = 1): List<NovelSearchResult> = emptyList()
    
    /**
     * Get popular/trending novels.
     */
    open suspend fun getPopularNovels(page: Int = 1): List<NovelSearchResult> = emptyList()
    
    /**
     * Get available filter options for browsing.
     */
    open fun getFilterList(): List<NovelFilter> = emptyList()
    
    // ===== Helper Methods =====
    
    /**
     * Make a GET request and return the response.
     */
    protected suspend fun get(url: String, headers: Headers = this.headers): Response {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .build()
        return client.newCall(request).execute()
    }
    
    /**
     * Make a GET request and parse the response as an HTML document.
     */
    protected suspend fun getDocument(url: String, headers: Headers = this.headers): Document {
        val response = get(url, headers)
        val body = response.body?.string() ?: ""
        return Jsoup.parse(body, url)
    }
    
    /**
     * Parse HTML string into a Document.
     */
    protected fun parseHtml(html: String): Document {
        return Jsoup.parse(html)
    }
    
    /**
     * Make a POST request with form data.
     */
    protected suspend fun postForm(url: String, data: Map<String, String>, headers: Headers = this.headers): String {
        val formBody = okhttp3.FormBody.Builder().apply {
            data.forEach { (key, value) -> add(key, value) }
        }.build()
        
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .post(formBody)
            .build()
        
        return client.newCall(request).execute().body?.string() ?: ""
    }

    /**
     * Fix a relative URL to be absolute.
     */
    protected fun fixUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }
    
    /**
     * Fix a URL that might be null.
     */
    protected fun fixUrlOrNull(url: String?): String? {
        return url?.let { fixUrl(it) }
    }
    
    /**
     * URL-encode a string.
     */
    protected fun String.encodeUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
    
    /**
     * Parse a date string to timestamp.
     */
    protected fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            // Try ISO format first
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(dateStr)?.time
                ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse novel status from text.
     */
    protected fun parseNovelStatus(status: String?): NovelStatus {
        return when {
            status == null -> NovelStatus.UNKNOWN
            status.contains("ongoing", ignoreCase = true) -> NovelStatus.ONGOING
            status.contains("completed", ignoreCase = true) -> NovelStatus.COMPLETED
            status.contains("hiatus", ignoreCase = true) -> NovelStatus.ON_HIATUS
            status.contains("dropped", ignoreCase = true) -> NovelStatus.CANCELLED
            else -> NovelStatus.UNKNOWN
        }
    }
    
    companion object {
        const val DEFAULT_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

/**
 * Factory interface for creating multiple sources from a single extension.
 */
interface NovelSourceFactory {
    /**
     * Create all sources provided by this extension.
     */
    fun createSources(): List<NovelSource>
}
