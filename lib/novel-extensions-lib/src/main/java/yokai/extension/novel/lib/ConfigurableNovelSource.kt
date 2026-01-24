package yokai.extension.novel.lib

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Configuration for CSS selectors used to parse a novel website.
 * This allows creating sources with minimal code by just configuring selectors.
 */
data class SourceSelectors(
    // === Search/Browse Selectors ===
    /** Selector for novel items in search results */
    val searchItemSelector: String,
    /** Selector for novel title within an item (returns element, gets text and href) */
    val searchTitleSelector: String,
    /** Selector for cover image within an item (returns img element, gets src or data-src) */
    val searchCoverSelector: String? = null,
    /** Attribute to get cover URL from (default "src", can be "data-src") */
    val coverAttribute: String = "src",
    
    // === Browse/Main Page Selectors (if different from search) ===
    /** Selector for novel items on browse pages (defaults to searchItemSelector) */
    val browseItemSelector: String? = null,
    /** Selector for title on browse pages (defaults to searchTitleSelector) */
    val browseTitleSelector: String? = null,
    /** Selector for cover on browse pages (defaults to searchCoverSelector) */
    val browseCoverSelector: String? = null,
    /** Attribute to get browse cover URL from (defaults to coverAttribute) */
    val browseCoverAttribute: String? = null,
    
    // === Novel Details Selectors ===
    /** Selector for title on novel page */
    val detailTitleSelector: String,
    /** Selector for cover on novel page */
    val detailCoverSelector: String,
    /** Attribute to get detail cover URL from (defaults to coverAttribute) */
    val detailCoverAttribute: String? = null,
    /** Selector for description/synopsis */
    val descriptionSelector: String? = null,
    /** Selector for author */
    val authorSelector: String? = null,
    /** Selector for genre/tag links */
    val genreSelector: String? = null,
    /** Selector for status text */
    val statusSelector: String? = null,
    /** Selector for rating value */
    val ratingSelector: String? = null,
    
    // === Chapter List Selectors ===
    /** Selector for chapter links */
    val chapterListSelector: String,
    /** Alternative selector if primary fails */
    val chapterListSelectorAlt: String? = null,
    /** Selector for novel ID for AJAX chapter fetching */
    val novelIdSelector: String? = null,
    /** Attribute containing novel ID */
    val novelIdAttribute: String = "data-novel-id",
    /** AJAX URL pattern for chapter list (use {id} as placeholder) */
    val chapterAjaxUrl: String? = null,
    
    // === Chapter Content Selectors ===
    /** Selector for chapter content container */
    val chapterContentSelector: String,
    /** Alternative content selector */
    val chapterContentSelectorAlt: String? = null,
    /** Elements to remove from chapter content */
    val contentRemoveSelectors: List<String> = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
    /** Text patterns to remove from chapter content */
    val contentRemovePatterns: List<String> = emptyList(),
    
    // === URL Patterns ===
    /** URL pattern for search (use {query} and {page} as placeholders) */
    val searchUrlPattern: String,
    /** URL pattern for popular novels (use {page} as placeholder) */
    val popularUrlPattern: String? = null,
    /** URL pattern for latest updates (use {page} as placeholder) */
    val latestUrlPattern: String? = null,
    
    // === Cover URL Transformation ===
    /** Regex pattern to transform thumbnail URLs to full cover URLs */
    val coverTransformRegex: Regex? = null,
    /** Replacement for cover URL transformation */
    val coverTransformReplacement: String? = null,
    
    // === Flags ===
    /** Whether to fetch full cover from detail page during search */
    val fetchFullCoverFromDetails: Boolean = false
)

/**
 * A configurable novel source that uses CSS selectors to parse websites.
 * Extend this class and provide selectors configuration for quick source creation.
 */
abstract class ConfigurableNovelSource : NovelSource() {
    
    /**
     * Selectors configuration for this source.
     */
    abstract val selectors: SourceSelectors
    
    /**
     * Cache for full cover URLs to avoid repeated detail page fetches.
     * Shared across all instances of this class.
     */
    companion object {
        private val coverCache = mutableMapOf<String, String?>()
    }
    
    // ===== Search Implementation =====
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> = coroutineScope {
        val url = selectors.searchUrlPattern
            .replace("{query}", query.encodeUrl())
            .replace("{page}", page.toString())
        
        val document = getDocument(url)
        val items = parseNovelItems(document, forSearch = true)
        
        if (selectors.fetchFullCoverFromDetails) {
            // Fetch full covers from detail pages in parallel
            items.map { item ->
                async {
                    val coverUrl = getFullCoverUrl(item.url)
                    item.copy(coverUrl = coverUrl)
                }
            }.awaitAll()
        } else {
            items
        }
    }
    
    // ===== Browse Implementation =====
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> = coroutineScope {
        val urlPattern = selectors.popularUrlPattern ?: return@coroutineScope emptyList()
        val url = urlPattern.replace("{page}", page.toString())
        
        val document = getDocument(url)
        val items = parseNovelItems(document, forSearch = false)
        
        if (selectors.fetchFullCoverFromDetails) {
            items.map { item ->
                async {
                    val coverUrl = getFullCoverUrl(item.url)
                    item.copy(coverUrl = coverUrl)
                }
            }.awaitAll()
        } else {
            items
        }
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> = coroutineScope {
        val urlPattern = selectors.latestUrlPattern ?: return@coroutineScope emptyList()
        val url = urlPattern.replace("{page}", page.toString())
        
        val document = getDocument(url)
        val items = parseNovelItems(document, forSearch = false)
        
        if (selectors.fetchFullCoverFromDetails) {
            items.map { item ->
                async {
                    val coverUrl = getFullCoverUrl(item.url)
                    item.copy(coverUrl = coverUrl)
                }
            }.awaitAll()
        } else {
            items
        }
    }
    
    // ===== Novel Details Implementation =====
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst(selectors.detailTitleSelector)?.text() ?: ""
        
        val detailAttr = selectors.detailCoverAttribute ?: selectors.coverAttribute
        var coverUrl = getCoverFromElement(document.selectFirst(selectors.detailCoverSelector), detailAttr)
        coverUrl = transformCoverUrl(coverUrl)
        
        // Cache the cover URL
        coverCache[url] = coverUrl
        
        val description = selectors.descriptionSelector?.let { 
            document.selectFirst(it)?.text() 
        }
        val author = selectors.authorSelector?.let { 
            document.selectFirst(it)?.text() 
        }
        val genres = selectors.genreSelector?.let { 
            document.select(it).map { el -> el.text() } 
        } ?: emptyList()
        val statusText = selectors.statusSelector?.let { 
            document.selectFirst(it)?.text() 
        }
        val rating = selectors.ratingSelector?.let {
            document.selectFirst(it)?.text()?.toFloatOrNull()
        }
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = parseNovelStatus(statusText),
            rating = rating
        )
    }
    
    // ===== Chapter List Implementation =====
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        // Try AJAX endpoint first if configured
        val novelIdSel = selectors.novelIdSelector
        val ajaxUrlPattern = selectors.chapterAjaxUrl
        if (novelIdSel != null && ajaxUrlPattern != null) {
            try {
                val document = getDocument(novelUrl)
                val novelId = document.selectFirst(novelIdSel)
                    ?.attr(selectors.novelIdAttribute)
                
                if (!novelId.isNullOrBlank()) {
                    val ajaxUrl = ajaxUrlPattern.replace("{id}", novelId)
                    val ajaxDoc = getDocument(ajaxUrl)
                    val chapters = parseChaptersFromDocument(ajaxDoc)
                    if (chapters.isNotEmpty()) {
                        return chapters.mapIndexed { index, chapter ->
                            chapter.copy(chapterNumber = (index + 1).toFloat())
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ConfigurableSource", "AJAX chapter fetch failed: ${e.message}")
            }
        }
        
        // Fallback to HTML parsing
        val document = getDocument(novelUrl)
        val chapters = parseChaptersFromDocument(document)
        
        return chapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
    
    // ===== Chapter Content Implementation =====
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val content = document.selectFirst(selectors.chapterContentSelector)
            ?: selectors.chapterContentSelectorAlt?.let { document.selectFirst(it) }
        
        // Remove unwanted elements
        selectors.contentRemoveSelectors.forEach { selector ->
            content?.select(selector)?.remove()
        }
        
        var html = content?.html() ?: ""
        
        // Remove unwanted text patterns
        selectors.contentRemovePatterns.forEach { pattern ->
            html = html.replace(pattern, "", ignoreCase = true)
        }
        
        return html
    }
    
    // ===== Helper Methods =====
    
    /**
     * Parse novel items from a document.
     */
    protected open fun parseNovelItems(document: Document, forSearch: Boolean): List<NovelSearchResult> {
        val itemSelector = if (forSearch) {
            selectors.searchItemSelector
        } else {
            selectors.browseItemSelector ?: selectors.searchItemSelector
        }
        
        val titleSelector = if (forSearch) {
            selectors.searchTitleSelector
        } else {
            selectors.browseTitleSelector ?: selectors.searchTitleSelector
        }
        
        val coverSelector = if (forSearch) {
            selectors.searchCoverSelector
        } else {
            selectors.browseCoverSelector ?: selectors.searchCoverSelector
        }
        
        val coverAttr = if (forSearch) {
            selectors.coverAttribute
        } else {
            selectors.browseCoverAttribute ?: selectors.coverAttribute
        }
        
        return document.select(itemSelector).mapNotNull { element ->
            val titleElement = element.selectFirst(titleSelector) ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            
            var coverUrl = coverSelector?.let { 
                getCoverFromElement(element.selectFirst(it), coverAttr)
            }
            coverUrl = transformCoverUrl(coverUrl)
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    /**
     * Parse chapters from a document.
     */
    protected open fun parseChaptersFromDocument(document: Document): List<NovelChapter> {
        var chapterElements = document.select(selectors.chapterListSelector)
        
        if (chapterElements.isEmpty() && selectors.chapterListSelectorAlt != null) {
            chapterElements = document.select(selectors.chapterListSelectorAlt!!)
        }
        
        return chapterElements.mapNotNull { element ->
            // Handle both direct links and option elements
            val url = element.attr("href").ifBlank { element.attr("value") }
            if (url.isBlank()) return@mapNotNull null
            
            NovelChapter(
                url = fixUrl(url),
                name = element.text(),
                chapterNumber = 0f  // Will be assigned later
            )
        }
    }
    
    /**
     * Get cover URL from an element, trying various attributes.
     */
    protected fun getCoverFromElement(element: Element?, preferredAttr: String = selectors.coverAttribute): String? {
        if (element == null) return null
        
        // Try the specified attribute first
        var url = element.attr(preferredAttr)
        
        // Fallback to common attributes
        if (url.isBlank()) url = element.attr("data-src")
        if (url.isBlank()) url = element.attr("src")
        if (url.isBlank()) url = element.attr("data-lazy-src")
        
        return fixUrlOrNull(url.ifBlank { null })
    }
    
    /**
     * Transform a thumbnail URL to a full cover URL if configured.
     */
    protected fun transformCoverUrl(url: String?): String? {
        if (url == null) return null
        if (selectors.coverTransformRegex == null || selectors.coverTransformReplacement == null) {
            return url
        }
        return url.replace(selectors.coverTransformRegex!!, selectors.coverTransformReplacement!!)
    }
    
    /**
     * Fetch full cover URL from a novel's detail page, with caching.
     */
    protected suspend fun getFullCoverUrl(novelUrl: String): String? {
        // Check cache first
        if (coverCache.containsKey(novelUrl)) {
            return coverCache[novelUrl]
        }
        
        // Fetch from detail page
        val coverUrl = try {
            val detailDoc = getDocument(novelUrl)
            val detailAttr = selectors.detailCoverAttribute ?: selectors.coverAttribute
            var url = getCoverFromElement(detailDoc.selectFirst(selectors.detailCoverSelector), detailAttr)
            transformCoverUrl(url)
        } catch (e: Exception) {
            android.util.Log.e("ConfigurableSource", "Error fetching cover for $novelUrl: ${e.message}")
            null
        }
        
        // Cache the result
        coverCache[novelUrl] = coverUrl
        return coverUrl
    }
}
