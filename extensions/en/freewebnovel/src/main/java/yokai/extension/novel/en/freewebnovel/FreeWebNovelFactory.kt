package yokai.extension.novel.en.freewebnovel

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class FreeWebNovelFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(FreeWebNovel())
}
