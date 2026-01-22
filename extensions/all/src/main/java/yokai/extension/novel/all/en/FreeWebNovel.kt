package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for FreeWebNovel (freewebnovel.com)
 */
class FreeWebNovel : NovelSource() {
    
    override val id: Long = 6005L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?searchkey=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.li-row").mapNotNull { element ->
            val titleElement = element.selectFirst("h3.tit > a") ?: return@mapNotNull null
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
        
        val title = document.selectFirst("h1.tit")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.pic > img")?.attr("src"))
        val description = document.selectFirst("div.inner")?.text()
        val author = document.selectFirst("span:contains(Author) > a")?.text()
        
        val genres = document.select("div.right a[href*=genre]").map { it.text() }
        val statusText = document.selectFirst("span:contains(Status) > a")?.text()
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
        
        return document.select("div.m-newest2 > ul.ul-list5 > li > a").mapIndexed { index, element ->
            NovelChapter(
                name = element.text(),
                url = fixUrl(element.attr("href")),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.txt")
        content?.select("script, ins, div.ads, div.adsbygoogle")?.remove()
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
