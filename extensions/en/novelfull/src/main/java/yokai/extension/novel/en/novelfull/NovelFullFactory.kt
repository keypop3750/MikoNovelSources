package yokai.extension.novel.en.novelfull

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelFullFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelFull())
}
