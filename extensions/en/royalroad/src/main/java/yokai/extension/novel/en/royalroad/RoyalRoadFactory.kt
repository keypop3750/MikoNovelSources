package yokai.extension.novel.en.royalroad

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory

class RoyalRoadFactory : NovelSourceFactory {
    override fun createSources(): List<NovelSource> = listOf(RoyalRoad())
}
