package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for HiraethTranslation (hiraethtranslation.com)
 */
class HiraethTranslation : NovelSource() {
    
    override val id: Long = 6013L
    override val name: String = "HiraethTranslation"
    override val baseUrl: String = "https://hiraethtranslation.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=wp-manga"
        val document = getDocument(url)
        
        return document.select("div.c-tabs-item__content").mapNotNull { element ->
            val titleElement = element.selectFirst("div.post-title > h3 > a, h4 > a") ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("div.post-title > h1")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.summary_image img")?.attr("src"))
        val description = document.selectFirst("div.summary__content, div.description-summary")?.text()
        val author = document.selectFirst("div.author-content > a")?.text()
        
        val genres = document.select("div.genres-content > a").map { it.text() }
        val statusText = document.selectFirst("div.post-status")?.selectFirst("div.summary-content")?.text()
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
        val chapterListUrl = "$novelUrl/ajax/chapters/"
        
        val response = client.newCall(
            okhttp3.Request.Builder()
                .url(chapterListUrl)
                .post(okhttp3.FormBody.Builder().build())
                .headers(headers)
                .build()
        ).execute()
        
        val doc = org.jsoup.Jsoup.parse(response.body?.string() ?: "")
        
        return doc.select("li.wp-manga-chapter > a").reversed().mapIndexed { index, element ->
            NovelChapter(
                name = element.text().trim(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.reading-content, div.text-left")
        content?.select("script, ins, div.ads")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
