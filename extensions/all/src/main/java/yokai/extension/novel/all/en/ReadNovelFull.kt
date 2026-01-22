package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for ReadNovelFull (readnovelfull.com)
 */
class ReadNovelFull : NovelSource() {
    
    override val id: Long = 6010L
    override val name: String = "ReadNovelFull"
    override val baseUrl: String = "https://readnovelfull.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?keyword=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.list-novel > div.row").mapNotNull { element ->
            val titleElement = element.selectFirst("h3.novel-title > a") ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("img.cover")?.attr("src"))
            val author = element.selectFirst("span.author")?.text()
            
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
        
        val title = document.selectFirst("h3.title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.book > img")?.attr("src"))
        val description = document.selectFirst("div.desc-text")?.text()
        
        val infoItems = document.select("ul.info-meta > li")
        var author: String? = null
        var status: NovelStatus = NovelStatus.UNKNOWN
        val genres = mutableListOf<String>()
        
        infoItems.forEach { item ->
            val label = item.selectFirst("h3")?.text()?.lowercase() ?: ""
            when {
                label.contains("author") -> author = item.selectFirst("a")?.text()
                label.contains("status") -> status = parseNovelStatus(item.selectFirst("a")?.text())
                label.contains("genre") -> genres.addAll(item.select("a").map { it.text() })
            }
        }
        
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
        
        return document.select("ul.list-chapter > li > a").mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div#chr-content, div#chapter-content")
        content?.select("script, ins, div.ads")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
