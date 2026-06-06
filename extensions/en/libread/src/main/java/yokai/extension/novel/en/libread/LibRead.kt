package yokai.extension.novel.en.libread

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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
     */
    private val fallbackUrls = listOf(
        "https://freewebnovel.com"
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
            "Please report us if you find any errors"
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
    
    // Override getChapterList to use chapterlist.php API
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Extract the novel ID from the image src pattern like "573s.jpg"
        val html = document.html()
        val aid = Regex("/([0-9]+)s\\.jpg").find(html)?.groupValues?.get(1)
            ?: Regex("([0-9]+)s\\.jpg").find(html)?.value?.substringBefore("s")
            ?: throw Exception("Could not find novel ID")
        
        // Fetch chapter list from API
        val chaptersResponse = postForm(
            url = "$baseUrl/api/chapterlist.php",
            data = mapOf("aid" to aid)
        )
        
        val chaptersDoc = Jsoup.parse(chaptersResponse.replace("\\\"", "\""), baseUrl)
        val prefix = novelUrl.trim().removeSuffix("/")
        
        return chaptersDoc.select("option").mapIndexedNotNull { index, element ->
            val chapterSlug = element.attr("value").split('/').lastOrNull() ?: return@mapIndexedNotNull null
            if (chapterSlug.isBlank()) return@mapIndexedNotNull null
            
            val chapterUrl = "$prefix/$chapterSlug"
            // Clean up chapter name - remove any escaped HTML artifacts
            val rawName = element.text().ifBlank { "Chapter ${index + 1}" }
            val name = rawName
                .replace(Regex("<\\\\?/?option[^>]*>"), "")  // Remove escaped option tags
                .replace(Regex("\\}\"?\\s*$"), "")           // Remove trailing }
                .replace(Regex("^\"?\\s*"), "")              // Remove leading quotes
                .trim()
                .ifBlank { "Chapter ${index + 1}" }
            
            NovelChapter(
                url = chapterUrl,
                name = name,
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    // Override getChapterContent with fallback support
    override suspend fun getChapterContent(chapterUrl: String): String {
        return withFallback(chapterUrl) { url ->
            val document = getDocument(url)
            
            val content = document.selectFirst("div.txt")?.let { element ->
                element.select("script, div.ads, ins, div.chapter-warning, p.report-tips").remove()
                element.html()
            } ?: throw Exception("Could not find chapter content")
            
            // Clean up watermarks from both sites
            content
                .replace("\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62", "", ignoreCase = true)
                .replace("libread.com", "", ignoreCase = true)
                .replace("freewebnovel.com", "", ignoreCase = true)
                .replace("☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜", "")
        }
    }
    
    /**
     * Execute an operation with fallback support.
     * If the primary URL fails, try the same path on fallback mirrors.
     */
    private suspend fun <T> withFallback(url: String, operation: suspend (String) -> T): T {
        // Try primary URL first
        try {
            return operation(url)
        } catch (primaryError: Exception) {
            android.util.Log.w("LibRead", "Primary URL failed: $url - ${primaryError.message}")
            
            // Try fallback mirrors
            val path = url.removePrefix(baseUrl)
            for (fallbackBase in fallbackUrls) {
                try {
                    val fallbackUrl = "$fallbackBase$path"
                    android.util.Log.d("LibRead", "Trying fallback: $fallbackUrl")
                    return operation(fallbackUrl)
                } catch (fallbackError: Exception) {
                    android.util.Log.w("LibRead", "Fallback failed: $fallbackBase - ${fallbackError.message}")
                }
            }
            
            // All failed, throw original error
            throw primaryError
        }
    }
}
