package yokai.extension.novel.en.novelsonline

import org.jsoup.nodes.Document
import yokai.extension.novel.lib.*

/**
 * Source for NovelsOnline (novelsonline.org)
 * Note: Uses Cloudflare protection - may require special handling.
 *
 * Supported filters:
 * - Sort: Popular (top), Latest
 */
class NovelsOnline : ConfigurableNovelSource() {

    override val id: Long = 6013L
    override val name: String = "NovelsOnline"
    override val baseUrl: String = "https://novelsonline.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L

    /**
     * Declare this source's filtering capabilities.
     */
    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "last_updated"),
        supportsSortDirection = false,
        supportedGenres = emptyList(),  // Has genres but complex structure
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
     * NovelsOnline supports top novels and latest release.
     */
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = if (page <= 1) "$baseUrl/top-novel" else "$baseUrl/top-novel/$page"
        return parseNovelList(getDocument(url))
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = if (page <= 1) "$baseUrl/latest-updates" else "$baseUrl/latest-updates?page=$page"
        val document = getDocument(url)
        val seenUrls = mutableSetOf<String>()
        return document.select("div.list-by-word-body ul li a").mapNotNull { link ->
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null
            val fullUrl = fixUrl(href)
            // Extract novel URL from chapter URL: /novel-slug/chapter-N -> /novel-slug
            val novelUrl = fullUrl.substringBeforeLast("/chapter-")
                .substringBeforeLast("/volume-")
            if (novelUrl.isBlank() || novelUrl == fullUrl) return@mapNotNull null
            if (!seenUrls.add(novelUrl)) return@mapNotNull null // deduplicate
            val text = link.text()
            val title = text.substringBefore(" Ch. ")
                .substringBefore(" Vol. ")
                .trim()
                .ifBlank { return@mapNotNull null }
            // Try to infer cover URL from novel slug
            val slug = novelUrl.substringAfterLast("/")
            val coverUrl = "$baseUrl/uploads/posters/$slug.jpg"
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }

    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val sort = filters["sort"] ?: "popular"
        return when (sort) {
            "last_updated" -> getLatestUpdates(page)
            else -> getPopularNovels(page)
        }
    }

    /**
     * Parse novel list from browse pages.
     */
    private suspend fun parseNovelList(document: org.jsoup.nodes.Document): List<NovelSearchResult> {
        return document.select("div.top-novel-block").mapNotNull { block ->
            val titleElement = block.selectFirst("div.top-novel-header h2 a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val url = titleElement.attr("href")

            if (title.isBlank() || url.isBlank()) return@mapNotNull null

            val coverUrl = block.selectFirst("div.top-novel-cover img")
                ?.attr("src")
                ?.let { fixUrl(it) }

            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }

    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "div.top-novel-block",
        searchTitleSelector = "div.top-novel-header h2 a",
        searchCoverSelector = "div.top-novel-cover img",
        coverAttribute = "src",

        // Browse selectors
        browseItemSelector = "div.top-novel-block",
        browseTitleSelector = "div.top-novel-header h2 a",
        browseCoverSelector = "div.top-novel-cover img",

        // Novel details selectors (base selectors only; custom parsing in getNovelDetails)
        detailTitleSelector = "div.block-title h1",
        detailCoverSelector = "div.novel-cover img",
        descriptionSelector = null,  // Parsed manually in getNovelDetails
        authorSelector = null,     // Parsed manually in getNovelDetails
        genreSelector = null,        // Parsed manually in getNovelDetails
        statusSelector = null,       // Parsed manually in getNovelDetails

        // Chapter list selectors
        // Actual structure: div.panel.panel-default > div.panel-body > ul > li > a
        chapterListSelector = "div.panel.panel-default ul li a",

        // Chapter content selectors
        chapterContentSelector = "#contentall",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.novel-detail-item"),
        contentRemovePatterns = listOf(
            "novelsonline.org",
            "Your browser does not support JavaScript"
        ),

        // URL patterns
        searchUrlPattern = "https://novelsonline.org/search-ajax?q={query}",
        popularUrlPattern = "https://novelsonline.org/top-novel/{page}",
        latestUrlPattern = "https://novelsonline.org/latest-updates",

        fetchFullCoverFromDetails = false
    )

    /**
     * Override getNovelDetails because the site's detail page uses h6 labels
     * inside nested divs that JSoup's :has() pseudo-class cannot handle.
     */
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)

        val title = document.selectFirst("div.block-title h1")?.text()?.trim() ?: ""
        val coverUrl = document.selectFirst("div.novel-cover img")?.attr("src")?.let { fixUrl(it) }

        // Parse metadata fields by iterating over .novel-detail-item blocks
        var description: String? = null
        var author: String? = null
        val genres = mutableListOf<String>()
        var statusText: String? = null

        document.select("div.novel-detail-item").forEach { item ->
            val label = item.selectFirst("div.novel-detail-header")?.text()?.trim()?.lowercase() ?: return@forEach
            when {
                label.contains("description") -> {
                    description = item.selectFirst("div.novel-detail-body")?.text()?.trim()
                }
                label.contains("author") -> {
                    author = item.selectFirst("div.novel-detail-body a")?.text()?.trim()
                        ?: item.selectFirst("div.novel-detail-body")?.text()?.trim()
                }
                label.contains("genre") -> {
                    genres.addAll(item.select("div.novel-detail-body a").map { it.text().trim() })
                }
                label.contains("status") -> {
                    statusText = item.selectFirst("div.novel-detail-body")?.text()?.trim()
                }
            }
        }

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = parseNovelStatus(statusText),
            tags = emptyList(),
            rating = null,
            ratingCount = null,
            views = null,
            alternativeTitles = emptyList()
        )
    }

    /**
     * Override getChapterList because NovelsOnline uses accordion panels
     * (div.panel.panel-default) per volume, with chapter links inside.
     * We must filter out volume toggle links (#collapse-N).
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        val chapters = mutableListOf<NovelChapter>()

        document.select("div.panel.panel-default").forEach panelLoop@{ panel ->
            // Extract volume name from panel heading for chapter prefix
            val volumeName = panel.selectFirst("div.panel-heading")?.text()?.trim() ?: ""

            panel.select("ul li a").forEach linkLoop@{ link ->
                val url = link.attr("href")
                val text = link.text().trim()

                // Skip volume toggle links (href starts with #collapse- or text is just "Volume N")
                if (url.startsWith("#collapse-")) return@linkLoop
                if (url.isBlank()) return@linkLoop
                if (text.isBlank()) return@linkLoop
                if (text.equals(volumeName, ignoreCase = true)) return@linkLoop

                // Build chapter name: prefix with volume if short (like "CH 1")
                val name = if (volumeName.isNotBlank() && !text.contains(volumeName, ignoreCase = true)) {
                    "$volumeName - $text"
                } else {
                    text
                }

                chapters.add(
                    NovelChapter(
                        url = fixUrl(url),
                        name = name,
                        chapterNumber = 0f
                    )
                )
            }
        }

        // Assign sequential chapter numbers
        return chapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }

    /**
     * Override getChapterContent to also try the chapter-content3 selector
     * as a fallback when #contentall is not found.
     */
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("#contentall")
            ?: document.selectFirst("div.chapter-content3")
            ?: return ""

        selectors.contentRemoveSelectors.forEach { selector ->
            content.select(selector).remove()
        }

        var html = content.html()
        selectors.contentRemovePatterns.forEach { pattern ->
            html = html.replace(pattern, "", ignoreCase = true)
        }
        return html
    }

    // Override search for POST-based search
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()  // Search doesn't support pagination

        val response = postForm(
            "$baseUrl/search-ajax",
            mapOf("q" to query)
        )
        val document = parseHtml(response)

        return document.select("li").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val title = link.text()
            val url = link.attr("href")

            if (title.isNotBlank() && url.isNotBlank()) {
                NovelSearchResult(
                    title = title,
                    url = fixUrl(url),
                    coverUrl = null
                )
            } else null
        }
    }
}
