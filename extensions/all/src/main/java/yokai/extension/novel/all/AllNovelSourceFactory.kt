package yokai.extension.novel.all

import yokai.extension.novel.lib.NovelSource
import yokai.extension.novel.lib.NovelSourceFactory
import yokai.extension.novel.all.en.*
import yokai.extension.novel.all.id.*
import yokai.extension.novel.all.tr.*
import yokai.extension.novel.all.pt.*

/**
 * Factory that creates all novel sources provided by this extension.
 * This is the entry point discovered by Miko through manifest metadata.
 */
class AllNovelSourceFactory : NovelSourceFactory {
    
    override fun createSources(): List<NovelSource> = listOf(
        // English sources (6000-6099)
        RoyalRoad(),
        Scribblehub(),
        NovelFull(),
        NovelBin(),
        LibRead(),
        FreeWebNovel(),
        ReadFromNet(),
        AllNovel(),
        NovelsOnline(),
        MtlNovel(),
        ReadNovelFull(),
        BestLightNovel(),
        GrayCity(),
        HiraethTranslation(),
        MoreNovel(),
        WtrLab(),
        PawRead(),
        AnnasArchive(),
        
        // Indonesian sources (6100-6199)
        IndoWebNovel(),
        SakuraNovel(),
        
        // Turkish sources (6200-6299)
        KolNovel(),
        
        // Portuguese sources (6300-6399)
        MeioNovel(),
    )
}
