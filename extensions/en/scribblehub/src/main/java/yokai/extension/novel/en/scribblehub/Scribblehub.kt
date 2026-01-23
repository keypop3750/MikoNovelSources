package yokai.extension.novel.en.scribblehub

import yokai.extension.novel.lib.*

/**
 * Source for Scribble Hub (scribblehub.com)
 * Popular platform for original web fiction and fan translations.
 */
class Scribblehub : NovelSource() {
    
    override val id: Long = 6001L
    override val name: String = "Scribblehub"
    override val baseUrl: String = "https://www.scribblehub.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=fictionposts&paged=$page"
        val document = getDocument(url)
        
        return document.select("div.search_main_box").map { element ->
            val titleElement = element.selectFirst("div.search_title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = titleElement?.attr("href") ?: ""
            val coverUrl = fixUrlOrNull(element.selectFirst("div.search_img img")?.attr("src"))
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("div.fic_title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.fic_image img")?.attr("src"))
        val description = document.selectFirst("div.wi_fic_desc")?.text()
        val author = document.selectFirst("span.auth_name_fic")?.text()
        
        val genres = document.select("span.wi_fic_genre a, span.wi_fic_showtags a").map { it.text() }
        
        val statusText = document.selectFirst("span.rnd_stats")?.text()
        val status = parseNovelStatus(statusText)
        
        val ratingText = document.selectFirst("span.cnt_rte")?.text()
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
        
        // Find the story ID for chapter list API
        val storyId = document.selectFirst("input#mypostid")?.attr("value") ?: return emptyList()
        
        // Scribblehub uses AJAX to load chapters
        val chapterListUrl = "$baseUrl/wp-admin/admin-ajax.php"
        val chapters = mutableListOf<NovelChapter>()
        var page = 1
        
        while (true) {
            val response = postForm(chapterListUrl, mapOf(
                "action" to "wi_gettocchp",
                "strSID" to storyId,
                "stession" to "",
                "page" to page.toString()
            ))
            
            val chapterDoc = parseHtml(response)
            val chapterElements = chapterDoc.select("li.toc_w")
            
            if (chapterElements.isEmpty()) break
            
            chapterElements.forEach { element ->
                val linkElement = element.selectFirst("a")
                val chapterTitle = linkElement?.text() ?: "Chapter ${chapters.size + 1}"
                val chapterUrl = linkElement?.attr("href") ?: ""
                val dateText = element.selectFirst("span.fic_date_pub")?.attr("title")
                val dateUploaded = parseDate(dateText) ?: 0L
                
                chapters.add(NovelChapter(
                    url = chapterUrl,
                    name = chapterTitle,
                    dateUpload = dateUploaded,
                    chapterNumber = (chapters.size + 1).toFloat()
                ))
            }
            
            page++
            if (page > 100) break // Safety limit
        }
        
        return chapters.reversed()
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        
        val chapterContent = document.selectFirst("div.chp_raw")
        
        // Remove author notes and ads
        chapterContent?.select("div.wi_authornotes, div.code-block, script, style")?.remove()
        
        return chapterContent?.html() ?: ""
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/series-ranking/?sort=5&order=1&pg=$page"
        return parseNovelList(url)
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/series-ranking/?sort=1&order=1&pg=$page"
        return parseNovelList(url)
    }
    
    private suspend fun parseNovelList(url: String): List<NovelSearchResult> {
        val document = getDocument(url)
        
        return document.select("div.search_main_box").map { element ->
            val titleElement = element.selectFirst("div.search_title > a")
            val title = titleElement?.text() ?: ""
            val novelUrl = titleElement?.attr("href") ?: ""
            val coverUrl = fixUrlOrNull(element.selectFirst("div.search_img img")?.attr("src"))
            val ratingText = element.selectFirst("span.search_ratings")?.text()
            val rating = ratingText?.toFloatOrNull()
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = coverUrl,
                rating = rating
            )
        }
    }
}
