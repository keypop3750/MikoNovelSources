package yokai.extension.novel.en.allnovel

import yokai.extension.novel.lib.*

/**
 * Source for AllNovel (allnovel.org)
 * Large library of translated Asian novels.
 */
class AllNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6010L
    override val name: String = "AllNovel"
    override val baseUrl: String = "https://allnovel.org"
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors
        searchItemSelector = "div.list > div.row",
        searchTitleSelector = "h3.truyen-title > a",
        searchCoverSelector = "img.cover",
        coverAttribute = "src",
        
        // Browse selectors
        browseItemSelector = "div.list > div.row",
        browseTitleSelector = "h3.truyen-title > a",
        browseCoverSelector = "img.cover",
        
        // Novel details selectors
        detailTitleSelector = "h3.title",
        detailCoverSelector = "div.book > img",
        descriptionSelector = "div.desc-text",
        authorSelector = "div.info > div:nth-child(1) > a",
        genreSelector = "div.info > div:nth-child(3) a",
        statusSelector = "div.info > div:nth-child(5) > a",
        
        // Chapter list selectors - uses AJAX endpoint
        chapterListSelector = "select > option[value], .list-chapter > li > a",
        novelIdSelector = "#rating",
        novelIdAttribute = "data-novel-id",
        chapterAjaxUrl = "https://allnovel.org/ajax-chapter-option?novelId={id}",
        
        // Chapter content selectors
        chapterContentSelector = "#chapter-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe"),
        contentRemovePatterns = listOf(
            "If you find any errors ( broken links, non-standard content, etc.. ), Please let us know",
            "allnovel.org"
        ),
        
        // URL patterns
        searchUrlPattern = "https://allnovel.org/search?keyword={query}",
        popularUrlPattern = "https://allnovel.org/most-popular?page={page}",
        latestUrlPattern = "https://allnovel.org/latest-release-novel?page={page}",
        
        fetchFullCoverFromDetails = false
    )
}
