package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for LibRead (libread.com)
 */
class LibRead : NovelSource() {
    
    override val id: Long = 6004L
    override val name: String = "LibRead"
    override val baseUrl: String = "https://libread.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?q=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.novel-item, li.novel-item").mapNotNull { element ->
            val titleElement = element.selectFirst("a.novel-title, h4 > a") ?: return@mapNotNull null
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
        
        val title = document.selectFirst("h1.novel-title, h3.title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("figure.cover > img, div.book img")?.attr("src"))
        val description = document.selectFirst("div.summary, div.desc-text")?.text()
        val author = document.selectFirst("span.author, a[href*=author]")?.text()
        
        val genres = document.select("div.categories > a, a[href*=genre]").map { it.text() }
        val statusText = document.selectFirst("span.status, div.header-stats span:contains(Status)")?.text()
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
        
        return document.select("ul.chapter-list > li > a, div.chapter-list a").mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.chapter-content, div#chapter-container")
        content?.select("script, ins, div.ads")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
