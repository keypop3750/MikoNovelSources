package yokai.extension.novel.en.boxnovel

import okhttp3.Headers
import org.jsoup.nodes.Document
import yokai.extension.novel.lib.NovelChapter
import yokai.extension.novel.lib.NovelComment
import yokai.extension.novel.lib.NovelDetails
import yokai.extension.novel.lib.NovelSearchResult
import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelStatus
import yokai.extension.novel.lib.SourceCapabilities

/**
 * BoxNovel — originally at boxnovel.com (now behind ParkLogic redirect),
 * then novlove.com (now a "we moved" page), now at novelnice.com.
 *
 * NovelNice is a standard WordPress wp-manga / Madara-theme site.
 *
 * HTML pages:
 *  - GET /read/?m_orderby=views          — popular (paginated via /read/page/N/)
 *  - GET /read/?m_orderby=new-manga      — latest
 *  - GET /read/{slug}/                   — novel details
 *  - GET /read/{slug}/chapter-{N}/       — chapter content
 *  - POST /read/{slug}/ajax/chapters/?t={page} — chapter list (200/page, newest-first)
 *     Requires Referer + X-Requested-With headers.
 * Search:
 *  - GET /?s={query}&post_type=wp-manga  — may be Cloudflare-gated
 */
class BoxNovel : NovelSource() {

    override val id: Long = 6011L
    override val name: String = "BoxNovel"
    override val baseUrl: String = "https://novelnice.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "latest"),
        supportsSortDirection = false,
        supportsSearch = true,
        supportsComments = false,
    )

    // ===== Search =====

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=wp-manga"
        return try {
            val doc = getDocument(url)
            parseSearchResults(doc)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResults(doc: Document): List<NovelSearchResult> {
        // Search results use .c-tabs-item__content (Madara theme)
        return doc.select(".c-tabs-item__content").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.absUrl("href").ifEmpty { link.attr("href") }
            if (!href.contains("/read/")) return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val cover = item.selectFirst("img")?.let { img ->
                img.absUrl("data-src").ifEmpty { img.absUrl("src") }.ifEmpty { img.attr("data-src") }
            }?.takeIf { it.isNotBlank() }
            NovelSearchResult(title = title, url = href, coverUrl = cover)
        }
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/read/?m_orderby=views"
        } else {
            "$baseUrl/read/page/$page/?m_orderby=views"
        }
        return fetchNovelListFromHtml(url)
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/read/?m_orderby=new-manga"
        } else {
            "$baseUrl/read/page/$page/?m_orderby=new-manga"
        }
        return fetchNovelListFromHtml(url)
    }

    private suspend fun fetchNovelListFromHtml(url: String): List<NovelSearchResult> {
        val doc = getDocument(url)
        return doc.select(".page-item-detail").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.absUrl("href").ifEmpty { link.attr("href") }
            if (!href.contains("/read/")) return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val cover = item.selectFirst("img")?.let { img ->
                img.absUrl("data-src").ifEmpty { img.absUrl("src") }.ifEmpty { img.attr("data-src") }
            }?.takeIf { it.isNotBlank() }
            NovelSearchResult(title = title, url = href, coverUrl = cover)
        }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore(" - ")?.trim() ?: ""

        val coverUrl = doc.selectFirst("div.summary_image img")?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }

        val description = doc.selectFirst(".summary__content")?.text()?.trim()
            ?: doc.selectFirst("div[class*=description]")?.text()?.trim()

        val author = doc.selectFirst(".author-content a")?.text()?.trim()

        val genres = doc.select(".genres-content a").map { it.text().trim() }
            .filter { it.isNotBlank() }

        // Status: find the .post-content_item that contains "Status" in its heading
        val statusText = doc.select(".post-content_item").firstOrNull { el ->
            el.selectFirst(".summary-heading h5")?.text()?.contains("Status", ignoreCase = true) == true
        }?.selectFirst(".summary-content")?.text()?.trim()
        val status = parseNovelStatus(statusText)

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres.distinct(),
            status = status,
        )
    }

    // ===== Chapter List =====

    /**
     * Chapter list via AJAX: POST {novel_url}ajax/chapters/?t={page}
     * Returns HTML with li.wp-manga-chapter > a[href], 200 per page, newest-first.
     * Requires Referer and X-Requested-With headers.
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        while (true) {
            val ajaxUrl = "${novelUrl.trimEnd('/')}/ajax/chapters/?t=$page"
            val html = try {
                postForm(
                    url = ajaxUrl,
                    data = emptyMap(),
                    headerMap = mapOf(
                        "Referer" to novelUrl,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                )
            } catch (_: Exception) {
                break
            }
            if (html.isBlank()) break

            val doc = org.jsoup.Jsoup.parse(html)
            val chapterEls = doc.select("li.wp-manga-chapter > a[href]")
            if (chapterEls.isEmpty()) break

            chapterEls.forEach { a ->
                val chapterUrl = a.absUrl("href").ifEmpty { a.attr("href") }
                val name = a.text().trim()
                // Extract chapter number from URL: /chapter-{N}/
                val number = Regex("""chapter-(\d+)""").find(chapterUrl)
                    ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                chapters.add(
                    NovelChapter(
                        name = name,
                        url = chapterUrl,
                        chapterNumber = number,
                    ),
                )
            }

            // Check for more pages
            val hasMore = doc.select(".pagination .page a").any { link ->
                val href = link.attr("href")
                Regex("""[?&]t=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()?.let { it > page } == true
            }
            if (!hasMore) break
            page++
        }
        // AJAX returns newest-first; reverse to oldest-first (chapter 1 first)
        chapters.reverse()
        return chapters
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        // Normal text content: div.text-left inside .reading-content
        val contentEl = doc.selectFirst(".reading-content .text-left")
            ?: doc.selectFirst(".reading-content div.text-left")
            ?: doc.selectFirst("div.text-left")
            ?: doc.selectFirst("div.entry-content")
            ?: doc.selectFirst("div.chapter-content")
        contentEl?.select("script, style, .ads, ins.adsbygoogle, iframe, .ad-container")?.remove()
        return contentEl?.html() ?: ""
    }

    // ===== Chapter Comments =====

    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        return emptyList()
    }
}
