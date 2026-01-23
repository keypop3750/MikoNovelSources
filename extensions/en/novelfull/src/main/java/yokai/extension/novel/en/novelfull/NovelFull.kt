package yokai.extension.novel.en.novelfull

import yokai.extension.novel.lib.*

/**
 * Source for NovelFull (novelfull.com)
 * Large library of translated Asian novels.
 */
class NovelFull : NovelSource() {
    
    override val id: Long = 6002L
    override val name: String = "NovelFull"
    override val baseUrl: String = "https://novelfull.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?keyword=${query.encodeUrl()}&page=$page"
        val document = getDocument(url)
        
        return document.select("div.list-novel > div.row").map { element ->
            val titleElement = element.selectFirst("h3.novel-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img.cover")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h3.title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.book img")?.attr("src"))
        val description = document.selectFirst("div.desc-text")?.text()
        val author = document.selectFirst("div.info > div:contains(Author) > a")?.text()
        
        val genres = document.select("div.info > div:contains(Genre) a").map { it.text() }
        
        val statusText = document.selectFirst("div.info > div:contains(Status) > a")?.text()
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
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        
        // Get novel ID from URL
        val novelId = novelUrl.substringAfterLast("/").substringBefore(".html")
        
        while (true) {
            val url = "$novelUrl?page=$page&per-page=50"
            val document = getDocument(url)
            
            val chapterElements = document.select("ul.list-chapter > li > a")
            if (chapterElements.isEmpty()) break
            
            chapterElements.forEach { element ->
                val chapterTitle = element.text()
                val chapterUrl = fixUrl(element.attr("href"))
                
                chapters.add(NovelChapter(
                    url = chapterUrl,
                    name = chapterTitle,
                    chapterNumber = (chapters.size + 1).toFloat()
                ))
            }
            
            // Check if there's a next page
            val hasNext = document.selectFirst("li.next:not(.disabled)") != null
            if (!hasNext) break
            
            page++
            if (page > 200) break // Safety limit
        }
        
        return chapters
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val chapterContent = document.selectFirst("div#chapter-content")
        
        // Remove ads and scripts
        chapterContent?.select("script, div.ads, ins.adsbygoogle")?.remove()
        
        return chapterContent?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/most-popular?page=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/latest-release-novel?page=$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.list-novel > div.row").map { element ->
            val titleElement = element.selectFirst("h3.novel-title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            val coverUrl = fixUrlOrNull(element.selectFirst("img.cover")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
}
