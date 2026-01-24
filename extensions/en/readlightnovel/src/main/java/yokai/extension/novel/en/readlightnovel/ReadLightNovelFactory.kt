package yokai.extension.novel.en.readlightnovel

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class ReadLightNovelFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(ReadLightNovel())
}
