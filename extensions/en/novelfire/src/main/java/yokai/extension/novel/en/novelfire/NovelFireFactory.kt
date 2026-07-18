package yokai.extension.novel.en.novelfire

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelFireFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelFire())
}
