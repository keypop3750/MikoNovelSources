package yokai.extension.novel.en.ranobes

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class RanobesFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(Ranobes())
}
