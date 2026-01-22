package yokai.extension.novel.all.id

import yokai.extension.novel.lib.*

/**
 * Source for SakuraNovel (sakuranovel.id)
 * Indonesian translated novels.
 */
class SakuraNovel : NovelSource() {
    
    override val id: Long = 6101L
    override val name: String = "SakuraNovel"
    override val baseUrl: String = "https://sakuranovel.id"
    override val lang: String = "id"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=series"
        val document = getDocument(url)
        
        return document.select("div.bsx, article.bs").mapNotNull { element ->
            val titleElement = element.selectFirst("a[title], a.tip") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifEmpty { titleElement.text() }
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
        
        val title = document.selectFirst("h1.entry-title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.thumb img")?.attr("src"))
        val description = document.selectFirst("div.entry-content")?.text()
        val author = document.selectFirst("span:contains(Author)")?.nextElementSibling()?.text()
        
        val genres = document.select("span.mgen a").map { it.text() }
        val statusText = document.selectFirst("span:contains(Status)")?.nextElementSibling()?.text()
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
        
        return document.select("div.eplister ul li a").reversed().mapIndexed { index, element ->
            val chapterTitle = element.selectFirst("span.chapternum")?.text() 
                ?: element.text()
            
            NovelChapter(
                name = chapterTitle,
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.epcontent, div.entry-content")
        content?.select("script, ins, div.ads, div.kln")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
