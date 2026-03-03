# CLAUDE.md — EbookReader

Guidance for AI assistants working on this repository.

---

## Project overview

Android ebook reader app. Kotlin + Jetpack Compose + Material Design 3. Supports EPUB, PDF,
TXT, FB2, CBZ. All data stays on-device — no internet permission, no cloud.

- **Min SDK:** 26 (Android 8.0)
- **Compile SDK:** 34
- **Language:** Kotlin 2.0.0
- **UI:** Jetpack Compose (no XML layouts)
- **Architecture:** MVVM + Repository + Kotlin Flow

---

## Repository layout

```
.github/
  workflows/
    ci.yml            # Lint + unit tests + debug build (runs on every push/PR)
    pr-check.yml      # PR title validation + required checks before merge
    auto-merge.yml    # Enables squash auto-merge when all checks pass
    auto-release.yml  # push to main → semver tag → signed APK → GitHub Release
    release.yml       # Manual one-off release (workflow_dispatch)
    security.yml      # Weekly dependency audit + secret scan

app/src/main/java/com/ebooks/reader/
  MainActivity.kt           # Single activity. Compose NavHost ("library", "reader/{bookId}")
  data/
    db/
      AppDatabase.kt        # Room singleton, version 1, exportSchema=true
      BookDao.kt            # All queries (Flow-returning and suspend)
      Converters.kt         # Room TypeConverters (e.g. enums ↔ String)
      entities/
        Book.kt             # @Entity "books" + ReadingStatus + FileType enums
        Bookmark.kt         # @Entity "bookmarks"
        ReadingProgress.kt  # @Entity "reading_progress" (chapterIndex, scrollPosition, …)
    parser/
      EpubBook.kt           # EpubBook, EpubChapter, TocItem, ManifestItem, SpineItem
      EpubParser.kt         # Pure-Kotlin EPUB parser + ReaderTheme data class
    repository/
      BookRepository.kt     # Single source of truth. Wraps DAO + EpubParser
  ui/
    components/
      BookCard.kt
      ChapterPanel.kt
      ReaderSettingsSheet.kt
    screens/
      LibraryScreen.kt
      ReaderScreen.kt
    theme/
      Color.kt
      Theme.kt
      Type.kt
  viewmodel/
    LibraryViewModel.kt     # SortOrder, ViewMode, LibraryUiState, ImportState sealed class
    ReaderViewModel.kt

app/src/test/java/com/ebooks/reader/
  EpubParserTest.kt         # JVM unit tests for ReaderTheme
  LibraryViewModelTest.kt   # JVM unit tests for filtering/sorting logic

gradle/
  libs.versions.toml        # Version catalog — single source for all dependency versions
  wrapper/
    gradle-wrapper.jar      # Committed to repo (required for CI)
    gradle-wrapper.properties  # gradle-8.7-bin

DECISIONS.md   # Architecture Decision Records (ADR-001 through ADR-005)
TODO.md        # Prioritised backlog (🔴 critical / 🟠 important / 🟢 nice-to-have)
SECURITY.md    # Security policy
```

---

## Build commands

```bash
# Debug APK — app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Release APK (unsigned unless signing props are set)
./gradlew assembleRelease

# Unit tests
./gradlew test

# Lint (produces SARIF + HTML in app/build/reports/)
./gradlew lint

# All at once
./gradlew lint test assembleDebug
```

Gradle 8.7 is pinned via the wrapper. **Do not upgrade** without updating
`gradle-wrapper.properties` and verifying AGP compatibility.

---

## Key dependencies (from `gradle/libs.versions.toml`)

| Library | Version | Purpose |
|---------|---------|---------|
| AGP | 8.4.2 | Android Gradle Plugin |
| Kotlin | 2.0.0 | Language + compose compiler |
| KSP | 2.0.0-1.0.22 | Annotation processor for Room |
| Compose BOM | 2024.08.00 | Compose library versions |
| Room | 2.6.1 | Local SQLite database |
| Coil | 2.7.0 | Compose-native image loading |
| Navigation Compose | 2.8.0 | In-app navigation |
| Coroutines Test | 1.8.1 | Unit test utilities |

**Dependency updates go in `gradle/libs.versions.toml` only.** Never hardcode versions
in `build.gradle.kts`.

---

## Architecture conventions

### MVVM + Repository

```
UI (Screen) → ViewModel → Repository → DAO / Parser
                ↑ StateFlow/SharedFlow
```

- **ViewModels** hold `StateFlow<UiState>` and expose intent functions (`setSortOrder`, `importBook`, …).
- **Repository** is the only layer that talks to Room or the EPUB parser.
- **Screens** are stateless Compose functions — they observe ViewModel state and emit events upward.
- No direct DAO calls from UI or ViewModel.

### State modelling

- Use `data class` for UI state (e.g. `LibraryUiState`).
- Use `sealed class` for async/one-shot states (e.g. `ImportState`).
- Combine multiple flows with `combine { }` rather than nested `flatMapLatest`.
  Stay within the 5-parameter typed overload; group flows into intermediate data classes if needed
  (see `LibraryViewModel.filterState`).

### Navigation

Routes are plain strings in `MainActivity.kt`:
- `"library"` — `LibraryScreen`
- `"reader/{bookId}"` — `ReaderScreen`

Do not add a navigation graph file. Keep navigation simple and co-located in `MainActivity`.

---

## Architecture decisions (do not reverse without discussion)

| ADR | Decision |
|-----|----------|
| ADR-001 | No external EPUB library. Parser is pure Kotlin using `ZipInputStream` + `XmlPullParser`. |
| ADR-002 | EPUB chapters rendered in `WebView`. CSS injected via `ReaderTheme`. |
| ADR-003 | Room (SQLite) for all persistence — books, bookmarks, reading progress. |
| ADR-004 | Jetpack Compose only — no XML layouts. |
| ADR-005 | Coil 2 for cover image loading. |

See `DECISIONS.md` for full context and trade-offs.

---

## Room database

- **DB name:** `ebook_reader.db`
- **Version:** 1
- **Tables:** `books`, `reading_progress`, `bookmarks`
- `exportSchema = true` — generated schemas live in `app/schemas/`
- **No migration strategy exists yet.** If you bump the DB version, you must add a
  `Migration` object to `AppDatabase`. Do not use `fallbackToDestructiveMigration` in production.

---

## EPUB parser (`EpubParser.kt`)

- Handles EPUB 2 (NCX TOC) and EPUB 3 (nav document).
- Inlines images as base64 data URIs so WebView can display them without file access.
- Strips external stylesheets and injects `ReaderTheme` CSS.
- `ReaderTheme` has four presets: `LIGHT`, `DARK`, `SEPIA`, `NIGHT`.
  Add new presets in the companion object only — do not change existing color values
  without updating tests in `EpubParserTest`.
- The parser returns `null` on any error (uses `runCatching`). Callers must handle null.

---

## Testing

```bash
./gradlew test                # runs all JVM unit tests
./gradlew test --info         # verbose output
```

- Unit tests live in `app/src/test/`. They are pure JVM — no emulator needed.
- The test source still imports `android.*` indirectly through the main source tree;
  the Android SDK stubs (`android.jar`) are resolved by Gradle from `$ANDROID_HOME`.
  **All CI jobs that run Gradle now include `setup-android@v3`** to ensure the SDK is present.
- Instrumented tests (`androidTest/`) do not exist yet. Add Espresso/Compose UI tests there
  when implementing (see TODO.md).
- Do not add `@Ignore`-d tests to pass CI. Fix or delete flaky tests.

---

## CI/CD

### Workflows and triggers

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `ci.yml` | push to `main`, `develop`, `claude/**`; PR to those branches | `lint`, `test`, `build-debug` (all parallel) |
| `pr-check.yml` | PR to `main` or `develop` | `validate-title`, `required-checks` |
| `auto-merge.yml` | PR opened/updated/ready for review → `main` | `enable-auto-merge` |
| `auto-release.yml` | push to `main`; manual | `release` (tag → build → publish) |
| `release.yml` | manual `workflow_dispatch` | `build-release` |
| `security.yml` | every Monday 08:00 UTC; push to `main` | `dependency-audit`, `secret-scan` |

### PR title — conventional commits required

`pr-check.yml` enforces the conventional commits format via `amannn/action-semantic-pull-request@v5`.

Valid prefixes: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`

```
feat: add night mode toggle
fix(epub): handle missing OPF rootfile
ci: add setup-android to lint job
```

**PRs with branch-name-derived titles (e.g. "Claude/android ebook reader …") will fail.**
Always set an explicit conventional-commit title when opening a PR.

### Auto-release flow (on push to `main`)

1. `mathieudutour/github-tag-action@v6.2` reads commit messages since the last tag and bumps semver.
2. If a new tag is produced, builds a signed (or unsigned fallback) release APK.
3. Publishes a GitHub Release with the APK attached.

Bump rules:
- `feat!:` / `BREAKING CHANGE` → major
- `feat:` → minor
- `fix:`, `perf:`, `refactor:` → patch
- `chore:`, `docs:`, `ci:`, `style:` → patch

### Action versions in use

All workflows use these pinned versions — **keep them consistent**:

| Action | Version |
|--------|---------|
| `actions/checkout` | `@v6` |
| `actions/setup-java` | `@v4` |
| `actions/cache` | `@v4` |
| `actions/upload-artifact` | `@v4` |
| `android-actions/setup-android` | `@v3` |
| `github/codeql-action/upload-sarif` | `@v3` |
| `softprops/action-gh-release` | `@v2` |

Do not bump individual actions in isolation — update all occurrences at once.

---

## Commit convention

```
<type>(<optional scope>): <short description>

feat:     new user-facing feature
fix:      bug fix
docs:     documentation only
style:    formatting, no logic change
refactor: restructure without behavior change
test:     add/update tests
ci:       CI/CD configuration
chore:    build system, dependency updates
build:    build scripts
perf:     performance improvement
```

Scope examples: `epub`, `db`, `ui`, `reader`, `library`, `ci`.

---

## Security

- No secrets in source code. CI scans for patterns like `password = "..."` in `*.kt` / `*.xml`.
- Signing secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`) live in GitHub repository secrets only.
- `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` are gitignored.
- `WebView.setWebContentsDebuggingEnabled(true)` must not appear in release builds (TODO).
- File URIs from user-picked files: call `takePersistableUriPermission` to retain access
  across app restarts (currently missing — see TODO.md).

---

## Known gaps / active TODOs

These are known issues — do not paper over them with workarounds:

| Priority | Issue |
|----------|-------|
| 🔴 | `takePersistableUriPermission` not called on import — files become inaccessible after restart |
| 🔴 | WebView debugging enabled in all build types (should be debug-only) |
| 🟠 | Room has no migration strategy — bumping DB version requires adding `Migration` |
| 🟠 | PDF and TXT reader screens not yet implemented (import works, rendering is a stub) |
| 🟠 | EPUB parser not tested against malformed EPUBs |
| 🟠 | No instrumented UI tests |

See `TODO.md` for the full prioritised backlog.

---

## Common mistakes to avoid

- **Do not add dependencies outside `libs.versions.toml`.**
- **Do not use XML layouts.** All UI is Compose.
- **Do not call DAO directly from a ViewModel.** Always go through `BookRepository`.
- **Do not catch and swallow exceptions silently** in business logic — `runCatching` is used
  in the parser as a deliberate boundary; elsewhere, propagate errors to UI state.
- **Do not bump Room's DB version** without adding a corresponding `Migration`.
- **Do not commit `*.jks` / `*.keystore` files.** The `.gitignore` prevents it, but verify.
- **Do not use `setup-java@v5` or `upload-artifact@v7`** — these versions are inconsistent
  with the rest of the project and caused CI failures.
