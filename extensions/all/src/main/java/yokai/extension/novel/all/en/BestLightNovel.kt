package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for BestLightNovel (bestlightnovel.com)
 */
class BestLightNovel : NovelSource() {
    
    override val id: Long = 6011L
    override val name: String = "BestLightNovel"
    override val baseUrl: String = "https://bestlightnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search_novels/${query.replace(' ', '_')}"
        val document = getDocument(url)
        
        return document.select("div.danh_sach > div.list_category").mapNotNull { element ->
            val head = element.selectFirst("a") ?: return@mapNotNull null
            val title = head.attr("title")
            val novelUrl = fixUrl(head.attr("href"))
            val coverUrl = fixUrlOrNull(head.selectFirst("img")?.attr("src"))
            val latestChapter = element.selectFirst("a.chapter")?.text()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val infoHeaders = document.select("ul.truyen_info_right > li")
        val title = infoHeaders.getOrNull(0)?.selectFirst("h1")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("span.info_image > img")?.attr("src"))
        val description = document.select("div.entry-header > div").getOrNull(1)?.text()
        
        var author: String? = null
        infoHeaders.getOrNull(1)?.select("a")?.forEach { a ->
            val href = a.attr("href")
            if (a.hasText() && href.length > "$baseUrl/search_author/".length && 
                href.startsWith("$baseUrl/search_author/")) {
                author = a.text()
            }
        }
        
        val genres = infoHeaders.getOrNull(2)?.select("a")?.map { it.text() } ?: emptyList()
        val statusText = infoHeaders.getOrNull(3)?.selectFirst("a")?.text()
        val status = parseNovelStatus(statusText)
        
        val viewsText = infoHeaders.getOrNull(6)?.text()
        val views = viewsText?.replace(",", "")?.replace("\"", "")
            ?.substringAfter("View : ")?.toIntOrNull()
        
        var rating: Float? = null
        var ratingCount: Int? = null
        try {
            val ratingHeader = infoHeaders.getOrNull(9)?.selectFirst("em > em")?.select("em")
            rating = ratingHeader?.getOrNull(1)?.selectFirst("em > em")?.text()?.toFloatOrNull()
            ratingCount = ratingHeader?.getOrNull(2)?.text()?.replace(",", "")?.toIntOrNull()
        } catch (_: Throwable) {}
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status,
            views = views,
            rating = rating,
            ratingCount = ratingCount
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        return document.select("div.chapter-list > div").mapNotNull { element ->
            val spans = element.select("span")
            val text = spans.getOrNull(0)?.selectFirst("a")
            val chapterUrl = fixUrl(text?.attr("href") ?: return@mapNotNull null)
            val chapterName = text.text() ?: return@mapNotNull null
            val dateText = spans.getOrNull(1)?.text()
            
            NovelChapter(
                name = chapterName,
                url = chapterUrl,
                chapterNumber = -1f
            )
        }.reversed().mapIndexed { index, chapter -> 
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        val content = document.selectFirst("div.vung_doc")
        
        return content?.html()
            ?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
            ?.replace("Find authorized novels in Webnovel，faster updates, better experience，Please click for visiting. ", "")
            ?: ""
    }
}
