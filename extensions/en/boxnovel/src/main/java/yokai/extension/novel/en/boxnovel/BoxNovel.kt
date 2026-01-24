package yokai.extension.novel.en.boxnovel

import yokai.extension.novel.lib.*

/**
 * Source for BoxNovel / NovLove (boxnovel.com / novlove.com)
 * Uses Madara theme - popular WordPress theme for novel sites.
 */
class BoxNovel : ConfigurableNovelSource() {
    
    override val id: Long = 6011L
    override val name: String = "BoxNovel"
    override val baseUrl: String = "https://novlove.com"  // boxnovel moved to novlove
    override val lang: String = "en"
    override val hasMainPage: Boolean = true
    override val rateLimitMs: Long = 500L
    
    override val selectors = SourceSelectors(
        // Search selectors - Madara theme
        searchItemSelector = "div.c-tabs-item__content",
        searchTitleSelector = "div.post-title h3 a, div.post-title h4 a",
        searchCoverSelector = "div.tab-thumb img",
        coverAttribute = "data-src",
        
        // Browse selectors - Madara theme
        browseItemSelector = "div.page-item-detail",
        browseTitleSelector = "div.item-summary div.post-title h3 a, div.item-thumb a",
        browseCoverSelector = "div.item-thumb img",
        browseCoverAttribute = "data-src",
        
        // Novel details selectors - Madara theme
        detailTitleSelector = "div.post-title h1",
        detailCoverSelector = "div.summary_image img",
        detailCoverAttribute = "data-src",
        descriptionSelector = "div.summary__content, div.description-summary",
        authorSelector = "div.author-content a",
        genreSelector = "div.genres-content a",
        statusSelector = "div.post-status div.post-content_item:contains(Status) div.summary-content",
        ratingSelector = "span.total_votes",
        
        // Chapter list selectors - Madara AJAX
        chapterListSelector = "li.wp-manga-chapter a",
        
        // Chapter content selectors
        chapterContentSelector = "div.text-left, div.reading-content",
        contentRemoveSelectors = listOf("script", "div.ads", "ins.adsbygoogle", "iframe", "div.code-block"),
        contentRemovePatterns = listOf(
            "boxnovel.com",
            "novlove.com",
            "If you find any errors"
        ),
        
        // URL patterns
        searchUrlPattern = "https://novlove.com/?s={query}&post_type=wp-manga",
        popularUrlPattern = "https://novlove.com/novel/page/{page}/?m_orderby=views",
        latestUrlPattern = "https://novlove.com/novel/page/{page}/?m_orderby=latest",
        
        fetchFullCoverFromDetails = false
    )
    
    // Override getChapterList for Madara AJAX
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = getDocument(novelUrl)
        
        // Madara sites often have chapters directly on the page
        val chapters = document.select("li.wp-manga-chapter a").mapIndexedNotNull { index, element ->
            val url = element.attr("href")
            val name = element.text().trim()
            if (url.isNotBlank() && name.isNotBlank()) {
                NovelChapter(
                    url = fixUrl(url),
                    name = name,
                    chapterNumber = (index + 1).toFloat()
                )
            } else null
        }
        
        // Reverse to get oldest first
        return chapters.reversed().mapIndexed { index, chapter ->
            chapter.copy(chapterNumber = (index + 1).toFloat())
        }
    }
}
