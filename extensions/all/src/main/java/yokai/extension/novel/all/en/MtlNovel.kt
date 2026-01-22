package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for MtlNovel (mtlnovel.com)
 * Machine translated novels.
 */
class MtlNovel : NovelSource() {
    
    override val id: Long = 6009L
    override val name: String = "MtlNovel"
    override val baseUrl: String = "https://www.mtlnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1500L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/wp-admin/admin-ajax.php"
        
        val response = client.newCall(
            okhttp3.Request.Builder()
                .url(url)
                .post(
                    okhttp3.FormBody.Builder()
                        .add("action", "autosuggest")
                        .add("q", query)
                        .build()
                )
                .headers(headers)
                .build()
        ).execute()
        
        val results = mutableListOf<NovelSearchResult>()
        
        try {
            val jsonText = response.body?.string() ?: return emptyList()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val items = json.parseToJsonElement(jsonText)
                .jsonObject["items"]?.jsonArray?.firstOrNull()
                ?.jsonArray ?: return emptyList()
            
            items.forEach { item ->
                val obj = item.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: return@forEach
                val novelUrl = obj["permalink"]?.jsonPrimitive?.content ?: return@forEach
                val coverUrl = obj["thumbnail"]?.jsonPrimitive?.content
                
                results.add(NovelSearchResult(
                    title = title.replace(Regex("<[^>]*>"), ""), // Strip HTML tags
                    url = novelUrl,
                    coverUrl = coverUrl
                ))
            }
        } catch (e: Exception) {
            // Fallback to HTML search
            val htmlUrl = "$baseUrl/?s=${query.encodeUrl()}&post_type=wp-manga"
            val document = getDocument(htmlUrl)
            
            document.select("div.search-results div.novel-item").forEach { element ->
                val titleElement = element.selectFirst("a.novel-title") ?: return@forEach
                results.add(NovelSearchResult(
                    title = titleElement.text(),
                    url = fixUrl(titleElement.attr("href")),
                    coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
                ))
            }
        }
        
        return results
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1.entry-title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.nov-head amp-img, img.wp-post-image")?.attr("src"))
        val description = document.selectFirst("div.desc, div.summary__content")?.text()
        
        val infoBlock = document.selectFirst("div.info, table.info")
        val author = infoBlock?.selectFirst("tr:contains(Author) td:last-child, span:contains(Author)")
            ?.text()?.substringAfter(":")?.trim()
        
        val genres = document.select("div.genres a, a[href*=novel-genre]").map { it.text() }
        val statusText = document.selectFirst("tr:contains(Status) td:last-child, span:contains(Status)")?.text()
        val status = parseNovelStatus(statusText)
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val chapterListUrl = "$novelUrl/chapter-list/"
        val document = getDocument(chapterListUrl)
        
        return document.select("div.ch-list a.ch-link").reversed().mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.par, div.chapter-content")
        content?.select("script, ins, div.ads, div.code-block, div.adsbygoogle")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
    
    private val kotlinx.serialization.json.JsonElement.jsonObject 
        get() = this as kotlinx.serialization.json.JsonObject
    private val kotlinx.serialization.json.JsonElement.jsonArray 
        get() = this as kotlinx.serialization.json.JsonArray
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive 
        get() = this as kotlinx.serialization.json.JsonPrimitive
}
