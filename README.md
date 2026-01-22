w# MikoNovelSources

Official novel extension repository for Miko (Yōkai) manga/novel reader.

## About

This repository contains novel source extensions that can be installed in Miko to read novels from various websites. Extensions are distributed as APK files that are dynamically loaded by the app.

## Available Sources

### English (en)
| Source | Version | Status |
|--------|---------|--------|
| Royal Road | 1.0.0 | ✅ Active |
| Scribblehub | 1.0.0 | ✅ Active |
| NovelFull | 1.0.0 | ✅ Active |
| NovelBin | 1.0.0 | ✅ Active |
| LibRead | 1.0.0 | ✅ Active |
| FreeWebNovel | 1.0.0 | ✅ Active |
| ReadFromNet | 1.0.0 | ✅ Active |
| AllNovel | 1.0.0 | ✅ Active |
| NovelsOnline | 1.0.0 | ✅ Active |
| MtlNovel | 1.0.0 | ✅ Active |
| ReadNovelFull | 1.0.0 | ✅ Active |
| BestLightNovel | 1.0.0 | ✅ Active |
| GrayCity | 1.0.0 | ✅ Active |
| HiraethTranslation | 1.0.0 | ✅ Active |
| MoreNovel | 1.0.0 | ✅ Active |
| WtrLab | 1.0.0 | ✅ Active |
| PawRead | 1.0.0 | ✅ Active |
| Anna's Archive | 1.0.0 | ✅ Active |

### Multi-language
| Source | Languages | Version | Status |
|--------|-----------|---------|--------|
| IndoWebNovel | id | 1.0.0 | ✅ Active |
| SakuraNovel | id | 1.0.0 | ✅ Active |
| KolNovel | tr | 1.0.0 | ✅ Active |
| MeioNovel | pt-BR | 1.0.0 | ✅ Active |

## Installation

1. Open Miko app
2. Go to Browse → Extensions → Extension Repos
3. Add this repository URL: `https://raw.githubusercontent.com/keypop3750/MikoNovelSources/main`
4. Browse available novel extensions and install

## Building from Source

```bash
# Clone the repository
git clone https://github.com/keypop3750/MikoNovelSources.git
cd MikoNovelSources

# Build all extensions
./gradlew assembleRelease

# Build specific extension
./gradlew :extensions:all:assembleRelease
```

## Extension Development

### Structure
```
MikoNovelSources/
├── lib/                          # Shared extension library
│   └── novel-extensions-lib/     # NovelSource base classes
├── extensions/                   # Individual extensions
│   └── all/                      # All-in-one extension package
│       └── src/main/java/
│           └── yokai/extension/novel/
│               └── en/           # English sources
│               └── id/           # Indonesian sources
│               └── tr/           # Turkish sources
│               └── pt/           # Portuguese sources
├── index.min.json               # Extension manifest
└── apk/                         # Built APK files
```

### Creating a New Source

1. Create a new class extending `NovelSource`
2. Implement required methods: `search()`, `getNovelDetails()`, `getChapterList()`, `getChapterContent()`
3. Register in `NovelSourceFactory`
4. Build and test

## Credits

- Original providers ported from [QuickNovel](https://github.com/LagradOst/QuickNovel)
- Extension architecture inspired by [Tachiyomi Extensions](https://github.com/tachiyomiorg/extensions)

## License

Apache License 2.0
