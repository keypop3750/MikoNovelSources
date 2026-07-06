package yokai.extension.novel.en.annasarchive

import yokai.extension.novel.en.annasarchive.api.AnnasArchiveApi
import yokai.extension.novel.en.annasarchive.download.DownloadResolver
import yokai.extension.novel.en.annasarchive.download.EpubContentExtractor
import yokai.extension.novel.lib.*
import java.io.File
import java.io.IOException

/**
 * Anna's Archive novel source for Miko.
 *
 * Anna's Archive is a shadow library that aggregates books from multiple sources.
 * This source treats each book as a "novel" with a single "chapter" (the full book).
 * The chapter name is the book title, allowing future expansion to multi-volume works.
 *
 * Search filters to EPUB format only via the ext=epub URL parameter.
 * Download flow: try direct links first, fall back to slow_download via WebViewResolver.
 * Content extraction: downloads EPUB and extracts XHTML pages into a single HTML string.
 *
 * Source ID: 6400 (Multi-language sources range)
 */
class AnnasArchive : NovelSource() {

    override val id: Long = 6400L
    override val name: String = "Anna's Archive"
    override val baseUrl: String = "https://annas-archive.gl"
    override val lang: String = "all"
    override val hasMainPage: Boolean = false
    override val isNsfw: Boolean = false
    override val versionName: String = "1.0.0"
    override val versionCode: Int = 1
    override val rateLimitMs: Long = 2000L

    private val api by lazy { AnnasArchiveApi(client) }
    private val downloadResolver = DownloadResolver()
    private val epubExtractor = EpubContentExtractor()

    override fun getCapabilities(): SourceCapabilities = SourceCapabilities(
        supportsSearch = true,
        supportedSorts = listOf("newest"),
        supportsSortDirection = false,
        supportsAuthorFilter = false,
        supportsComments = false
    )

    override suspend fun search(query: String, page: Int): List<NovelSearchResult> {
        val results = api.search(query, page)

        return results.map { book ->
            NovelSearchResult(
                title = book.title,
                url = book.url,
                coverUrl = book.coverUrl,
                author = book.author
            )
        }
    }

    override suspend fun getNovelDetails(url: String): NovelDetails {
        val details = api.getBookDetailsByUrl(url)

        return NovelDetails(
            url = url,
            title = details.title,
            author = details.author,
            coverUrl = details.coverUrl,
            description = details.description,
            genres = listOfNotNull(details.format, details.language, details.year)
                .map { it.toString() },
            tags = listOfNotNull(details.publisher).map { it.toString() },
            status = NovelStatus.COMPLETED
        )
    }

    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        // Single chapter — named after the book title, not "Chapter 1"
        // This allows future expansion for multi-volume works
        val details = api.getBookDetailsByUrl(novelUrl)

        return listOf(
            NovelChapter(
                name = details.title,
                url = novelUrl,
                chapterNumber = 1f
            )
        )
    }

    override suspend fun getChapterContent(chapterUrl: String): String {
        // 1. Fetch book details with download links
        val details = api.getBookDetailsByUrl(chapterUrl)

        // 2. Create temp directory for download
        val tempDir = File(System.getProperty("java.io.tmpdir"), "annasarchive")
        tempDir.mkdirs()

        // 3. Download the book file (direct links first, slow_download fallback)
        val downloadedFile = downloadResolver.download(
            details = details,
            client = client,
            webViewResolver = webViewResolver,
            tempDir = tempDir
        )

        // 4. Extract content based on file format
        val content = try {
            if (epubExtractor.isValidEpub(downloadedFile)) {
                epubExtractor.extractContent(downloadedFile)
            } else {
                // Check file extension for non-EPUB formats
                val ext = downloadedFile.extension.lowercase()
                when (ext) {
                    "pdf" -> {
                        // PDF not supported in reader yet
                        "<p><strong>PDF format detected.</strong> " +
                        "This book is in PDF format which is not yet supported " +
                        "in the in-app reader. The file has been downloaded to: " +
                        downloadedFile.absolutePath + "</p>"
                    }
                    "mobi", "azw3" -> {
                        "<p><strong>${ext.uppercase()} format detected.</strong> " +
                        "This format is not yet supported in the in-app reader. " +
                        "The file has been downloaded to: " +
                        downloadedFile.absolutePath + "</p>"
                    }
                    else -> {
                        // Not a valid book file — likely an HTML error page
                        throw IOException("Downloaded file is not a valid EPUB or PDF. " +
                            "File size: ${downloadedFile.length()} bytes. " +
                            "The download may have failed due to DDoS-Guard protection or " +
                            "membership requirements. Try again later.")
                    }
                }
            }
        } finally {
            // Clean up temp file after extraction
            downloadedFile.delete()
        }

        return content
    }

    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        // Anna's Archive "latest" = newest EPUB books
        return api.searchNewest(page).map { book ->
            NovelSearchResult(
                title = book.title,
                url = book.url,
                coverUrl = book.coverUrl,
                author = book.author
            )
        }
    }

    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        // Anna's Archive has no "popular" page — use newest as fallback
        return getLatestUpdates(page)
    }
}
