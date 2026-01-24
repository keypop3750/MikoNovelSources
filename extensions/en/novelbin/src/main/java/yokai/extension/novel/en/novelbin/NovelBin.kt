package yokai.extension.novel.en.novelbin

import yokai.extension.novel.lib.*

/**
 * Source for NovelBin (novelbin.com)
 * Large library of translated novels with multiple genres.
 * 
 * Uses the AllNovel-style template with image URL transformation.
 */
class NovelBin : ConfigurableNovelSource() {
    
    override val id: Long = 6003L
    override val name: String = "NovelBin"
    override val baseUrl: String = "https://novelbin.com"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "#list-page>.archive>.list>.row",
        searchTitleSelector = ">div>div>.truyen-title>a, >div>div>.novel-title>a",
        searchCoverSelector = ">div>div>img",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.list>div.row",
        browseTitleSelector = "div > div > h3.novel-title > a",
        browseCoverSelector = "div > div > img",
        
        // Novel details selectors
        detailTitleSelector = "h3.title",
        detailCoverSelector = "div.book img",
        descriptionSelector = "div.desc-text",
        authorSelector = "ul.info-meta li:contains(Author) a, ul.info > li:nth-child(1) > a",
        genreSelector = "ul.info-meta li:contains(Genre) a, ul.info > li:nth-child(5) a",
        statusSelector = "ul.info-meta li:contains(Status) a, ul.info > li:nth-child(3) > a",
        
        // Chapter list selectors
        chapterListSelector = "ul.list-chapter li a, select > option[value], .list-chapter>li>a",
        novelIdSelector = "#rating",
        novelIdAttribute = "data-novel-id",
        chapterAjaxUrl = "https://novelbin.com/ajax/chapter-archive?novelId={id}",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content",
        chapterContentSelectorAlt = "#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
        contentRemovePatterns = listOf(
            "[Updated from F r e e w e b n o v e l. c o m]",
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelbin.com/search?keyword={query}",
        popularUrlPattern = "https://novelbin.com/sort/top-hot-novel?page={page}",
        latestUrlPattern = "https://novelbin.com/sort/latest?page={page}",
        
        // Transform thumbnail URLs to full cover URLs
        // NovelBin uses /novel_123_456/ for thumbnails and /novel/ for full covers
        coverTransformRegex = Regex("/novel_[0-9]*_[0-9]*/"),
        coverTransformReplacement = "/novel/",
        
        // Don't need to fetch from details since we can transform the URL
        fetchFullCoverFromDetails = false
    )
}
