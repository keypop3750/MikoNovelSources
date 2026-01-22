package yokai.extension.novel.lib

import kotlinx.serialization.Serializable

/**
 * Represents a novel in search results.
 */
@Serializable
data class NovelSearchResult(
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val latestChapter: String? = null,
    val rating: Float? = null,
    val status: NovelStatus? = null
)

/**
 * Detailed information about a novel.
 */
@Serializable
data class NovelDetails(
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: NovelStatus = NovelStatus.UNKNOWN,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    val views: Int? = null,
    val alternativeTitles: List<String> = emptyList()
)

/**
 * Represents a chapter of a novel.
 */
@Serializable
data class NovelChapter(
    val name: String,
    val url: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null
)

/**
 * Publication status of a novel.
 */
@Serializable
enum class NovelStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    LICENSED,
    PUBLISHING_FINISHED,
    CANCELLED,
    ON_HIATUS
}

/**
 * Helper function to parse status from string.
 */
fun parseNovelStatus(status: String?): NovelStatus {
    if (status == null) return NovelStatus.UNKNOWN
    return when (status.lowercase().trim()) {
        "ongoing", "on-going", "on_going", "active" -> NovelStatus.ONGOING
        "completed", "complete", "done", "finished" -> NovelStatus.COMPLETED
        "hiatus", "paused", "pause", "on hiatus" -> NovelStatus.ON_HIATUS
        "dropped", "drop", "cancelled", "canceled" -> NovelStatus.CANCELLED
        "licensed" -> NovelStatus.LICENSED
        else -> NovelStatus.UNKNOWN
    }
}

/**
 * Content returned from a chapter.
 */
@Serializable
data class NovelContent(
    val content: String,
    val title: String? = null,
    val nextChapterUrl: String? = null,
    val prevChapterUrl: String? = null
)
