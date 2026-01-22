package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for PawRead (pawread.com)
 */
class PawRead : NovelSource() {
    
    override val id: Long = 6016L
    override val name: String = "PawRead"
    override val baseUrl: String = "https://pawread.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search/${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.novel-item, div.book-item").mapNotNull { element ->
            val titleElement = element.selectFirst("h3 > a, a.title") ?: return@mapNotNull null
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
        
        val title = document.selectFirst("h1, h3.title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.cover img, img.book-cover")?.attr("src"))
        val description = document.selectFirst("div.summary, div.description")?.text()
        val author = document.selectFirst("a[href*=author], span.author")?.text()
        
        val genres = document.select("a[href*=genre], span.genre").map { it.text() }
        val statusText = document.selectFirst("span.status")?.text()
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
        
        return document.select("div.chapter-list a, ul.chapters a").mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.chapter-content, div#chapter-content")
        content?.select("script, ins, div.ads")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
