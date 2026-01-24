package yokai.extension.novel.en.readlightnovel

import yokai.extension.novel.lib.*

/**
 * Source for ReadLightNovel (readlightnovel.me)
 * Large library of translated light novels.
 * 
 * WARNING: This site may have Cloudflare protection.
 * May require WebView to bypass protection on some occasions.
 */
class ReadLightNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6014L
    override val name: String = "ReadLightNovel"
    override val baseUrl: String = "https://www.readlightnovel.me"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L
    
    override val selectors = SourceSelectors(
        // Search selectors (search is POST-based autocomplete)
        searchItemSelector = "li > a",
        searchTitleSelector = "span:nth-child(2)",
        searchCoverSelector = "span:first-child img",
        coverAttribute = "src",
        
        // Browse selectors for top novels page
        browseItemSelector = "div.top-novel-block",
        browseTitleSelector = "div.top-novel-header > h2 > a",
        browseCoverSelector = "div.top-novel-cover a img",
        
        // Novel details selectors
        detailTitleSelector = "div.block-title h1",
        detailCoverSelector = "div.novel-cover a img",
        descriptionSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Description)) div.novel-detail-body",
        authorSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Author)) div.novel-detail-body a",
        genreSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Genre)) div.novel-detail-body a",
        statusSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Status)) div.novel-detail-body",
        ratingSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Rating)) div.novel-detail-body",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-chs li a",
        chapterListSelectorAlt = "div.tab-content ul li a",
        
        // Chapter content selectors
        chapterContentSelector = "div.chapter-content3 div.desc",
        contentRemoveSelectors = listOf(
            "script", "div.ads", "ins.adsbygoogle", "iframe",
            "div.alert", "#podium-spot", "small.ads-title", "div.hidden", "p.hid"
        ),
        contentRemovePatterns = listOf(
            "readlightnovel",
            "Please read this chapter at"
        ),
        
        // URL patterns
        searchUrlPattern = "", // Search requires POST with special headers
        popularUrlPattern = "https://www.readlightnovel.me/top-novels/most-viewed/{page}",
        latestUrlPattern = "https://www.readlightnovel.me/top-novels/top-rated/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search for POST-based autocomplete
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        val response = postForm(
            url = "$baseUrl/search/autocomplete",
            data = mapOf("q" to query),
            headerMap = mapOf(
                "referer" to baseUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*"
            )
        )
        
        val document = parseHtml(response)
        
        return document.select("li > a").mapNotNull { element ->
            val spans = element.select("span")
            if (spans.size < 2) return@mapNotNull null
            
            val title = spans.getOrNull(1)?.text()?.trim() ?: return@mapNotNull null
            val url = element.attr("href")
            
            if (title.isBlank() || url.isBlank()) return@mapNotNull null
            
            val coverUrl = spans.getOrNull(0)?.selectFirst("img")?.attr("src")?.let { fixUrl(it) }
            
            NovelSearchResult(
                title = title,
                url = fixUrl(url),
                coverUrl = coverUrl
            )
        }
    }
    
    // Override getChapterList for ReadLightNovel's panel-based structure
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        val allChapters = mutableListOf<NovelChapter>()
        
        // Novels have chapters organized in panels (volumes)
        val panels = document.select("div.panel")
        
        for (panel in panels) {
            val volumeName = panel.selectFirst("div.panel-heading h4.panel-title a")?.text()?.trim()
            val volumePrefix = if (volumeName == "Chapters" || volumeName.isNullOrBlank()) "" else "$volumeName • "
            
            val chapterLinks = panel.select("ul.chapter-chs li a")
            for (link in chapterLinks) {
                val chapterName = link.text().trim()
                val chapterUrl = link.attr("href")
                
                if (chapterUrl.isBlank() || chapterName.isBlank()) continue
                
                // Clean up chapter name (CH 1 -> Chapter 1)
                var cleanName = chapterName
                    .replace("CH ([0-9]+)".toRegex(), "Chapter $1")
                    .replace("CH ", "")
                
                cleanName = when (cleanName) {
                    "Pr" -> "Prologue"
                    "Ep" -> "Epilogue"
                    else -> cleanName
                }
                
                allChapters.add(NovelChapter(
                    url = fixUrl(chapterUrl),
                    name = volumePrefix + cleanName,
                    chapterNumber = 0f
                ))
            }
        }
        
        // If no panels found, try direct chapter list
        if (allChapters.isEmpty()) {
            document.select("ul.chapter-chs li a, div.tab-content ul li a").forEach { link ->
                val chapterName = link.text().trim()
                val chapterUrl = link.attr("href")
                
                if (chapterUrl.isNotBlank() && chapterName.isNotBlank()) {
                    allChapters.add(NovelChapter(
                        url = fixUrl(chapterUrl),
                        name = chapterName,
                        chapterNumber = 0f
                    ))
                }
            }
        }
        
        // Assign chapter numbers
        return allChapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
