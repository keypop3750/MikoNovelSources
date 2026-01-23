package yokai.extension.novel.en.scribblehub

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class ScribblehubFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(Scribblehub())
}
