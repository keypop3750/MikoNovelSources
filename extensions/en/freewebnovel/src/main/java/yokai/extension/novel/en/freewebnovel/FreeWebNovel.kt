package yokai.extension.novel.en.freewebnovel

import yokai.extension.novel.lib.*

/**
 * Source for FreeWebNovel (freewebnovel.com)
 * Large library of translated web novels.
 */
class FreeWebNovel : NovelSource() {
    
    override val id: Long = 6004L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?searchkey=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("div.li-row > div.li > div.con").mapNotNull { element ->
            val titleElement = element.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifEmpty { titleElement.text() }
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("div.pic > img")?.attr("src"))
            
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
        val author = document.selectFirst("span.glyphicon-user")?.nextElementSibling()?.text()
        
        val genres = document.selectFirst("span.glyphicon-th-list")?.nextElementSibling()
            ?.text()?.split(", ") ?: emptyList()
        
        val statusText = document.selectFirst("span.s1.s3 a, span.s1.s2 a")?.text()
        val status = parseNovelStatus(statusText)
        
        val ratingText = document.selectFirst("div.score span.score-n")?.text()
        val rating = ratingText?.toFloatOrNull()
        
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
        val trimmedUrl = novelUrl.trim().removeSuffix("/")
        
        // Try to get chapter list via AJAX
        val response = get(novelUrl)
        val aid = Regex("[0-9]+s.jpg").find(response.body?.string() ?: "")
            ?.value?.substringBefore("s")
        
        if (aid != null) {
            try {
                val chaptersDoc = getDocument("$baseUrl/api/chapterlist.php?aid=$aid")
                val prefix = trimmedUrl.removeSuffix(".html")
                
                return chaptersDoc.select("option").mapIndexed { index, element ->
                    val chapterPath = element.attr("value").split('/').last()
                    val chapterUrl = "$prefix/$chapterPath"
                    val chapterTitle = element.text().ifEmpty { "Chapter ${index + 1}" }
                    
                    NovelChapter(
                        url = chapterUrl,
                        name = chapterTitle,
                        chapterNumber = (index + 1).toFloat()
                    )
                }
            } catch (e: Exception) {
                // Fall through to HTML parsing
            }
        }
        
        // Fallback: parse from HTML
        return document.select("ul.chapter-list li a").mapIndexed { index, element ->
            val chapterTitle = element.text()
            val chapterUrl = fixUrl(element.attr("href"))
            
            NovelChapter(
                url = chapterUrl,
                name = chapterTitle,
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        // Remove first paragraph (often contains site name)
        document.selectFirst("div.txt > p:first-child")?.remove()
        document.selectFirst("div.txt > .notice-text")?.remove()
        
        val chapterContent = document.selectFirst("div.txt")
        
        var content = chapterContent?.html() ?: ""
        
        // Clean up watermarks
        content = content
            .replace("freewebnovel.com", "", ignoreCase = true)
            .replace("libread.com", "", ignoreCase = true)
            .replace("New novel chapters are published on Freewebnovel.com.", "")
            .replace("The source of this content is Freewebnᴏvel.com.", "")
        
        return content
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/most-popular-novel/$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/latest-release-novels/$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row").mapNotNull { element ->
            val titleElement = element.selectFirst("h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifEmpty { titleElement.text() }
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(element.selectFirst("div.pic > a > img")?.attr("src"))
            val latestChapter = element.select("div.item").getOrNull(2)?.selectFirst("div > a")?.text()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                latestChapter = latestChapter
            )
        }
    }
}
