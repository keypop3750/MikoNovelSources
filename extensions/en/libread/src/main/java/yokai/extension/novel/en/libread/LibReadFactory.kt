package yokai.extension.novel.en.libread

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class LibReadFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(LibRead())
}
