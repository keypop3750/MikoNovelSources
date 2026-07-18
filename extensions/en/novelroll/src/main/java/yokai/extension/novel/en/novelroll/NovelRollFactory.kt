package yokai.extension.novel.en.novelroll

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelRollFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelRoll())
}
