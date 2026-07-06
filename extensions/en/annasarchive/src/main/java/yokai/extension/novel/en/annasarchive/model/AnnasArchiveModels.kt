package yokai.extension.novel.en.annasarchive.model

/**
 * A search result from Anna's Archive.
 */
data class AnnasBookResult(
    val title: String,
    val author: String?,
    val md5: String,
    val coverUrl: String?,
    val filesize: String?,
    val format: String?,
    val language: String?,
    val year: String?,
    val url: String
)

/**
 * Detailed information about a book from Anna's Archive.
 */
data class AnnasBookDetails(
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val md5: String,
    val format: String?,
    val filesize: String?,
    val language: String?,
    val publisher: String?,
    val year: String?,
    val downloadLinks: List<AnnasDownloadLink>
)

/**
 * A download link extracted from the book details page.
 */
data class AnnasDownloadLink(
    val url: String,
    val label: String,
    val type: DownloadLinkType
)

/**
 * Type of download link.
 */
enum class DownloadLinkType {
    /** Direct download URL ending in a known file extension (e.g. .epub, .pdf) */
    DIRECT,

    /** Anna's Archive slow download with countdown timer / CAPTCHA */
    SLOW_DOWNLOAD,

    /** Member-only fast download (requires account, skipped) */
    FAST_DOWNLOAD,

    /** IPFS gateway link */
    IPFS,

    /** Partner site link (libgen, zlib, etc.) */
    PARTNER,

    /** Unknown link type */
    UNKNOWN
}
