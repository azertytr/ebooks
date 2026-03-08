package com.ebooks.reader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.parser.EpubBook
import com.ebooks.reader.data.parser.EpubChapter
import com.ebooks.reader.data.parser.ReaderTheme
import com.ebooks.reader.data.repository.BookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReaderThemeOption { LIGHT, DARK, SEPIA, NIGHT }
enum class FontFamily(val css: String, val displayName: String) {
    SERIF("Georgia, serif", "Georgia"),
    SANS_SERIF("'Roboto', sans-serif", "Roboto"),
    MONO("'Courier New', monospace", "Mono"),
    OPENTYPE("'OpenDyslexic', serif", "Dyslexic");
}

data class ReaderSettings(
    val themeOption: ReaderThemeOption = ReaderThemeOption.LIGHT,
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val fontFamily: FontFamily = FontFamily.SERIF,
    val paragraphIndent: Boolean = false,
    val brightness: Float = -1f,  // -1 = system
    val autoScrollSpeed: Int = 0, // 0 = off, 1-10 speed
    val keepScreenOn: Boolean = false,
    val isFullscreen: Boolean = false
)

data class ReaderUiState(
    val book: Book? = null,
    val epubBook: EpubBook? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String? = null,
    val isChapterLoading: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val showControls: Boolean = true,
    val showChapterPanel: Boolean = false,
    val showSettingsPanel: Boolean = false,
    val showBookmarksPanel: Boolean = false,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val error: String? = null
)

class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val bookId: String = savedStateHandle["bookId"] ?: ""

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var autoScrollJob: Job? = null

    private val _autoScrollTick = MutableSharedFlow<Int>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val autoScrollTick: SharedFlow<Int> = _autoScrollTick.asSharedFlow()

    // Flow-based debounce for scroll progress saving — avoids creating a new Job
    // object on every scroll event (which can fire hundreds of times per second).
    private val scrollEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        if (bookId.isNotBlank()) {
            loadBook()
        }
        viewModelScope.launch {
            scrollEvents
                .debounce(1_000L)
                .collect { position -> persistProgress(position) }
        }
    }

    private fun loadBook() {
        // Single coroutine for sequential loading — avoids the race where the
        // second launch reads an empty chapters list before the first has populated it.
        viewModelScope.launch {
            val book = repository.getBookById(bookId) ?: run {
                _uiState.update { it.copy(error = "Book not found") }
                return@launch
            }
            val epubBook = repository.parseEpubBook(book)
            val progress = repository.getReadingProgress(bookId)
            val chapters = epubBook?.chapters ?: emptyList()
            val startIndex = progress?.chapterIndex?.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) ?: 0

            _uiState.update { state ->
                state.copy(
                    book = book,
                    epubBook = epubBook,
                    chapters = chapters,
                    currentChapterIndex = startIndex
                )
            }

            repository.updateLastRead(bookId)

            // Load the starting chapter now that state is populated
            if (chapters.isNotEmpty()) {
                loadChapter(startIndex)
            }
        }

        // Observe bookmarks in a separate coroutine (infinite flow — must be isolated)
        viewModelScope.launch {
            repository.getBookmarks(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    fun loadChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index < 0 || index >= chapters.size) return

        viewModelScope.launch {
            _uiState.update { it.copy(isChapterLoading = true, currentChapterIndex = index) }
            val chapter = chapters[index]
            val theme = buildReaderTheme()
            val html = repository.getChapterHtml(bookId, chapter.href, theme)
            _uiState.update { it.copy(
                currentChapterHtml = html,
                isChapterLoading = false,
                showChapterPanel = false
            )}
        }
    }

    fun nextChapter() {
        val current = _uiState.value.currentChapterIndex
        val total = _uiState.value.chapters.size
        if (current < total - 1) loadChapter(current + 1)
    }

    fun previousChapter() {
        val current = _uiState.value.currentChapterIndex
        if (current > 0) loadChapter(current - 1)
    }

    // ── Controls Visibility ───────────────────────────────────────────────────

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleChapterPanel() {
        _uiState.update { it.copy(
            showChapterPanel = !it.showChapterPanel,
            showSettingsPanel = false,
            showBookmarksPanel = false
        )}
    }

    fun toggleSettingsPanel() {
        _uiState.update { it.copy(
            showSettingsPanel = !it.showSettingsPanel,
            showChapterPanel = false,
            showBookmarksPanel = false
        )}
    }

    fun toggleBookmarksPanel() {
        _uiState.update { it.copy(
            showBookmarksPanel = !it.showBookmarksPanel,
            showChapterPanel = false,
            showSettingsPanel = false
        )}
    }

    fun closeAllPanels() {
        _uiState.update { it.copy(
            showChapterPanel = false,
            showSettingsPanel = false,
            showBookmarksPanel = false
        )}
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun updateSettings(settings: ReaderSettings) {
        val old = _uiState.value.settings
        val visualChanged = settings.themeOption != old.themeOption ||
            settings.fontSize != old.fontSize ||
            settings.lineHeight != old.lineHeight ||
            settings.fontFamily != old.fontFamily ||
            settings.paragraphIndent != old.paragraphIndent
        val speedChanged = settings.autoScrollSpeed != old.autoScrollSpeed

        _uiState.update { it.copy(settings = settings) }

        if (visualChanged) loadChapter(_uiState.value.currentChapterIndex)
        if (speedChanged) {
            if (settings.autoScrollSpeed > 0) startAutoScroll() else stopAutoScroll()
        }
    }

    fun setTheme(theme: ReaderThemeOption) {
        updateSettings(_uiState.value.settings.copy(themeOption = theme))
    }

    fun setFontSize(size: Int) {
        updateSettings(_uiState.value.settings.copy(fontSize = size.coerceIn(12, 32)))
    }

    fun increaseFontSize() = setFontSize(_uiState.value.settings.fontSize + 2)
    fun decreaseFontSize() = setFontSize(_uiState.value.settings.fontSize - 2)

    fun setFontFamily(family: FontFamily) {
        updateSettings(_uiState.value.settings.copy(fontFamily = family))
    }

    fun setLineHeight(height: Float) {
        updateSettings(_uiState.value.settings.copy(lineHeight = height.coerceIn(1.0f, 3.0f)))
    }

    fun toggleAutoScroll() {
        val current = _uiState.value.settings.autoScrollSpeed
        setAutoScrollSpeed(if (current > 0) 0 else 3)
    }

    fun setAutoScrollSpeed(speed: Int) {
        val coerced = speed.coerceIn(0, 10)
        _uiState.update { it.copy(settings = it.settings.copy(autoScrollSpeed = coerced)) }
        if (coerced > 0) startAutoScroll() else stopAutoScroll()
    }

    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = viewModelScope.launch {
            while (true) {
                delay(50L)
                val speed = _uiState.value.settings.autoScrollSpeed
                if (speed > 0) _autoScrollTick.emit(speed)
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible, searchQuery = "") }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun addBookmark(scrollPosition: Int = 0, selectedText: String? = null) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.currentChapterIndex) ?: return
        viewModelScope.launch {
            val bookmark = Bookmark(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = state.currentChapterIndex,
                chapterHref = chapter.href,
                position = scrollPosition,
                selectedText = selectedText
            )
            repository.addBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    fun navigateToBookmark(bookmark: Bookmark) {
        if (bookmark.chapterIndex != _uiState.value.currentChapterIndex) {
            loadChapter(bookmark.chapterIndex)
        }
        // Scroll position handled by WebView
    }

    // ── Progress Saving ───────────────────────────────────────────────────────

    fun saveProgress(scrollPosition: Int) {
        scrollEvents.tryEmit(scrollPosition)
    }

    private suspend fun persistProgress(scrollPosition: Int) {
        val state = _uiState.value
        repository.saveReadingProgress(
            ReadingProgress(
                bookId = bookId,
                chapterIndex = state.currentChapterIndex,
                chapterHref = state.chapters.getOrNull(state.currentChapterIndex)?.href ?: "",
                scrollPosition = scrollPosition
            )
        )
        if (state.currentChapterIndex == state.chapters.size - 1) {
            repository.updateReadingStatus(bookId, ReadingStatus.READ)
        }
    }

    // ── Theme Building ────────────────────────────────────────────────────────

    private fun buildReaderTheme(): ReaderTheme {
        val settings = _uiState.value.settings
        val base = when (settings.themeOption) {
            ReaderThemeOption.LIGHT -> ReaderTheme.LIGHT
            ReaderThemeOption.DARK -> ReaderTheme.DARK
            ReaderThemeOption.SEPIA -> ReaderTheme.SEPIA
            ReaderThemeOption.NIGHT -> ReaderTheme.NIGHT
        }
        return base.copy(
            fontSize = settings.fontSize,
            lineHeight = settings.lineHeight,
            fontFamily = settings.fontFamily.css,
            paragraphIndent = settings.paragraphIndent
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScroll()
    }
}
