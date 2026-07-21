package yokai.extension.novel.en.boxnovel

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class BoxNovelFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(BoxNovel())
}
