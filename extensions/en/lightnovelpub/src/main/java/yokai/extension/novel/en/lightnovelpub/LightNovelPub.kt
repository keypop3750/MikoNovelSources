package yokai.extension.novel.en.lightnovelpub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import yokai.extension.novel.lib.NovelChapter
import yokai.extension.novel.lib.NovelComment
import yokai.extension.novel.lib.NovelDetails
import yokai.extension.novel.lib.NovelSearchResult
import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelStatus
import yokai.extension.novel.lib.SourceCapabilities

/**
 * LightNovelPub / LightNovelWorld
 *
 * The original lightnovelpub.com was shut down in April 2025 by Kakao Entertainment's
 * anti-piracy action. The site relaunched at lightnovelpub.org (which also runs the
 * "Chikari" platform). It provides a clean JSON API.
 *
 * API endpoints (all require trailing slashes):
 *  - GET /api/novels/?page=N&sort=popular  — browse popular (paginated, 20 per page)
 *  - GET /api/novels/?page=N&sort=latest   — browse latest
 *  - GET /api/search/?q=QUERY              — search by title
 *  - GET /api/novel/{slug}/chapters/?page=N — chapter list (paginated, 150 per page)
 *  - GET /novel/{slug}/                    — novel details page (HTML)
 *  - GET /novel/{slug}/chapter/{number}/   — chapter content (HTML)
 */
class LightNovelPub : NovelSource() {

    override val id: Long = 6023L
    override val name: String = "LightNovelPub"
    override val baseUrl: String = "https://lightnovelpub.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 2000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        // The search API returns all results at once (no pagination param)
        if (page > 1) return emptyList()
        val url = "$baseUrl/api/search/?q=${query.encodeUrl()}"
        val response = get(url)
        val body = response.body?.string() ?: return emptyList()
        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val novels = jsonObj["novels"]?.jsonArray ?: return emptyList()
            novels.map { el ->
                val obj = el.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverPath = obj["cover_path"]?.jsonPrimitive?.contentOrNull
                NovelSearchResult(
                    title = title,
                    url = "$baseUrl/novel/$slug/",
                    coverUrl = coverPath?.let { "$baseUrl$it" },
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ===== Popular / Latest =====

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return getBrowsePage(page, "popular")
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return getBrowsePage(page, "latest")
    }

    private suspend fun getBrowsePage(page: Int, sort: String): List<NovelSearchResult> {
        val url = "$baseUrl/api/novels/?page=$page&sort=$sort"
        val response = get(url)
        val body = response.body?.string() ?: return emptyList()
        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val novels = jsonObj["novels"]?.jsonArray ?: return emptyList()
            novels.map { el ->
                val obj = el.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
                val coverPath = obj["cover_path"]?.jsonPrimitive?.contentOrNull
                NovelSearchResult(
                    title = title,
                    url = "$baseUrl/novel/$slug/",
                    coverUrl = coverPath?.let { "$baseUrl$it" },
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ===== Novel Details =====

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val doc = getDocument(url)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore(" | ")?.trim() ?: ""

        // Cover: look for og:image meta or the main cover img
        val coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img[alt]")?.absUrl("src")

        // Description: from meta description or the page content
        val description = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst("div[class*=description]")?.text()?.trim()
            ?: doc.selectFirst("div[class*=summary]")?.text()?.trim()

        // Author: look in the page
        val author = doc.selectFirst("[itemprop=author]")?.text()?.trim()
            ?: doc.selectFirst("a[href*=author]")?.text()?.trim()
            ?: run {
                // Try to extract from meta description "by AuthorName"
                val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
                val byMatch = Regex("""by\s+([^.]+?)(?:\.|Genres:)""").find(metaDesc)
                byMatch?.groupValues?.get(1)?.trim()
            }

        // Genres: from the page
        val genres = doc.select("a[href*=/genre/], a[href*=genre]").map { it.text().trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                // Try extracting from meta description "Genres: X, Y, Z"
                val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
                val genresMatch = Regex("""Genres:\s*(.+)""").find(metaDesc)
                genresMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
            }

        // Status: look in the page
        val statusText = doc.selectFirst("div[class*=status]")?.text()?.trim()
            ?: doc.selectFirst("span[class*=status]")?.text()?.trim()
            ?: run {
                val metaDesc = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
                if (metaDesc.contains("Ongoing", ignoreCase = true)) "Ongoing"
                else if (metaDesc.contains("Completed", ignoreCase = true)) "Completed"
                else null
            }
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
     * Chapter list via JSON API: GET /api/novel/{slug}/chapters?page=N
     * Returns: { chapters: [{ number, title, display_name, ... }], has_more: bool }
     * Pages are oldest-first (page 1 = chapter 1+), 150 per page.
     */
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val slug = novelUrl.trimEnd('/').substringAfterLast("/")
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        while (true) {
            val apiUrl = "$baseUrl/api/novel/$slug/chapters/?page=$page"
            val response = get(apiUrl)
            val body = response.body?.string() ?: break
            val jsonObj = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) { break }

            val chapterArray = jsonObj["chapters"]?.jsonArray ?: break
            if (chapterArray.isEmpty()) break

            chapterArray.forEach { el ->
                val obj = el.jsonObject
                val number = obj["number"]?.jsonPrimitive?.intOrNull ?: 0
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["display_name"]?.jsonPrimitive?.contentOrNull
                    ?: "Chapter $number"
                val chapterUrl = "$baseUrl/novel/$slug/chapter/$number/"
                chapters.add(
                    NovelChapter(
                        name = title,
                        url = chapterUrl,
                        chapterNumber = number.toFloat(),
                    )
                )
            }

            val hasMore = jsonObj["has_more"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
            if (!hasMore) break
            page++
        }
        // API returns oldest-first already, so no reversal needed
        return chapters
    }

    // ===== Chapter Content =====

    override suspend fun getChapterContent(chapterUrl: String): String {
        val doc = getDocument(chapterUrl)
        val contentEl = doc.selectFirst("div.chapter-content")
            ?: doc.selectFirst("div.chapter-text")
            ?: doc.selectFirst("div#content")
            ?: doc.selectFirst("div.text")
        contentEl?.select("script, style, .ads, ins.adsbygoogle, iframe, .ad-container, .chapter-ad-container")?.remove()
        return contentEl?.html() ?: ""
    }

    // ===== Chapter Comments =====
    // The new site (lightnovelworld.org) does not currently support chapter comments via API.
    // Comment support can be re-enabled if the API adds it in the future.

    override suspend fun getChapterComments(chapterUrl: String): List<NovelComment> {
        return emptyList()
    }
}
