package yokai.extension.novel.en.novelsonline

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelsOnlineFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelsOnline())
}
