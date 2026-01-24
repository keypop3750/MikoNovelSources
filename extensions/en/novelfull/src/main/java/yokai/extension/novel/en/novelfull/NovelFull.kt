package yokai.extension.novel.en.novelfull

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    override val rateLimitMs: Long = 100L  // Reduced from 500ms to speed up chapter loading
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?keyword=${query.encodeUrl()}&page=$page"
        val document = getDocument(url)
        
        // Updated selector for new website structure
        return document.select("div.list.list-truyen div.row").map { element ->
            val titleElement = element.selectFirst("h3.truyen-title a") ?: element.selectFirst("a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            // Keep thumbnail URL as-is - NovelFull only provides thumbnails
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
        
        val title = document.selectFirst("h3.title")?.text() ?: ""
        // Keep thumbnail URL - NovelFull only provides thumbnails
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
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> = coroutineScope {
        // First, get the total page count from the first page
        val firstPageUrl = "$novelUrl?page=1&per-page=50"
        val firstDocument = getDocument(firstPageUrl)
        
        // Find total pages from pagination
        val lastPageLink = firstDocument.selectFirst("li.last a")?.attr("href")
        val totalPages = lastPageLink?.let {
            Regex("page=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: run {
            // Try to find the last page number from pagination items
            val pageNumbers = firstDocument.select(".pagination li a")
                .mapNotNull { it.text().toIntOrNull() }
            pageNumbers.maxOrNull() ?: 1
        }
        
        android.util.Log.d("NOVELFULL", "Total chapter pages detected: $totalPages")
        
        // Parse first page chapters
        val firstPageChapters = parseChaptersFromDocument(firstDocument)
        
        if (totalPages <= 1) {
            return@coroutineScope firstPageChapters
        }
        
        // Fetch remaining pages in parallel (batch of 5 to avoid overwhelming the server)
        val remainingPages = (2..totalPages.coerceAtMost(200)).toList()
        val batchSize = 5
        val allChapters = mutableListOf<NovelChapter>()
        allChapters.addAll(firstPageChapters)
        
        remainingPages.chunked(batchSize).forEach { batch ->
            val batchResults = batch.map { page ->
                async {
                    try {
                        val url = "$novelUrl?page=$page&per-page=50"
                        val doc = getDocument(url)
                        parseChaptersFromDocument(doc)
                    } catch (e: Exception) {
                        android.util.Log.e("NOVELFULL", "Error fetching page $page: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll()
            
            batchResults.forEach { chapters ->
                allChapters.addAll(chapters)
            }
        }
        
        // Remove duplicates and assign chapter numbers
        val uniqueChapters = allChapters.distinctBy { it.url }
        uniqueChapters.mapIndexed { index, chapter ->
            NovelChapter(
                url = chapter.url,
                name = chapter.name,
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    private fun parseChaptersFromDocument(document: org.jsoup.nodes.Document): List<NovelChapter> {
        var chapterElements = document.select("ul.list-chapter li a")
        
        if (chapterElements.isEmpty()) {
            chapterElements = document.select("#list-chapter a")
        }
        
        if (chapterElements.isEmpty()) {
            chapterElements = document.select(".list-chapter a[href*='/chapter']")
        }
        
        if (chapterElements.isEmpty()) {
            chapterElements = document.select("a[href*='chapter-']")
        }
        
        return chapterElements.map { element ->
            NovelChapter(
                url = fixUrl(element.attr("href")),
                name = element.text(),
                chapterNumber = 0f  // Will be assigned later
            )
        }
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
        
        // Updated selector for new website structure: div.list.list-truyen contains the novels
        val novels = document.select("div.list.list-truyen div.row")
        
        return novels.map { element ->
            val titleElement = element.selectFirst("h3.truyen-title a") ?: element.selectFirst("a")
            val title = titleElement?.text() ?: ""
            val novelUrl = fixUrl(titleElement?.attr("href") ?: "")
            // Keep the thumbnail URL as-is - NovelFull only provides thumbnail images
            val coverUrl = fixUrlOrNull(element.selectFirst("img")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
}
