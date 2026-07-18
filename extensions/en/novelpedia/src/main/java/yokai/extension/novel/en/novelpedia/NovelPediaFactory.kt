package yokai.extension.novel.en.novelpedia

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class NovelPediaFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(NovelPedia())
}
