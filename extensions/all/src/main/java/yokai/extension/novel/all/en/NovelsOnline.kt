package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for NovelsOnline (novelsonline.net)
 */
class NovelsOnline : NovelSource() {
    
    override val id: Long = 6008L
    override val name: String = "NovelsOnline"
    override val baseUrl: String = "https://novelsonline.net"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/sResults.php?s=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.top-novel-block").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 > a") ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("img.wp-post-image")?.attr("src"))
            val author = element.selectFirst("div.bl")?.text()?.substringAfter("Author :")?.trim()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                author = author
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.post-content img")?.attr("src"))
        val description = document.selectFirst("div.desc, div.summary")?.text()
        val author = document.selectFirst("li:contains(Author)")?.text()?.substringAfter(":")?.trim()
        
        val genres = document.select("li:contains(Genre) a").map { it.text() }
        val statusText = document.selectFirst("li:contains(Status)")?.text()
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
        val document = getDocument(novelUrl)
        
        return document.select("ul.chapters > li > a").mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div#contentall")
        content?.select("script, ins, iframe, div.ads")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
