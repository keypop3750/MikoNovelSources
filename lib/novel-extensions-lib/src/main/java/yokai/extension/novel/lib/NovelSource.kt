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
     * WebView resolver for URLs that require CAPTCHA/countdown resolution. Injected by the app.
     * Null if the source doesn't need WebView resolution or if the app hasn't injected it yet.
     */
    var webViewResolver: WebViewResolver? = null
    
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
     * Get the latest chapters for a novel, fetching only enough to get
     * chapters beyond what the user already has.
     *
     * Sources that support pagination can override this to avoid fetching
     * the entire chapter list when only new chapters are needed.
     *
     * @param novelUrl URL of the novel's main page
     * @param existingCount the number of chapters the user already has
     * @return List of chapters (may be partial if the source supports incremental fetching)
     */
    open suspend fun getLatestChapters(novelUrl: String, existingCount: Int): List<NovelChapter> = getChapterList(novelUrl)

    /**
     * Get a single page of chapters for a novel.
     *
     * Sources that support pagination can override this to allow incremental fetching
     * with progress tracking. The default implementation fetches all chapters on page 1.
     *
     * @param novelUrl URL of the novel's main page
     * @param page the page number (1-indexed)
     * @return a [ChapterPageResult] containing the chapters on this page and the total page count
     */
    open suspend fun getChapterListPage(novelUrl: String, page: Int): ChapterPageResult {
        if (page == 1) {
            val chapters = getChapterList(novelUrl)
            return ChapterPageResult(chapters, 1, 1)
        }
        return ChapterPageResult(emptyList(), 1, 1)
    }
    
    /**
     * Get the content of a chapter.
     * @param chapterUrl URL of the chapter page
     * @return Chapter content as HTML or plain text
     */
    abstract suspend fun getChapterContent(chapterUrl: String): String
    
    /**
     * Get comments for a chapter.
     * Only called if [getCapabilities] returns supportsComments = true.
     * @param chapterUrl URL of the chapter page
     * @return List of comments for this chapter
     */
    open suspend fun getChapterComments(chapterUrl: String): List<NovelComment> = emptyList()
    
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
     * Browse novels with filters.
     * @param page Page number (1-indexed)
     * @param filters Filter parameters as key-value pairs
     * @return List of novels matching filters
     */
    open suspend fun getBrowseNovels(page: Int = 1, filters: Map<String, String> = emptyMap()): List<NovelSearchResult> {
        // Default implementation falls back to popular novels
        return getPopularNovels(page)
    }
    
    /**
     * Get available filter options for browsing.
     */
    open fun getFilterList(): List<NovelFilter> = emptyList()
    
    /**
     * Declare what filtering/sorting capabilities this source supports.
     * The app uses this to dynamically show only supported options.
     * 
     * Override this to declare your source's capabilities.
     */
    open fun getCapabilities(): SourceCapabilities = SourceCapabilities()
    
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
     * Make a POST request with form data and custom headers map.
     */
    protected suspend fun postForm(url: String, data: Map<String, String>, headerMap: Map<String, String>): String {
        val formBody = okhttp3.FormBody.Builder().apply {
            data.forEach { (key, value) -> add(key, value) }
        }.build()
        
        val headersBuilder = Headers.Builder()
        headerMap.forEach { (key, value) -> headersBuilder.add(key, value) }
        // Add default user agent if not present
        if (!headerMap.containsKey("User-Agent")) {
            headersBuilder.add("User-Agent", DEFAULT_USER_AGENT)
        }
        
        val request = Request.Builder()
            .url(url)
            .headers(headersBuilder.build())
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
     *
     * Tries common formats used by novel sites: ISO, relative phrases
     * ("today", "yesterday", "X days ago"), and several regional formats.
     */
    protected fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val cleaned = dateStr.trim()

        // Relative time phrases (English only, common on aggregated novel sites)
        val lower = cleaned.lowercase()
        val now = java.util.Calendar.getInstance()
        when {
            lower == "today" || lower == "just now" -> return now.timeInMillis
            lower == "yesterday" -> {
                now.add(java.util.Calendar.DAY_OF_YEAR, -1)
                return now.timeInMillis
            }
            lower.contains("hour") || lower.contains("min") || lower.contains("sec") -> return now.timeInMillis
            lower.contains("day") -> {
                val days = lower.filter { it.isDigit() }.toIntOrNull() ?: 1
                now.add(java.util.Calendar.DAY_OF_YEAR, -days)
                return now.timeInMillis
            }
            lower.contains("week") -> {
                val weeks = lower.filter { it.isDigit() }.toIntOrNull() ?: 1
                now.add(java.util.Calendar.WEEK_OF_YEAR, -weeks)
                return now.timeInMillis
            }
            lower.contains("month") -> {
                val months = lower.filter { it.isDigit() }.toIntOrNull() ?: 1
                now.add(java.util.Calendar.MONTH, -months)
                return now.timeInMillis
            }
            lower.contains("year") -> {
                val years = lower.filter { it.isDigit() }.toIntOrNull() ?: 1
                now.add(java.util.Calendar.YEAR, -years)
                return now.timeInMillis
            }
        }

        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "MMM dd yyyy",
            "dd MMM yyyy",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "EEEE, MMM dd, yyyy h:mm:ss a",
            "EEEE, MMMM dd, yyyy h:mm:ss a",
            "MMMM dd, yyyy",
            "MMM dd, yyyy h:mm:ss a",
        )
        for (format in formats) {
            runCatching {
                java.text.SimpleDateFormat(format, java.util.Locale.US).parse(cleaned)?.time
            }.getOrNull()?.let { return it }
        }
        return null
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
 * Interface for resolving URLs that require WebView interaction (CAPTCHA, countdown timers, etc.).
 * The app provides the implementation and injects it into sources that need it.
 */
interface WebViewResolver {
    /**
     * Load [initialUrl] in a WebView and wait until a URL matching [expectedPattern] is encountered.
     * @param initialUrl The URL to load (e.g. a slow_download page with a countdown timer)
     * @param expectedPattern Regex pattern to match the final download URL
     * @return The resolved download URL
     * @throws Exception if resolution fails or times out
     */
    suspend fun resolveUrl(
        initialUrl: String,
        expectedPattern: Regex
    ): String
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
