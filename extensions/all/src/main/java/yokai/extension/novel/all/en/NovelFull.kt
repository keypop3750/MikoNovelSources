package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for NovelFull (novelfull.com)
 * Large collection of translated novels.
 */
class NovelFull : NovelSource() {
    
    override val id: Long = 6002L
    override val name: String = "NovelFull"
    override val baseUrl: String = "https://novelfull.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?keyword=${query.encodeUrl()}&page=$page"
        val document = getDocument(url)
        
        return document.select("div.list-truyen > div.row").mapNotNull { element ->
            val titleElement = element.selectFirst("h3.truyen-title > a") ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
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
        
        val infoElements = document.select("div.info > div")
        var author: String? = null
        var status: NovelStatus = NovelStatus.UNKNOWN
        
        infoElements.forEach { info ->
            val label = info.selectFirst("h3")?.text()?.lowercase() ?: ""
            val value = info.selectFirst("a")?.text() ?: info.ownText()
            when {
                label.contains("author") -> author = value
                label.contains("status") -> status = parseNovelStatus(value)
            }
        }
        
        val genres = document.select("div.info > div > a[href*=genre]").map { it.text() }
        
        val ratingText = document.selectFirst("div.small")?.text()
        val rating = Regex("([0-9.]+)").find(ratingText ?: "")?.groupValues?.get(1)?.toFloatOrNull()
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            status = status,
            rating = rating
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Get novel ID for pagination
        val novelId = document.selectFirst("input#id-novel")?.attr("value")
        
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        var hasMore = true
        
        while (hasMore) {
            val chapterUrl = "$novelUrl?page=$page&per-page=50"
            val chapterDoc = if (page == 1) document else getDocument(chapterUrl)
            
            val pageChapters = chapterDoc.select("ul.list-chapter > li > a").mapIndexed { index, element ->
                NovelChapter(
                    name = element.text(),
                    url = fixUrl(element.attr("href")),
                    chapterNumber = (chapters.size + index + 1).toFloat()
                )
            }
            
            if (pageChapters.isEmpty()) {
                hasMore = false
            } else {
                chapters.addAll(pageChapters)
                page++
                if (page > 100) hasMore = false // Safety limit
            }
        }
        
        return chapters
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val content = document.selectFirst("div#chapter-content")
        
        // Remove ads and scripts
        content?.select("script, ins, div.ads, div[id*=ad]")?.remove()
        
        return content?.html() ?: ""
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
