package yokai.extension.novel.all.en

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import yokai.extension.novel.lib.*

/**
 * Source for Royal Road (royalroad.com)
 * One of the largest English web fiction platforms.
 */
class RoyalRoad : NovelSource() {
    
    override val id: Long = 6000L
    override val name: String = "Royal Road"
    override val baseUrl: String = "https://www.royalroad.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/search?title=${query.encodeUrl()}&page=$page"
        val document = getDocument(url)
        
        return document.select("div.fiction-list-item").map { element ->
            val titleElement = element.selectFirst("h2.fiction-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            val latestChapter = element.selectFirst("span.chapter-title")?.text()
            val ratingText = element.selectFirst("span.star")?.attr("title")
            val rating = ratingText?.substringBefore(" ")?.toFloatOrNull()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter,
                rating = rating
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1.font-white")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.fic-header img.thumbnail")?.attr("src"))
        val description = document.selectFirst("div.description > div.hidden-content")?.text()
        val author = document.selectFirst("span.author > a")?.text()
        
        val genres = document.select("span.tags > a.fiction-tag").map { it.text() }
        
        val statusText = document.selectFirst("span.label-sm")?.text()
        val status = parseNovelStatus(statusText)
        
        val ratingText = document.selectFirst("span.overall-score")?.text()
        val rating = ratingText?.toFloatOrNull()
        
        val statsElements = document.select("div.stats > div.col-sm-6")
        var views: Int? = null
        statsElements.forEach { stat ->
            if (stat.text().contains("Views", ignoreCase = true)) {
                views = stat.selectFirst("span")?.text()
                    ?.replace(",", "")?.replace(".", "")?.toIntOrNull()
            }
        }
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status,
            rating = rating,
            views = views
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        return document.select("table#chapters tbody tr").mapIndexed { index, element ->
            val linkElement = element.selectFirst("td:first-child > a")
            val chapterUrl = fixUrl(linkElement?.attr("href") ?: "")
            val chapterName = linkElement?.text() ?: "Chapter ${index + 1}"
            
            val dateText = element.selectFirst("td:last-child > time")?.attr("datetime")
            val dateUpload = parseDate(dateText)
            
            NovelChapter(
                name = chapterName,
                url = chapterUrl,
                dateUpload = dateUpload,
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val content = document.selectFirst("div.chapter-content")
        
        // Remove author notes if present
        content?.select("div.author-note")?.remove()
        
        return content?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return getBrowse("best-rated", page)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return getBrowse("latest-updates", page)
    }
    
    private suspend fun getBrowse(category: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/$category?page=$page"
        val document = getDocument(url)
        
        return document.select("div.fiction-list-item").map { element ->
            val titleElement = element.selectFirst("h2.fiction-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            val latestChapter = element.selectFirst("span.chapter-title")?.text()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter
            )
        }
    }
    
    override fun getFilterList(): List<NovelFilter> = listOf(
        NovelFilter.Header("Sort By"),
        NovelFilter.Select(
            name = "Order",
            values = listOf(
                "Best Rated", "Ongoing", "Completed", "Popular This Week",
                "Latest Updates", "New Releases", "Trending", "Rising Stars"
            )
        ),
        NovelFilter.Header("Genres"),
        NovelFilter.Group(
            name = "Genres",
            filters = listOf(
                NovelFilter.TriState("Action"),
                NovelFilter.TriState("Adventure"),
                NovelFilter.TriState("Comedy"),
                NovelFilter.TriState("Drama"),
                NovelFilter.TriState("Fantasy"),
                NovelFilter.TriState("Horror"),
                NovelFilter.TriState("Mystery"),
                NovelFilter.TriState("Psychological"),
                NovelFilter.TriState("Romance"),
                NovelFilter.TriState("Sci-fi"),
                NovelFilter.TriState("LitRPG"),
                NovelFilter.TriState("GameLit"),
                NovelFilter.TriState("Progression"),
                NovelFilter.TriState("Isekai")
            )
        )
    )
    
    private fun parseDate(dateString: String?): Long {
        if (dateString == null) return 0L
        return try {
            java.time.Instant.parse(dateString).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
