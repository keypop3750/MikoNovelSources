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
