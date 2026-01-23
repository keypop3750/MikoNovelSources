package yokai.extension.novel.en.royalroad

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
        
        // Royal Road loads chapters via AJAX, but they're also in a script tag
        val scriptContent = document.select("script").find { 
            it.data().contains("window.chapters") 
        }?.data()
        
        if (scriptContent != null) {
            // Parse from embedded JavaScript
            return parseChaptersFromScript(scriptContent, novelUrl)
        }
        
        // Fallback: parse from HTML table
        return document.select("table#chapters tbody tr").mapIndexed { index, row ->
            val linkElement = row.selectFirst("td:first-child a")
            val chapterTitle = linkElement?.text() ?: "Chapter ${index + 1}"
            val chapterUrl = fixUrl(linkElement?.attr("href") ?: "")
            val dateText = row.selectFirst("td:last-child time")?.attr("datetime")
            val dateUploaded = parseDate(dateText) ?: 0L
            
            NovelChapter(
                url = chapterUrl,
                name = chapterTitle,
                dateUpload = dateUploaded,
                chapterNumber = (index + 1).toFloat()
            )
        }.reversed() // Royal Road lists newest first
    }
    
    private fun parseChaptersFromScript(script: String, novelUrl: String): List<NovelChapter> {
        val chapters = mutableListOf<NovelChapter>()
        
        try {
            // Extract the JSON array from the script
            val jsonStart = script.indexOf("window.chapters = ") + "window.chapters = ".length
            val jsonEnd = script.indexOf(";", jsonStart)
            val jsonStr = script.substring(jsonStart, jsonEnd)
            
            val jsonArray = json.parseToJsonElement(jsonStr).jsonArray
            
            jsonArray.forEachIndexed { index, element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                val url = obj["url"]?.jsonPrimitive?.content?.let { fixUrl(it) } ?: ""
                val date = obj["date"]?.jsonPrimitive?.content?.let { parseDate(it) } ?: 0L
                
                chapters.add(NovelChapter(
                    url = url,
                    name = title,
                    dateUpload = date,
                    chapterNumber = (index + 1).toFloat()
                ))
            }
        } catch (e: Exception) {
            // If parsing fails, return empty and let fallback handle it
        }
        
        return chapters
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val chapterContent = document.selectFirst("div.chapter-content")
        
        // Remove author notes, ads, etc.
        chapterContent?.select("div.author-note, div.advertisement, script, style")?.remove()
        
        return chapterContent?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/best-rated?page=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/fictions/latest-updates?page=$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
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
}
