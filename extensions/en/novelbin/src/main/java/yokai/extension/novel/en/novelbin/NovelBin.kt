package yokai.extension.novel.en.novelbin

import yokai.extension.novel.lib.*

/**
 * Source for NovelBin (novelbin.com)
 * Large library of translated novels with multiple genres.
 */
class NovelBin : NovelSource() {
    
    override val id: Long = 6003L
    override val name: String = "NovelBin"
    override val baseUrl: String = "https://novelbin.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    private val fullPosterRegex = Regex("/novel_[0-9]*_[0-9]*/")
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?keyword=${query.encodeUrl()}"
        val document = getDocument(url)
        
        return document.select("#list-page>.archive>.list>.row").mapNotNull { element ->
            val titleElement = element.selectFirst(">div>div>.truyen-title>a")
                ?: element.selectFirst(">div>div>.novel-title>a")
                ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(
                element.selectFirst(">div>div>img")?.attr("src")
                    ?.replace(fullPosterRegex, "/novel/")
            )
            
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
        val coverUrl = fixUrlOrNull(
            document.selectFirst("div.book img")?.attr("src")
                ?.replace(fullPosterRegex, "/novel/")
        )
        val description = document.selectFirst("div.desc-text")?.text()
        val author = document.selectFirst("ul.info-meta li:contains(Author) a")?.text()
        
        val genres = document.select("ul.info-meta li:contains(Genre) a").map { it.text() }
        
        val statusText = document.selectFirst("ul.info-meta li:contains(Status) a")?.text()
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
        
        // Get novel ID for AJAX request
        val novelId = document.selectFirst("div#rating")?.attr("data-novel-id")
        
        if (novelId != null) {
            // Try AJAX endpoint for full chapter list
            try {
                val ajaxUrl = "$baseUrl/ajax/chapter-archive?novelId=$novelId"
                val ajaxDoc = getDocument(ajaxUrl)
                
                return ajaxDoc.select("ul.list-chapter li a").mapIndexed { index, element ->
                    val chapterTitle = element.text()
                    val chapterUrl = fixUrl(element.attr("href"))
                    
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
        
        // Fallback: parse from page
        return document.select("#list-chapter .row ul li a").mapIndexed { index, element ->
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
        
        val chapterContent = document.selectFirst("#chapter-content")
            ?: document.selectFirst("#chr-content")
        
        // Remove ads and watermarks
        chapterContent?.select("script, div.ads, ins.adsbygoogle, iframe")?.remove()
        
        var content = chapterContent?.html() ?: ""
        
        // Clean up common watermarks
        content = content
            .replace("[Updated from F r e e w e b n o v e l. c o m]", "")
            .replace("If you find any errors ( broken links, non-standard content, etc.. ), Please let us know", "")
            .replace(Regex("<iframe.*?</iframe>", RegexOption.DOT_MATCHES_ALL), "")
        
        return content
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/sort/top-hot-novel?page=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/sort/latest?page=$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.list>div.row").mapNotNull { element ->
            val titleElement = element.selectFirst("div > div > h3.novel-title > a")
                ?: return@mapNotNull null
            val title = titleElement.text()
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = fixUrlOrNull(
                element.selectFirst("div > div > img")?.attr("data-src")
                    ?.replace(fullPosterRegex, "/novel/")
            )
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
}
