package yokai.extension.novel.en.readlightnovel

import yokai.extension.novel.lib.*

/**
 * Source for ReadLightNovel (readlightnovel.me)
 * Large library of translated light novels.
 */
class ReadLightNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6014L
    override val name: String = "ReadLightNovel"
    override val baseUrl: String = "https://www.readlightnovel.me"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
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
        detailTitleSelector = "div.block-title h1",
        detailCoverSelector = "div.novel-cover img",
        descriptionSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Description)) div.novel-detail-body",
        authorSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Author)) div.novel-detail-body a",
        genreSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Genre)) div.novel-detail-body a",
        statusSelector = "div.novel-detail-item:has(.novel-detail-header:contains(Status)) div.novel-detail-body",
        
        // Chapter list selectors
        chapterListSelector = "ul.chapter-chs li a",
        
        // Chapter content selectors
        chapterContentSelector = "div.chapter-content3 div.desc",
        contentRemoveSelectors = listOf(
            "script", "div.ads", "ins.adsbygoogle", "iframe",
            "div.alert", "#podium-spot", "small.ads-title", "div"
        ),
        contentRemovePatterns = listOf(
            "readlightnovel",
            "Please read this chapter at"
        ),
        
        // URL patterns
        searchUrlPattern = "https://www.readlightnovel.me/search/autocomplete?q={query}",
        popularUrlPattern = "https://www.readlightnovel.me/top-novels/most-viewed/{page}",
        latestUrlPattern = "https://www.readlightnovel.me/latest-chapters/{page}",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override search for autocomplete-style search
    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        if (page > 1) return emptyList()
        
        val searchUrl = "$baseUrl/search/autocomplete?q=$query"
        val document = getDocument(searchUrl)
        
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
    
    // Override getChapterList for ReadLightNovel's structure
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        val chapters = document.select("ul.chapter-chs li a, div.tab-content ul li a").mapNotNull { element ->
            val url = element.attr("href")
            val name = element.text().trim()
            
            if (url.isNotBlank() && name.isNotBlank()) {
                NovelChapter(
                    url = fixUrl(url),
                    name = name,
                    chapterNumber = 0f
                )
            } else null
        }
        
        return chapters.mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
