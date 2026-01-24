package yokai.extension.novel.en.novelsonline

import yokai.extension.novel.lib.*

/**
 * Source for NovelsOnline (novelsonline.org)
 * Note: Uses Cloudflare protection - may require special handling.
 */
class NovelsOnline : ConfigurableNovelSource() {
    
    override val id: Long = 6013L
    override val name: String = "NovelsOnline"
    override val baseUrl: String = "https://novelsonline.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 1000L
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "div.top-novel-block",
        searchTitleSelector = "div.top-novel-header h2 a",
        searchCoverSelector = "div.top-novel-cover img",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.top-novel-block",
        browseTitleSelector = "div.top-novel-header h2 a",
        browseCoverSelector = "div.top-novel-cover img",
        
        // Novel details selectors
        detailTitleSelector = "div.novel-detail-header h1",
        detailCoverSelector = "div.novel-cover img",
        descriptionSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Description)) div.content",
        authorSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Author)) div.content a",
        genreSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Genre)) div.content a",
        statusSelector = "div.novel-detail-body div.novel-detail-item:has(h4:contains(Status)) div.content",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-chs li a",
        
        // Chapter content selectors
        chapterContentSelector = "#contentall",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.novel-detail-item"),
        contentRemovePatterns = listOf(
            "novelsonline.org",
            "Your browser does not support JavaScript"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelsonline.org/search-ajax?q={query}",
        popularUrlPattern = "https://novelsonline.org/top-novel/{page}",
        latestUrlPattern = "https://novelsonline.org/latest-release/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search for POST-based search
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()  // Search doesn't support pagination
        
        val response = postForm(
            "$baseUrl/search-ajax",
            mapOf("q" to query)
        )
        val document = parseHtml(response)
        
        return document.select("li").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val title = link.text()
            val url = link.attr("href")
            
            if (title.isNotBlank() && url.isNotBlank()) {
                NovelSearchResult(
                    title = title,
                    url = fixUrl(url),
                    coverUrl = null
                )
            } else null
        }
    }
}
