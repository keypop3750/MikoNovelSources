package yokai.extension.novel.en.readnovelfull

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class ReadNovelFullFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(ReadNovelFull())
}
