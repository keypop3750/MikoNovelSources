package yokai.extension.novel.en.annasarchive.download

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Extracts text content from downloaded EPUB files.
 *
 * EPUB structure:
 * - mimetype (stored, uncompressed)
 * - META-INF/container.xml — points to the OPF package document
 * - OEBPS/content.opf (or similar) — manifest + spine listing all XHTML pages
 * - OEBPS/chapter_001.html, etc. — actual content pages
 *
 * This extractor reads all XHTML pages in spine order and concatenates their
 * body inner HTML into a single HTML string suitable for Miko's novel reader.
 */
class EpubContentExtractor {

    /**
     * Extract content from an EPUB file as a single HTML string.
     *
     * @param epubFile The downloaded EPUB file
     * @return HTML content string (concatenated body content from all pages)
     * @throws IOException if the file is not a valid EPUB
     */
    fun extractContent(epubFile: File): String {
        if (!epubFile.exists()) {
            throw IOException("EPUB file does not exist: ${epubFile.absolutePath}")
        }

        ZipFile(epubFile).use { zipFile ->
            // 1. Read META-INF/container.xml to find the OPF path
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
                ?: zipFile.getEntry("META-INF\\container.xml")
                ?: throw IOException("Invalid EPUB: missing META-INF/container.xml")

            val containerDoc = zipFile.getInputStream(containerEntry).use { stream ->
                Jsoup.parse(stream, null, "", Parser.xmlParser())
            }

            val opfPath = containerDoc.selectFirst("rootfile")
                ?.attr("full-path")
                ?: "OEBPS/content.opf"

            // 2. Read the OPF package document
            val opfEntry = zipFile.getEntry(opfPath)
                ?: zipFile.getEntry(opfPath.replace("/", "\\"))
                ?: throw IOException("Invalid EPUB: missing OPF at $opfPath")

            val opfDoc = zipFile.getInputStream(opfEntry).use { stream ->
                Jsoup.parse(stream, null, "", Parser.xmlParser())
            }

            // 3. Get all manifest items (id → href mapping)
            val manifestItems = opfDoc.select("manifest > item")
                .associateBy { it.attr("id") }
                .mapValues { it.value.attr("href") }

            val manifestMediaTypes = opfDoc.select("manifest > item")
                .associateBy { it.attr("id") }
                .mapValues { it.value.attr("media-type") }

            // 4. Get spine order (list of itemref idrefs)
            val spineIds = opfDoc.select("spine > itemref")
                .map { it.attr("idref") }
                .filter { it.isNotBlank() }

            if (spineIds.isEmpty()) {
                throw IOException("Invalid EPUB: empty spine")
            }

            // 5. Determine base path for resolving relative hrefs in the OPF
            val opfBasePath = opfPath.substringBeforeLast("/", "")

            // 6. Read each XHTML page in spine order and extract body content
            val contentBuilder = StringBuilder()
            val pathSeparator = detectPathSeparator(zipFile)

            for (id in spineIds) {
                val href = manifestItems[id] ?: continue
                val mediaType = manifestMediaTypes[id] ?: ""

                // Only process XHTML/HTML items
                if (mediaType.isNotBlank() &&
                    !mediaType.contains("xhtml", ignoreCase = true) &&
                    !mediaType.contains("html", ignoreCase = true)) {
                    continue
                }

                val entryPath = resolvePath(opfBasePath, href, pathSeparator)
                val entry = zipFile.getEntry(entryPath)
                    ?: zipFile.getEntry(entryPath.replace("/", "\\"))
                    ?: continue

                val pageDoc = zipFile.getInputStream(entry).use { stream ->
                    Jsoup.parse(stream, null, "", Parser.xmlParser())
                }

                // Extract body content
                val body = pageDoc.body()
                val bodyHtml = body.html()

                if (bodyHtml.isNotBlank()) {
                    // Add a page separator for readability between EPUB pages
                    if (contentBuilder.isNotEmpty()) {
                        contentBuilder.append("\n<hr/>\n")
                    }
                    contentBuilder.append(bodyHtml)
                }
            }

            if (contentBuilder.isEmpty()) {
                throw IOException("No readable content found in EPUB")
            }

            return contentBuilder.toString()
        }
    }

    /**
     * Check if a file is a valid EPUB by trying to open it and read the container.
     */
    fun isValidEpub(file: File): Boolean {
        return try {
            ZipFile(file).use { zipFile ->
                zipFile.getEntry("META-INF/container.xml") != null ||
                zipFile.getEntry("META-INF\\container.xml") != null
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect whether the EPUB uses forward slashes or backslashes for paths.
     */
    private fun detectPathSeparator(zipFile: ZipFile): String {
        return if (zipFile.getEntry("META-INF\\container.xml") != null) "\\" else "/"
    }

    /**
     * Resolve a relative path against a base path using the given separator.
     */
    private fun resolvePath(basePath: String, relativePath: String, separator: String): String {
        if (relativePath.startsWith(separator) || relativePath.startsWith("http")) {
            return relativePath.replace("\\", "/")
        }

        if (basePath.isBlank()) {
            return relativePath.replace("\\", "/")
        }

        // Simple path resolution: combine base + relative, handle ../
        val combined = if (basePath.endsWith(separator)) {
            basePath + relativePath
        } else {
            "$basePath$separator$relativePath"
        }

        // Normalize path separators to forward slash
        return combined.replace("\\", "/")
    }
}
