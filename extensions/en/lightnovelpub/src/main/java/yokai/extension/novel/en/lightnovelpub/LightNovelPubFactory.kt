package yokai.extension.novel.en.lightnovelpub

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class LightNovelPubFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(LightNovelPub())
}
