package yokai.extension.novel.en.novelbin

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelBinFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelBin())
}
