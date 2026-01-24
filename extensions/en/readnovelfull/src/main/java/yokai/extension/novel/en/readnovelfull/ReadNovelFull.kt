package yokai.extension.novel.en.readnovelfull

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * ReadNovelFull source (readnovelfull.com)
 * A popular web novel aggregator with extensive library.
 * 
 * Uses AJAX chapter-archive API for chapter lists.
 */
class ReadNovelFull : ConfigurableNovelSource() {
    
    override val id: Long = 6021L
    override val name: String = "ReadNovelFull"
    override val baseUrl: String = "https://readnovelfull.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 300L
    
    override val selectors = SourceSelectors(
        // Browse selectors
        browseItemSelector = "div.col-novel-main > div.list-novel > div.row",
        browseTitleSelector = "h3.novel-title > a",
        browseCoverSelector = "img.cover",
        
        // Search selectors - same as browse for this site
        searchItemSelector = "div.col-novel-main > div.list-novel > div.row",
        searchTitleSelector = "h3.novel-title > a",
        searchCoverSelector = "img.cover",
        
        // Novel details selectors
        detailTitleSelector = "div.books > div.desc > h3.title",
        detailCoverSelector = "div.books > div.book > img",
        descriptionSelector = "div.desc-text",
        authorSelector = "li:contains(Author) > a",
        genreSelector = "li:contains(Genre) > a",
        statusSelector = "li:contains(Status) > a",
        
        // Chapter list - using AJAX so just placeholder
        chapterListSelector = "ul.list-chapter > li > a",
        
        // Chapter content
        chapterContentSelector = "div#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins", "div.google-auto-placed"),
        contentRemovePatterns = listOf(
            "readnovelfull.com",
            "[Updated from F r e e w e b n o v e l. c o m]"
        ),
        
        // URL patterns
        searchUrlPattern = "$baseUrl/novel-list/search?keyword={query}",
        popularUrlPattern = "$baseUrl/novel-list/most-popular-novel?page={page}",
        latestUrlPattern = "$baseUrl/novel-list/latest-release-novel?page={page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = if (page == 1) {
            "$baseUrl/novel-list/search?keyword=${query.encodeUrl()}"
        } else {
            "$baseUrl/novel-list/search?keyword=${query.encodeUrl()}&page=$page"
        }
        
        return parseNovelList(getDocument(url))
    }
    
    // Override getPopularNovels
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val document = getDocument("$baseUrl/novel-list/most-popular-novel?page=$page")
        return parseNovelList(document)
    }
    
    // Override getLatestUpdates
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val document = getDocument("$baseUrl/novel-list/latest-release-novel?page=$page")
        return parseNovelList(document)
    }
    
    private fun parseNovelList(document: Document): List<NovelSearchResult> {
        return document.select("div.col-novel-main > div.list-novel > div.row").mapNotNull { row ->
            val cols = row.select("> div > div")
            if (cols.size < 2) return@mapNotNull null
            
            val titleElement = cols.getOrNull(1)?.selectFirst("h3.novel-title > a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val url = titleElement.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            // Get poster and upgrade to larger size
            val coverUrl = cols.getOrNull(0)?.selectFirst("img")
                ?.attr("src")
                ?.replace("t-200x89", "t-300x439")
                ?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getNovelDetails
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val header = document.selectFirst("div.col-info-desc")
        val bookInfo = header?.selectFirst("div.info-holder > div.books")
        
        val title = bookInfo?.selectFirst("div.desc > h3.title")?.text()?.trim()
            ?: throw Exception("Could not find novel title")
        
        val coverUrl = bookInfo.selectFirst("div.book > img")
            ?.attr("src")
            ?.let { fixUrl(it) }
        
        val description = document.selectFirst("div.desc-text")?.text()
        
        val infoMeta = document.select("ul.info-meta > li")
        
        val author = infoMeta.find { it.selectFirst("h3")?.text() == "Author:" }
            ?.selectFirst("a")?.text()?.trim()
        
        val genres = infoMeta.find { it.selectFirst("h3")?.text() == "Genre:" }
            ?.select("a")?.map { it.text().trim() } ?: emptyList()
        
        val statusText = infoMeta.find { it.selectFirst("h3")?.text() == "Status:" }
            ?.selectFirst("a")?.text()
        
        val status = when {
            statusText?.contains("Ongoing", ignoreCase = true) == true -> NovelStatus.ONGOING
            statusText?.contains("Completed", ignoreCase = true) == true -> NovelStatus.COMPLETED
            else -> NovelStatus.UNKNOWN
        }
        
        return NovelDetails(
            url = url,
            title = title,
            coverUrl = coverUrl,
            description = description,
            author = author,
            genres = genres,
            status = status
        )
    }
    
    // Override getChapterList to use AJAX API
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Find the novel ID from the rating element
        val novelId = document.selectFirst("div#rating")?.attr("data-novel-id")
            ?: throw Exception("Could not find novel ID")
        
        // Fetch chapter list from AJAX API
        val chaptersDoc = getDocument("$baseUrl/ajax/chapter-archive?novelId=$novelId")
        
        return chaptersDoc.select("div.panel-body > div.row > div > ul.list-chapter > li > a")
            .mapIndexedNotNull { index, element ->
                val chapterName = element.selectFirst("span")?.text()?.trim() ?: return@mapIndexedNotNull null
                val chapterUrl = element.attr("href")
                
                if (chapterUrl.isBlank()) return@mapIndexedNotNull null
                
                NovelChapter(
                    url = fixUrl(chapterUrl),
                    name = chapterName,
                    chapterNumber = (index + 1).toFloat()
                )
            }
    }
    
    // Override getChapterContent to clean watermarks
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val content = document.selectFirst("div#chr-content")?.let { element ->
            element.select("script, div.ads, ins, div.google-auto-placed").remove()
            element.html()
        } ?: throw Exception("Could not find chapter content")
        
        return content
            .replace("[Updated from F r e e w e b n o v e l. c o m]", "")
            .replace("readnovelfull.com", "", ignoreCase = true)
    }
}
