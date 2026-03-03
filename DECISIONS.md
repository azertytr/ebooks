# Architecture Decision Records

## ADR-001: No External EPUB Parsing Library

**Status:** Accepted
**Date:** 2026-03-03

### Context
EPUB parsing is required. Libraries like `epublib` exist but add 300KB+ to APK size and bring transitive dependencies.

### Decision
Implement a pure-Kotlin EPUB parser using:
- `java.util.zip.ZipInputStream` (built into Android)
- `org.xmlpull.v1.XmlPullParser` (built into Android)
- `android.util.Base64` (built into Android)

### Consequences
- ✅ Zero extra dependencies
- ✅ Full control over parsing behavior
- ✅ Handles EPUB 2 and EPUB 3
- ⚠️ Does not handle all edge cases of complex EPUBs (addressed in TODO)

---

## ADR-002: WebView for EPUB Rendering

**Status:** Accepted
**Date:** 2026-03-03

### Context
EPUB chapters are HTML+CSS files. Rendering them faithfully requires understanding HTML/CSS.

### Decision
Use Android's `WebView` to render chapter HTML. Inject reader-specific CSS (colors, fonts, sizes) before loading.

### Consequences
- ✅ Faithful rendering of book formatting
- ✅ Images, tables, lists work out of the box
- ✅ CSS-based theming
- ⚠️ WebView is heavier than a custom Text renderer
- ⚠️ No built-in page-turn animation (addressed by tap zones)

---

## ADR-003: Room for Local Storage

**Status:** Accepted
**Date:** 2026-03-03

### Context
Need persistent storage for book metadata, reading progress, and bookmarks.

### Decision
Use Room (SQLite wrapper from Jetpack) as the single source of truth.

### Consequences
- ✅ Type-safe queries
- ✅ Coroutines/Flow integration
- ✅ Schema migrations supported
- ✅ Industry-standard for Android apps

---

## ADR-004: Jetpack Compose UI

**Status:** Accepted
**Date:** 2026-03-03

### Context
Modern Android UI toolkit choice.

### Decision
Use Jetpack Compose with Material Design 3. No XML layouts.

### Consequences
- ✅ Declarative, reactive UI
- ✅ Less boilerplate than XML
- ✅ Material You dynamic colors support
- ⚠️ Requires API 26+ (minSdk set accordingly)

---

## ADR-005: Coil for Image Loading

**Status:** Accepted
**Date:** 2026-03-03

### Context
Book cover images need to be loaded efficiently with caching.

### Decision
Use Coil 2.x — the standard Compose-first image loader for Android.

### Consequences
- ✅ Compose-native API
- ✅ Memory and disk caching built-in
- ✅ Coroutines-based
- Small APK footprint compared to Glide or Picasso
