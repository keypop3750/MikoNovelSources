package yokai.extension.novel.all.en

import yokai.extension.novel.lib.*

/**
 * Source for Scribblehub (scribblehub.com)
 * Popular webnovel platform with strong community features.
 */
class Scribblehub : NovelSource() {
    
    override val id: Long = 6001L
    override val name: String = "Scribblehub"
    override val baseUrl: String = "https://www.scribblehub.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val url = "$baseUrl/?s=${query.encodeUrl()}&post_type=fictionposts"
        val document = getDocument(url)
        
        return document.select("div.search_main_box").mapNotNull { element ->
            val img = element.selectFirst("div.search_img > img")?.attr("src")
            val body = element.selectFirst("div.search_body > div.search_title > a")
            val title = body?.text() ?: return@mapNotNull null
            val novelUrl = body.attr("href") ?: return@mapNotNull null
            
            NovelSearchResult(
                title = title,
                url = novelUrl,
                coverUrl = img
            )
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = getDocument(url)
        
        val title = document.selectFirst("div.fic_title")?.text() ?: ""
        val coverUrl = fixUrlOrNull(document.selectFirst("div.fic_image > img")?.attr("src"))
        val description = document.selectFirst("div.wi_fic_desc")?.text()
        val author = document.selectFirst("span.auth_name_fic")?.text()
        
        val genres = document.select("span.wi_fic_genre > span > a.fic_genre").map { it.text() }
        val tags = document.select("span.wi_fic_showtags > span.wi_fic_showtags_inner > a").map { it.text() }
        
        val statusText = document.selectFirst("ul.widget_fic_similar > li > span")
            ?.lastElementSibling()?.ownText()?.substringBefore("-")
        val status = parseNovelStatus(statusText)
        
        val ratings = document.select("span#ratefic_user > span > span")
        val rating = ratings.firstOrNull()?.text()?.toFloatOrNull()
        val ratingCount = ratings.getOrNull(1)?.selectFirst("span")?.text()
            ?.replace(" ratings", "")?.toIntOrNull()
        
        return NovelDetails(
            url = url,
            title = title,
            author = author,
            coverUrl = coverUrl,
            description = description,
            genres = genres,
            tags = tags,
            status = status,
            rating = rating,
            ratingCount = ratingCount
        )
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        // Extract novel ID from URL
        val id = Regex("series/([0-9]*?)/").find(novelUrl)?.groupValues?.get(1)
            ?: throw Exception("Could not extract novel ID from URL")
        
        // Fetch chapter list via AJAX
        val response = client.newCall(
            okhttp3.Request.Builder()
                .url("$baseUrl/wp-admin/admin-ajax.php")
                .post(
                    okhttp3.FormBody.Builder()
                        .add("action", "wi_getreleases_pagination")
                        .add("pagenum", "1")
                        .add("mypostid", id)
                        .build()
                )
                .headers(
                    okhttp3.Headers.Builder()
                        .addAll(headers)
                        .add("Cookie", "toc_show=10000;toc_sorder=asc")
                        .build()
                )
                .build()
        ).execute()
        
        val doc = org.jsoup.Jsoup.parse(response.body?.string() ?: "")
        
        return doc.select("ol.toc_ol > li").mapIndexedNotNull { index, element ->
            val aHeader = element.selectFirst("a")
            val chapterUrl = aHeader?.attr("href") ?: return@mapIndexedNotNull null
            val chapterName = aHeader.ownText().ifBlank { "Chapter ${index + 1}" }
            val date = element.selectFirst("span")?.text()
            
            NovelChapter(
                name = chapterName,
                url = chapterUrl,
                dateUpload = parseRelativeDate(date),
                chapterNumber = (index + 1).toFloat()
            )
        }
    }
    
    override suspend fun getChapterContent(chapterUrl: String): String {
        val document = getDocument(chapterUrl)
        return document.selectFirst("div#chp_raw")?.html() ?: ""
    }
    
    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        // Parse relative dates like "2 days ago", "1 week ago", etc.
        val now = System.currentTimeMillis()
        return try {
            when {
                dateStr.contains("second") -> now - dateStr.extractNumber() * 1000
                dateStr.contains("minute") -> now - dateStr.extractNumber() * 60 * 1000
                dateStr.contains("hour") -> now - dateStr.extractNumber() * 60 * 60 * 1000
                dateStr.contains("day") -> now - dateStr.extractNumber() * 24 * 60 * 60 * 1000
                dateStr.contains("week") -> now - dateStr.extractNumber() * 7 * 24 * 60 * 60 * 1000
                dateStr.contains("month") -> now - dateStr.extractNumber() * 30 * 24 * 60 * 60 * 1000
                dateStr.contains("year") -> now - dateStr.extractNumber() * 365 * 24 * 60 * 60 * 1000
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun String.extractNumber(): Long {
        return Regex("(\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
