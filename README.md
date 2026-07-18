# MikoNovelSources

Official novel extension repository for Miko (Yōkai) manga/novel reader.

## Extension Repo URL

Copy this URL and paste it into **Browse → Extensions → Extension Repos** in the app:

```
https://raw.githubusercontent.com/keypop3750/MikoNovelSources/main/index.min.json
```

## About

This repository contains novel source extensions that can be installed in Miko to read novels from various websites. Extensions are distributed as APK files that are dynamically loaded by the app.

## Available Sources

### English (en)
| Source | Version | Comments | Status |
|--------|---------|----------|--------|
| Royal Road | 1.0.1 | — | ✅ Active |
| Scribblehub | 1.0.2 | — | ✅ Active |
| NovelFull | 1.0.4 | — | ✅ Active |
| NovelBin | 1.0.4 | — | ✅ Active |
| WebNovel | 1.0.1 | — | ✅ Active |
| NovelsOnline | 1.0.1 | — | ✅ Active |
| LibRead | 1.0.3 | — | ✅ Active |
| ReadNovelFull | 1.0.2 | — | ✅ Active |
| FreeWebNovel | 1.0.3 | — | ✅ Active |
| Ranobes | 1.0.0 | ✅ | ✅ Active |
| LightNovelPub | 1.0.0 | ✅ | ✅ Active |
| NovelFire | 1.0.0 | ✅ | ✅ Active |
| NovelRoll | 1.0.0 | — | ✅ Active |
| NovelPedia | 1.0.0 | ✅ | ✅ Active |

### Multi-language
| Source | Language | Version | Status |
|--------|----------|---------|--------|
| Anna's Archive | all | 1.0.9 | ✅ Active |

### Features

- **Chapter comments** — Sources marked with ✅ in the Comments column support reading chapter comments. The reader app shows a comments button in the bottom bar for these sources.
- **Chapter dates** — All sources parse chapter release dates when available from the source website.
- **Search** — All sources support text search; popular/latest browsing is supported where the site provides it.

## Installation

1. Open Miko app
2. Go to Browse → Extensions → Extension Repos
3. Add this repository URL: `https://raw.githubusercontent.com/keypop3750/MikoNovelSources/main/index.min.json`
4. Browse available novel extensions and install

## Building from Source

```bash
# Clone the repository
git clone https://github.com/keypop3750/MikoNovelSources.git
cd MikoNovelSources

# Build all extensions
./gradlew assembleRelease

# Build a specific extension
./gradlew :extensions:en:ranobes:assembleDebug

# Build and install on a connected device
./gradlew :extensions:en:ranobes:installDebug
```

## Extension Development

### Structure
```
MikoNovelSources/
├── lib/                          # Shared extension library
│   └── novel-extensions-lib/     # NovelSource base classes
├── extensions/                   # Individual extensions
│   └── en/                       # English sources
│       ├── royalroad/
│       ├── scribblehub/
│       ├── novelfull/
│       ├── novelbin/
│       ├── webnovel/
│       ├── novelsonline/
│       ├── libread/
│       ├── readnovelfull/
│       ├── freewebnovel/
│       ├── ranobes/
│       ├── lightnovelpub/
│       ├── novelfire/
│       ├── novelroll/
│       ├── novelpedia/
│       └── annasarchive/
├── index.min.json               # Extension manifest
└── apk/                         # Built APK files
```

### Creating a New Source

1. Create a new package under `extensions/en/<sourcename>/`
2. Create a source class extending `NovelSource`
3. Implement required methods: `search()`, `getNovelDetails()`, `getChapterList()`, `getChapterContent()`
4. Create a `*Factory.kt` class extending `NovelSourceFactory` to register the source
5. Add a `build.gradle` with the extension's package name and version
6. Add the extension entry to `index.min.json`
7. Build and test

### Optional Capabilities

Sources can declare optional capabilities by overriding `getCapabilities()`:

- `supportsComments` — enables chapter comments; implement `getChapterComments()` to parse comments from the chapter page
- `supportedSorts` — declares which sort options the source supports for browsing
- `supportsSearch` — whether the source supports text search

## Credits

- Original providers ported from [QuickNovel](https://github.com/LagradOst/QuickNovel)
- Extension architecture inspired by [Tachiyomi Extensions](https://github.com/tachiyomiorg/extensions)

## License

Apache License 2.0
