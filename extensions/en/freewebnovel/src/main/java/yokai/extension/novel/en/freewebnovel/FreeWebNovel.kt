package yokai.extension.novel.en.freewebnovel

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Source for FreeWebNovel (freewebnovel.com)
 * Large library of translated web novels.
 * 
 * Uses the LibRead-style template - extends LibReadProvider in QuickNovel.
 */
class FreeWebNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6004L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors - LibRead/FreeWebNovel style (POST search returns different HTML)
        searchItemSelector = "div.li-row",
        searchTitleSelector = "h3.tit > a",
        searchCoverSelector = "div.pic > a > img, div.pic > img",
        coverAttribute = "src",
        
        // Browse selectors - from LibReadProvider
        browseItemSelector = "div.ul-list1.ul-list1-2.ss-custom > div.li-row",
        browseTitleSelector = "h3.tit > a",
        browseCoverSelector = "div.pic > a > img",
        
        // Novel details selectors - from LibReadProvider
        detailTitleSelector = "h1.tit",
        detailCoverSelector = "div.pic > img",
        descriptionSelector = "div.inner",
        authorSelector = "span.glyphicon.glyphicon-user ~ a",
        genreSelector = "span.glyphicon.glyphicon-th-list ~ a",
        statusSelector = "span.s1.s3 a, span.s1.s2 a",
        ratingSelector = "div.m-desc > div.score > p:nth-child(2)",
        
        // Chapter list selectors - uses AJAX API
        chapterListSelector = "option[value]",
        
        // Chapter content selectors
        chapterContentSelector = "div.txt",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.txt > .notice-text"),
        contentRemovePatterns = listOf(
            "freewebnovel.com",
            "libread.com",
            "New novel chapters are published on Freewebnovel.com.",
            "The source of this content is Freewebnᴏvel.com.",
            "☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜"
        ),
        
        // URL patterns - FIXED: correct URL for popular and latest
        searchUrlPattern = "https://freewebnovel.com/search", // POST request, query added as form data
        popularUrlPattern = "https://freewebnovel.com/sort/most-popular",  // No pagination
        latestUrlPattern = "https://freewebnovel.com/sort/latest-release/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override getPopularNovels to handle FreeWebNovel's non-paginated popular page
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        // FreeWebNovel's popular page shows all popular novels without pagination
        // Only fetch on page 1 to avoid duplicates
        if (page > 1) {
            return emptyList()
        }
        
        val document = getDocument("$baseUrl/sort/most-popular")
        return parseNovelItems(document, forSearch = false)
    }
    
    // Override getLatestUpdates to use the correct URL pattern
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/sort/latest-release/$page"
        val document = getDocument(url)
        return parseNovelItems(document, forSearch = false)
    }
    
    // Override search to use POST request with correct headers
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val response = postForm(
            "$baseUrl/search",
            mapOf("searchkey" to query),
            mapOf(
                "referer" to baseUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*"
            )
        )
        val document = parseHtml(response)
        
        return document.select("div.li-row > div.li > div.con").mapNotNull { element ->
            val titleElement = element.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null
            val title = titleElement.attr("title").ifBlank { titleElement.text() }
            val novelUrl = fixUrl(titleElement.attr("href"))
            val coverUrl = element.selectFirst("div.pic > img")?.attr("src")?.let { fixUrlOrNull(it) }
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getChapterList to use FreeWebNovel's AJAX API
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val trimmedUrl = novelUrl.trim().removeSuffix("/")
        val response = get(novelUrl)
        val responseText = response.body?.string() ?: ""
        
        // Extract aid from the page - look for pattern like "12345s.jpg"
        val aid = Regex("[0-9]+s.jpg").find(responseText)
            ?.value?.substringBefore("s")
        
        if (aid != null) {
            try {
                val chaptersResponse = postForm(
                    "$baseUrl/api/chapterlist.php",
                    mapOf("aid" to aid)
                )
                val chaptersDoc = Jsoup.parse(chaptersResponse.replace("""\""", ""))
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
                android.util.Log.e("FreeWebNovel", "AJAX chapter fetch failed: ${e.message}")
            }
        }
        
        // Fallback to HTML parsing
        val document = getDocument(novelUrl)
        return parseChaptersFromDocument(document).mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
