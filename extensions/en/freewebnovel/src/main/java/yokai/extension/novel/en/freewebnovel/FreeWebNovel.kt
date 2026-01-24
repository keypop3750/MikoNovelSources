package yokai.extension.novel.en.freewebnovel

import yokai.extension.novel.lib.*
import org.jsoup.Jsoup

/**
 * Source for FreeWebNovel (freewebnovel.com)
 * Large library of translated web novels.
 * 
 * Uses the LibRead-style template.
 */
class FreeWebNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6004L
    override val name: String = "FreeWebNovel"
    override val baseUrl: String = "https://freewebnovel.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors - LibRead/FreeWebNovel style
        searchItemSelector = "div.li-row > div.li > div.con",
        searchTitleSelector = "div.txt > h3.tit > a",
        searchCoverSelector = "div.pic > img",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.ul-list1.ul-list1-2.ss-custom > div.li-row",
        browseTitleSelector = "h3.tit > a",
        browseCoverSelector = "div.pic > a > img",
        
        // Novel details selectors
        detailTitleSelector = "h1.tit",
        detailCoverSelector = "div.pic > img",
        descriptionSelector = "div.inner",
        authorSelector = "span.glyphicon-user + a, span.glyphicon.glyphicon-user ~ a",
        genreSelector = "span.glyphicon-th-list + a, span.glyphicon.glyphicon-th-list ~ a",
        statusSelector = "span.s1.s3 a, span.s1.s2 a",
        ratingSelector = "div.score span.score-n",
        
        // Chapter list selectors - uses AJAX API
        chapterListSelector = "ul.chapter-list li a, option[value]",
        
        // Chapter content selectors
        chapterContentSelector = "div.txt",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.txt > p:first-child", "div.txt > .notice-text"),
        contentRemovePatterns = listOf(
            "freewebnovel.com",
            "libread.com",
            "New novel chapters are published on Freewebnovel.com.",
            "The source of this content is Freewebnᴏvel.com.",
            "☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜"
        ),
        
        // URL patterns
        searchUrlPattern = "https://freewebnovel.com/search?searchkey={query}",
        popularUrlPattern = "https://freewebnovel.com/most-popular-novel/{page}",
        latestUrlPattern = "https://freewebnovel.com/latest-release-novels/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search to use POST request
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val response = postForm(
            "$baseUrl/search",
            mapOf("searchkey" to query)
        )
        val document = parseHtml(response)
        return parseNovelItems(document, forSearch = true)
    }
    
    // Override getChapterList to use FreeWebNovel's AJAX API
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val trimmedUrl = novelUrl.trim().removeSuffix("/")
        val response = get(novelUrl)
        val responseText = response.body?.string() ?: ""
        
        // Extract aid from the page
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
