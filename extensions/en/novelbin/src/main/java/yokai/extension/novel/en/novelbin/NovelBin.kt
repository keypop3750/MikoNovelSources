package yokai.extension.novel.en.novelbin

import yokai.extension.novel.lib.*
import org.jsoup.nodes.Document

/**
 * Source for NovelBin (novelbin.me)
 * Large library of translated novels with multiple genres.
 * 
 * The website has changed to a new domain and HTML structure.
 * Uses custom parsing for the new grid layout.
 */
class NovelBin : ConfigurableNovelSource() {
    
    override val id: Long = 6003L
    override val name: String = "NovelBin"
    override val baseUrl: String = "https://novelbin.me"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors - list style with rows
        searchItemSelector = "div.list div.row",
        searchTitleSelector = "h3.novel-title a",
        searchCoverSelector = "div.col-xs-3 img",
        coverAttribute = "data-src",
        
        // Browse selectors - same row structure as search
        browseItemSelector = "div.list div.row",
        browseTitleSelector = "h3.novel-title a",
        browseCoverSelector = "div.col-xs-3 img",
        browseCoverAttribute = "data-src",
        
        // Novel details selectors
        detailTitleSelector = "h3.title, h1.title",
        detailCoverSelector = "div.book img, div.pic img",
        detailCoverAttribute = "data-src",
        descriptionSelector = "div.desc-text, div.desc",
        authorSelector = "ul.info > li:nth-child(1) > a, span.author",
        genreSelector = "ul.info > li a[href*='/genre/'], div.genres a",
        statusSelector = "ul.info > li:nth-child(3) > a, span.status",
        
        // Chapter list selectors
        chapterListSelector = "select > option[value], ul.list-chapter li a, .chapter-list li a",
        novelIdSelector = "#rating, input[name='novel_id']",
        novelIdAttribute = "data-novel-id",
        chapterAjaxUrl = "https://novelbin.me/ajax/chapter-archive?novelId={id}",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content, #chr-content, div.chapter-content",
        chapterContentSelectorAlt = "#chr-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", ".ad-container"),
        contentRemovePatterns = listOf(
            "[Updated from F r e e w e b n o v e l. c o m]",
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know",
            "If you find any errors ( Ads popup, ads redirect, broken links, non-standard content, etc.. ), Please let us know",
            "novelbin.me",
            "novelbin.com"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novelbin.me/search?keyword={query}",
        popularUrlPattern = "https://novelbin.me/sort/novelbin-hot?page={page}",
        latestUrlPattern = "https://novelbin.me/sort/new-novel?page={page}",
        
        // Transform thumbnail URLs to full cover URLs
        // NovelBin uses /novel_80_113/ for thumbnails and /novel/ for full covers
        coverTransformRegex = Regex("/novel_[0-9]+_[0-9]+/"),
        coverTransformReplacement = "/novel/",
        
        // Don't need to fetch from details since we can transform the URL
        fetchFullCoverFromDetails = false
    )
}
