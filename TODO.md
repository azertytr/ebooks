# TODO

## 🔴 Critical

- [ ] Generate proper PNG launcher icons (`mipmap-hdpi/`, `mipmap-xhdpi/`, etc.) — required for APK build
- [ ] Add `gradlew` wrapper script and `gradle-wrapper.jar` — run `gradle wrapper` locally
- [ ] Test EPUB parser against malformed/unusual EPUBs (missing OPF, non-standard paths)
- [ ] Handle `IOException` when file URI becomes invalid (moved/deleted file)
- [ ] WebView `setWebContentsDebuggingEnabled` must be disabled in release builds
- [ ] Validate URI permissions are taken with `takePersistableUriPermission` so files remain accessible across app restarts

## 🟠 Important

- [ ] Add Room database migration strategy (currently version 1 only)
- [ ] Implement in-book text search (JavaScript-based highlight in WebView)
- [ ] Add PDF rendering screen using `android.graphics.pdf.PdfRenderer`
- [ ] Add TXT reader screen (plain text with Compose `LazyColumn`)
- [ ] Implement auto-scroll (JavaScript `window.scrollBy` loop via WebView)
- [ ] Add instrumented tests (Espresso/Compose test) for UI flows
- [ ] Add cover image rebuild functionality (re-import covers from existing books)
- [ ] Support FB2 format (XML-based Russian ebook format)
- [ ] Tilt-to-scroll (using `SensorManager` accelerometer)
- [ ] Screen orientation lock per-book

## 🟢 Nice to Have

- [ ] Bookshelf view mode (3D perspective like a real bookshelf)
- [x] Reading statistics (time read per book, pages per session)
- [ ] Sleep timer for auto-scroll
- [ ] Text-to-speech integration
- [ ] Share book excerpt feature
- [ ] Cloud sync (reading progress across devices)
- [ ] OPDS catalog support (download books from servers)
- [ ] Custom fonts — user can add TTF/OTF files
- [ ] Comic book (CBZ/CBR) reader with pinch-to-zoom
- [ ] Night light / warm color filter overlay
- [ ] Widget for current reading book
- [ ] Android 13+ per-app language preferences
