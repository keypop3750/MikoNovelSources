package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for Anna's Archive (annas-archive.org)
 * Large collection of books and documents.
 */
class AnnasArchive : NovelSource() {
    
    override val id: Long = 6017L
    override val name: String = "Anna's Archive"
    override val baseUrl: String = "https://annas-archive.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = false
    override val rateLimitMs: Long = 2000L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/search?q=${query.encodeUrl()}&ext=epub&lang=en"
        val document = getDocument(url)
        
        return document.select("a[href*=/md5/]").mapNotNull { element ->
            val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
            val novelUrl = fixUrl(element.attr("href"))
            val author = element.selectFirst("div.italic")?.text()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                author = author
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("h1, div.text-3xl")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("img[src*=cover]")?.attr("src"))
        val description = document.selectFirst("div.js-md5-top-box-description")?.text()
        val author = document.selectFirst("div:contains(Author) + div")?.text()
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            status = NovelStatus.COMPLETED // Books are typically complete
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        // Anna's Archive provides full books, not chapters
        // Return a single "chapter" representing the full book
        return listOf(
            NovelChapter(
                name = "Full Book (Download EPUB)",
                url = novelUrl,
                chapterNumber = 1f
            )
        )
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        // Anna's Archive doesn't support inline reading
        // The app should handle download links instead
        return """
            <div style="text-align: center; padding: 20px;">
                <h2>Download Required</h2>
                <p>This source provides downloadable EPUB files.</p>
                <p>Please use the download feature in the app to get this book.</p>
                <p><a href="$chapterUrl">Open in Browser</a></p>
            </div>
        """.trimIndent()
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
