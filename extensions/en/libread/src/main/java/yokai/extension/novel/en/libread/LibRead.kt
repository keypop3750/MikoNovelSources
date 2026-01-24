package yokai.extension.novel.en.libread

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * LibRead source (libread.com)
 * Popular web novel aggregator with a clean interface.
 * 
 * Uses POST search with form data and chapterlist.php API for chapters.
 */
class LibRead : ConfigurableNovelSource() {
    
    override val id: Long = 6020L
    override val name: String = "LibRead"
    override val baseUrl: String = "https://libread.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 300L
    
    override val selectors = SourceSelectors(
        // Browse selectors for main page listing
        browseItemSelector = "div.ul-list1.ul-list1-2.ss-custom > div.li-row",
        browseTitleSelector = "h3.tit > a",
        browseCoverSelector = "div.pic > a > img",
        
        // Search selectors - we use POST so these are for parsing response
        searchItemSelector = "div.li-row > div.li > div.con",
        searchTitleSelector = "div.txt > h3.tit > a",
        searchCoverSelector = "div.pic > img",
        
        // Novel details selectors
        detailTitleSelector = "h1.tit",
        detailCoverSelector = "div.pic > img, div.book > img",
        descriptionSelector = "div.txt > div.inner, div.content",
        authorSelector = "p.a-row:contains(Author) a, span.author a",
        genreSelector = "p.a-row:contains(Genre) a",
        statusSelector = "p.a-row:contains(Status) > span, span.s1.s2",
        
        // Chapter list (we override with API)
        chapterListSelector = "option",
        
        // Chapter content
        chapterContentSelector = "div.txt",
        contentRemoveSelectors = listOf("script", "div.ads", "ins", "div.chapter-warning", "p.report-tips"),
        contentRemovePatterns = listOf(
            "libread.com",
            "Please report us if you find any errors"
        ),
        
        // URL patterns
        searchUrlPattern = "$baseUrl/search",
        popularUrlPattern = "$baseUrl/sort/popular-novels/{page}",
        latestUrlPattern = "$baseUrl/sort/latest-release/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search to use POST request
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        val response = postForm(
            url = "$baseUrl/search",
            data = mapOf("searchkey" to query),
            headerMap = mapOf(
                "Referer" to baseUrl,
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "*/*"
            )
        )
        
        val document = Jsoup.parse(response, baseUrl)
        
        return document.select("div.li-row > div.li > div.con").mapNotNull { element ->
            val titleElement = element.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifBlank { titleElement.text() }
            val url = titleElement.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            val coverUrl = element.selectFirst("div.pic > img")
                ?.attr("src")
                ?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title.trim(),
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getPopularNovels
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val document = getDocument("$baseUrl/sort/popular-novels/$page")
        return parseNovelList(document)
    }
    
    // Override getLatestUpdates
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val document = getDocument("$baseUrl/sort/latest-release/$page")
        return parseNovelList(document)
    }
    
    private fun parseNovelList(document: Document): List<NovelSearchResult> {
        return document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row").mapNotNull { element ->
            val titleElement = element.selectFirst("h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifBlank { titleElement.text() }
            val url = titleElement.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            val coverUrl = element.selectFirst("div.pic > a > img")
                ?.attr("src")
                ?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title.trim(),
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getChapterList to use chapterlist.php API
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Extract the novel ID from the response - it's in an image src like "123s.jpg"
        val html = document.html()
        val aid = Regex("[0-9]+s\\.jpg").find(html)?.value?.substringBefore("s")
            ?: throw Exception("Could not find novel ID")
        
        // Fetch chapter list from API
        val chaptersResponse = postForm(
            url = "$baseUrl/api/chapterlist.php",
            data = mapOf("aid" to aid)
        )
        
        val chaptersDoc = Jsoup.parse(chaptersResponse.replace("\\\"", ""), baseUrl)
        val prefix = novelUrl.trim().removeSuffix("/")
        
        return chaptersDoc.select("option").mapIndexedNotNull { index, element ->
            val chapterSlug = element.attr("value").split('/').lastOrNull() ?: return@mapIndexedNotNull null
            val chapterUrl = "$prefix/$chapterSlug"
            val name = element.text().ifBlank { "Chapter ${index + 1}" }
            
            NovelChapter(
                url = chapterUrl,
                name = name.trim(),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    // Override getChapterContent to clean up watermarks
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        // Get content and clean it
        val content = document.selectFirst("div.txt")?.let { element ->
            // Remove ad elements
            element.select("script, div.ads, ins, div.chapter-warning, p.report-tips").remove()
            element.html()
        } ?: throw Exception("Could not find chapter content")
        
        // Clean up watermarks (including the fancy unicode version)
        return content
            .replace("\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62", "", ignoreCase = true)
            .replace("libread.com", "", ignoreCase = true)
    }
}
