package yokai.extension.novel.en.annasarchive.download

import okhttp3.OkHttpClient
import okhttp3.Request
import yokai.extension.novel.en.annasarchive.model.AnnasBookDetails
import yokai.extension.novel.en.annasarchive.model.DownloadLinkType
import yokai.extension.novel.lib.WebViewResolver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Orchestrates the download flow for Anna's Archive books.
 *
 * Strategy:
 * 1. Try direct download links first (partner sites, IPFS, direct .epub URLs)
 * 2. Fall back to slow_download links via WebViewResolver (handles countdown timers / CAPTCHA)
 */
class DownloadResolver {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val DOWNLOAD_FILE_PATTERN =
            Regex("https?://[^/]+/.*\\.(epub|pdf|mobi|azw3|fb2|djvu|txt)",
                RegexOption.IGNORE_CASE)

        private const val MAX_FILE_SIZE = 50L * 1024 * 1024
    }

    /**
     * Download a book file from Anna's Archive.
     *
     * @param details Book details with download links
     * @param client OkHttpClient for HTTP requests
     * @param webViewResolver WebView resolver for slow_download links (may be null)
     * @param tempDir Temporary directory to store the downloaded file
     * @return Downloaded file
     * @throws IOException if all download methods fail
     */
    suspend fun download(
        details: AnnasBookDetails,
        client: OkHttpClient,
        webViewResolver: WebViewResolver?,
        tempDir: File
    ): File {
        if (details.downloadLinks.isEmpty()) {
            throw IOException("No download links available for this book")
        }

        // Phase 1: Try direct links (DIRECT, IPFS, PARTNER)
        val directLinks = details.downloadLinks.filter {
            it.type == DownloadLinkType.DIRECT ||
            it.type == DownloadLinkType.IPFS ||
            it.type == DownloadLinkType.PARTNER
        }

        for (link in directLinks) {
            try {
                val file = downloadFile(link.url, client, tempDir, details.md5)
                if (file != null && file.length() > 0) return file
            } catch (_: Exception) { }
        }

        // Phase 2: Try slow_download links via WebViewResolver
        val slowLinks = details.downloadLinks.filter { it.type == DownloadLinkType.SLOW_DOWNLOAD }

        if (slowLinks.isNotEmpty() && webViewResolver != null) {
            for (link in slowLinks) {
                try {
                    val resolvedUrl = webViewResolver.resolveUrl(link.url, DOWNLOAD_FILE_PATTERN)
                    val file = downloadFile(resolvedUrl, client, tempDir, details.md5)
                    if (file != null && file.length() > 0) return file
                } catch (_: Exception) { }
            }
        }

        // Phase 3: Try unknown link types as last resort
        val unknownLinks = details.downloadLinks.filter { it.type == DownloadLinkType.UNKNOWN }
        for (link in unknownLinks) {
            try {
                val file = downloadFile(link.url, client, tempDir, details.md5)
                if (file != null && file.length() > 0) return file
            } catch (_: Exception) { }
        }

        val msg = if (slowLinks.isNotEmpty() && webViewResolver == null) {
            "All download methods failed. Slow download links are available but " +
            "WebView resolver is not configured."
        } else {
            "All download methods failed. Tried ${details.downloadLinks.size} links."
        }
        throw IOException(msg)
    }

    /**
     * Download a file from a URL to a temp directory.
     * Returns null if the download fails or the file is empty.
     */
    private suspend fun downloadFile(
        url: String,
        client: OkHttpClient,
        tempDir: File,
        md5: String
    ): File? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", "https://annas-archive.org/md5/$md5")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val contentType = response.header("Content-Type") ?: ""
                val body = response.body ?: return null

                // Determine file extension from URL or content type
                val ext = determineExtension(url, contentType)
                val outputFile = File(tempDir, "${md5}.$ext")

                tempDir.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    body.byteStream().use { input ->
                        input.copyTo(fos)
                    }
                }

                // Validate file size
                if (outputFile.length() == 0L || outputFile.length() > MAX_FILE_SIZE) {
                    outputFile.delete()
                    return null
                }

                outputFile
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determine file extension from URL path or Content-Type header.
     */
    private fun determineExtension(url: String, contentType: String): String {
        // Try URL path first
        val urlExt = Regex("\\.(epub|pdf|mobi|azw3|fb2|djvu|txt)$",
            RegexOption.IGNORE_CASE).find(url)?.value?.substring(1)
        if (urlExt != null) return urlExt.lowercase()

        // Fall back to content type
        return when {
            contentType.contains("epub", ignoreCase = true) ||
            contentType.contains("application/epub", ignoreCase = true) -> "epub"
            contentType.contains("pdf", ignoreCase = true) -> "pdf"
            contentType.contains("mobi", ignoreCase = true) -> "mobi"
            contentType.contains("octet-stream", ignoreCase = true) -> "epub"
            else -> "epub" // Default to epub since we filter for it
        }
    }
}
