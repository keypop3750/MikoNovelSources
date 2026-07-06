package yokai.extension.novel.en.annasarchive

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class AnnasArchiveFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(AnnasArchive())
}
