package yokai.extension.novel.en.novelroll

import okhttp3.Headers
import org.jsoup.nodes.Document
import yokai.extension.novel.lib.NovelChapter
import yokai.extension.novel.lib.NovelDetails
import yokai.extension.novel.lib.NovelSearchResult
import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelStatus
import yokai.extension.novel.lib.SourceCapabilities

class NovelRoll : NovelSource() {

    override val id: Long = 6025L
    override val name: String = "NovelRoll"
    override val baseUrl: String = "https://novelroll.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 2500L

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportedSorts = listOf("popular", "latest"),
        supportsSortDirection = false,
        supportsSearch = true,
        supportsComments = false
    )

    // ===== Search =====

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?title=${query.encodeUrl()}&page=$page"
        val doc = getDocument(url)
        return doc.select("div.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val coverUrl = el.selectFirst("img")?.absUrl("src")
            NovelSearchResult(title = title, url = novelUrl, coverUrl = coverUrl)
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/genre-all/sort-popular/status-all/all-novel?page=$page"
        return getBrowsePage(url)
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/genre-all/sort-new/status-all/all-novel?page=$page"
        return getBrowsePage(url)
    }

    private suspend fun getBrowsePage(url: String): List<NovelSearchResult> {
        val doc = getDocument(url)
        return doc.select("div.novel-item").map { el ->
            val titleLink = el.selectFirst("a")
            val title = titleLink?.attr("title") ?: titleLink?.text() ?: ""
            val novelUrl = titleLink?.absUrl("href") ?: ""
            val coverUrl = el.selectFirst("img")?.absUrl("src")
            NovelSearchResult(title = title, url = novelUrl, coverUrl = coverUrl)
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""

        val coverUrl = doc.selectFirst("div.fixed-img img")?.absUrl("src")
            ?: doc.selectFirst("img[alt]")?.absUrl("src")

        val description = doc.selectFirst("div.content")?.text()?.trim()
            ?: doc.selectFirst("div.description")?.text()?.trim()

        val author = doc.selectFirst("li:contains(Author) a")?.text()?.trim()
            ?: doc.selectFirst("div.author a")?.text()?.trim()

        val genres = doc.select("div.tags a, div.genres a").map { it.text().trim() }
            .filter { it.isNotBlank() }

        val statusText = doc.selectFirst("small:contains(Status)")?.parent()?.text()
            ?: doc.selectFirst("li:contains(Status)")?.text()
        val status = parseNovelStatus(statusText)

        val ratingText = doc.selectFirst("div.rating")?.text()?.substringBefore("/")?.trim()
        val rating = ratingText?.toFloatOrNull()

        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres.distinct(),
            status = status,
            rating = rating
        )
    }

    // ===== Chapter List =====

    /**
     * NovelRoll has a single table of contents page at /book/<slug>?toc
     * that contains all chapters.
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val tocUrl = "$novelUrl?toc"
        val doc = getDocument(tocUrl)
        return doc.select("a[href*=chapter]").map { a ->
            val href = a.absUrl("href")
            val name = a.text().trim()
            val dateStr = a.selectFirst(".chapter-date")?.text()
            NovelChapter(
                name = name,
                url = href,
                dateUpload = parseDate(dateStr) ?: 0L
            )
        }.filter { it.name.isNotBlank() && it.url.isNotBlank() }.reversed()
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        val contentEl = doc.selectFirst("div#content")
            ?: doc.selectFirst("div.chapter-content")
            ?: doc.selectFirst("article")
        contentEl?.select("script, .ads, ins.adsbygoogle, iframe, .ad-container")?.remove()
        return contentEl?.html() ?: ""
    }
}
