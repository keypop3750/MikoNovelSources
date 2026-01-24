package yokai.extension.novel.en.allnovel

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class AllNovelFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(AllNovel())
}
