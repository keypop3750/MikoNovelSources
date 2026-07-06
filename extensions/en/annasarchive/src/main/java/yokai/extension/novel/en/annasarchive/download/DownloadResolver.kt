package yokai.extension.novel.en.annasarchive.download

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
 * 1. Try LibGen.li partner links (two-step: fetch ads page → extract get.php link → download)
 * 2. Try direct download links (partner sites, IPFS, direct .epub URLs)
 * 3. Fall back to slow_download links via WebViewResolver (handles countdown timers / CAPTCHA)
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

        // EPUB/ZIP files start with "PK" (0x50 0x4B)
        private val EPUB_MAGIC = byteArrayOf(0x50, 0x4B)
        // PDF files start with "%PDF"
        private val PDF_MAGIC = "%PDF".toByteArray()
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

        // Phase 1: Try LibGen.li partner links (two-step download)
        val libgenLinks = details.downloadLinks.filter {
            it.type == DownloadLinkType.PARTNER && it.url.contains("libgen.li")
        }

        for (link in libgenLinks) {
            try {
                val file = downloadFromLibgen(link.url, client, tempDir, details.md5)
                if (file != null && file.length() > 0 && isValidBookFile(file)) return file
                file?.delete()
            } catch (_: Exception) { }
        }

        // Phase 2: Try direct links (DIRECT, IPFS, PARTNER excluding libgen.li)
        val directLinks = details.downloadLinks.filter {
            it.type == DownloadLinkType.DIRECT ||
            it.type == DownloadLinkType.IPFS ||
            (it.type == DownloadLinkType.PARTNER && !it.url.contains("libgen.li"))
        }

        for (link in directLinks) {
            try {
                // Skip fake IPFS links (just the website, not actual content)
                if (link.url == "https://ipfs.tech/" || link.url == "https://ipfs.tech") continue

                val file = downloadFile(link.url, client, tempDir, details.md5)
                if (file != null && file.length() > 0 && isValidBookFile(file)) return file
                file?.delete()
            } catch (_: Exception) { }
        }

        // Phase 3: Try slow_download links via WebViewResolver
        val slowLinks = details.downloadLinks.filter { it.type == DownloadLinkType.SLOW_DOWNLOAD }

        if (slowLinks.isNotEmpty() && webViewResolver != null) {
            for (link in slowLinks) {
                try {
                    val resolvedUrl = webViewResolver.resolveUrl(link.url, DOWNLOAD_FILE_PATTERN)
                    val file = downloadFile(resolvedUrl, client, tempDir, details.md5)
                    if (file != null && file.length() > 0 && isValidBookFile(file)) return file
                    file?.delete()
                } catch (_: Exception) { }
            }
        }

        // Phase 4: Try unknown link types as last resort
        val unknownLinks = details.downloadLinks.filter { it.type == DownloadLinkType.UNKNOWN }
        for (link in unknownLinks) {
            try {
                val file = downloadFile(link.url, client, tempDir, details.md5)
                if (file != null && file.length() > 0 && isValidBookFile(file)) return file
                file?.delete()
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
     * Two-step LibGen.li download:
     * 1. Fetch the ads.php page to get a download key
     * 2. Use the get.php link to download the actual file
     */
    private suspend fun downloadFromLibgen(
        url: String,
        client: OkHttpClient,
        tempDir: File,
        md5: String
    ): File? {
        // If the URL is already a get.php link, download directly
        if (url.contains("get.php")) {
            return downloadFile(url, client, tempDir, md5)
        }

        // Step 1: Fetch the ads.php or file.php page
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val html = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }
        } catch (_: Exception) {
            return null
        }

        // Step 2: Extract the get.php download link
        val doc = Jsoup.parse(html, url)
        var getLink = doc.selectFirst("a[href*=get.php]")?.let { elem ->
            elem.absUrl("href").ifBlank { elem.attr("href") }
        }

        // If no get.php link, try following ads.php link (file.php → ads.php → get.php)
        if (getLink == null) {
            val adsLink = doc.selectFirst("a[href*=ads.php]")?.let { elem ->
                elem.absUrl("href").ifBlank { elem.attr("href") }
            }
            if (adsLink != null) {
                return downloadFromLibgen(adsLink, client, tempDir, md5)
            }
            return null
        }

        val fullUrl = if (getLink.startsWith("http")) getLink else {
            // Extract base URL: https://libgen.li from https://libgen.li/ads.php?...
            val baseUrl = Regex("(https?://[^/]+)").find(url)?.value ?: "https://libgen.li"
            if (getLink.startsWith("/")) "$baseUrl$getLink" else "$baseUrl/$getLink"
        }

        // Step 3: Download the actual file
        return downloadFile(fullUrl, client, tempDir, md5, referer = url)
    }

    /**
     * Download a file from a URL to a temp directory.
     * Returns null if the download fails or the file is empty.
     */
    private suspend fun downloadFile(
        url: String,
        client: OkHttpClient,
        tempDir: File,
        md5: String,
        referer: String? = null
    ): File? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .apply {
                referer?.let { header("Referer", it) }
                if (referer == null) {
                    header("Referer", "https://annas-archive.gl/md5/$md5")
                }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val contentType = response.header("Content-Type") ?: ""
                val body = response.body ?: return null

                // Reject HTML responses — we only want actual book files
                if (contentType.contains("text/html", ignoreCase = true)) return null

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
     * Check if a downloaded file is a valid book file (EPUB or PDF).
     * This prevents treating HTML error pages as book files.
     */
    private fun isValidBookFile(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            val header = ByteArray(5)
            java.io.FileInputStream(file).use { fis ->
                fis.read(header)
            }
            // Check for EPUB/ZIP magic bytes (PK)
            header[0] == EPUB_MAGIC[0] && header[1] == EPUB_MAGIC[1] ||
            // Check for PDF magic bytes (%PDF)
            String(header, 0, 4) == "%PDF"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Determine file extension from URL path or Content-Type header.
     */
    private fun determineExtension(url: String, contentType: String): String {
        // Try URL path first
        val urlExt = Regex("\\.(epub|pdf|mobi|azw3|fb2|djvu|txt)",
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
