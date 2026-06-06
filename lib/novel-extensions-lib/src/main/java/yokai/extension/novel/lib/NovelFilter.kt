package yokai.extension.novel.lib

import kotlinx.serialization.Serializable

/**
 * Base class for novel filters used in browse/search.
 */
@Serializable
sealed class NovelFilter {
    abstract val name: String
    
    /**
     * A text input filter.
     */
    @Serializable
    data class Text(
        override val name: String,
        var state: String = ""
    ) : NovelFilter()
    
    /**
     * A checkbox filter.
     */
    @Serializable
    data class CheckBox(
        override val name: String,
        var state: Boolean = false
    ) : NovelFilter()
    
    /**
     * A tri-state filter (ignore, include, exclude).
     */
    @Serializable
    data class TriState(
        override val name: String,
        var state: Int = STATE_IGNORE
    ) : NovelFilter() {
        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }
    
    /**
     * A dropdown select filter.
     */
    @Serializable
    data class Select(
        override val name: String,
        val values: List<String>,
        var state: Int = 0
    ) : NovelFilter()
    
    /**
     * A group of filters.
     */
    @Serializable
    data class Group(
        override val name: String,
        val filters: List<NovelFilter>
    ) : NovelFilter()
    
    /**
     * A separator (visual only).
     */
    @Serializable
    data class Separator(
        override val name: String = ""
    ) : NovelFilter()
    
    /**
     * A header label (visual only).
     */
    @Serializable
    data class Header(
        override val name: String
    ) : NovelFilter()
    
    /**
     * Sort options filter.
     */
    @Serializable
    data class Sort(
        override val name: String,
        val values: List<String>,
        var state: Selection? = null
    ) : NovelFilter() {
        @Serializable
        data class Selection(val index: Int, val ascending: Boolean)
    }
}

/**
 * A list of filters.
 */
typealias NovelFilterList = List<NovelFilter>

/**
 * Declares what filtering/sorting capabilities a source supports.
 * The app uses this to dynamically show only supported options.
 * 
 * Sources should override getCapabilities() to declare their capabilities.
 * Any capability not listed is assumed to be unsupported and will be hidden.
 */
@Serializable
data class SourceCapabilities(
    /**
     * List of supported sort options. Use the standard values:
     * "popular", "last_updated", "newest", "rating", "views", "trending",
     * "most_chapters", "alphabetical"
     */
    val supportedSorts: List<String> = listOf("popular"),
    
    /**
     * Whether ascending/descending sort direction is supported.
     */
    val supportsSortDirection: Boolean = true,
    
    /**
     * List of supported genre/tag query values.
     * Use lowercase_underscore format: "action", "fantasy", "litrpg", etc.
     */
    val supportedGenres: List<String> = emptyList(),
    
    /**
     * Whether genre exclusion is supported (tagsRemove).
     */
    val supportsGenreExclusion: Boolean = false,
    
    /**
     * List of supported status values: "ongoing", "completed", "hiatus", "dropped"
     */
    val supportedStatuses: List<String> = emptyList(),
    
    /**
     * List of supported content warnings that can be filtered.
     * Royal Road uses: "ai_assisted", "ai_generated", "graphic_violence", 
     * "profanity", "sensitive_content", "sexual_content"
     */
    val supportedContentWarnings: List<String> = emptyList(),
    
    /**
     * Whether content warning filters can include (show only) or exclude (hide).
     * If true, content warnings are tri-state (ignore/include/exclude).
     */
    val supportsContentWarningExclusion: Boolean = false,
    
    /**
     * Whether min/max chapter count filtering is supported.
     */
    val supportsChapterCountFilter: Boolean = false,
    
    /**
     * Whether minimum rating filter is supported.
     */
    val supportsRatingFilter: Boolean = false,
    
    /**
     * Whether keyword search is supported.
     */
    val supportsSearch: Boolean = true,
    
    /**
     * Whether author search/filter is supported.
     */
    val supportsAuthorFilter: Boolean = false,
    
    /**
     * Additional custom filter keys this source supports.
     * These allow source-specific filters beyond the standard ones.
     */
    val customFilters: List<String> = emptyList()
)
