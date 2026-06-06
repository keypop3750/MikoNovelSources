package yokai.extension.novel.en.webnovel

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class WebNovelFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(WebNovel())
}
