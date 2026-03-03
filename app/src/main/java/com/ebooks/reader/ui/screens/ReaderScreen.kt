package com.ebooks.reader.ui.screens

import android.annotation.SuppressLint
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.ui.components.ChapterPanel
import com.ebooks.reader.ui.components.ReaderSettingsSheet
import com.ebooks.reader.viewmodel.ReaderThemeOption
import com.ebooks.reader.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModelFactory(LocalContext.current, bookId)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    val themeColors = remember(uiState.settings.themeOption) {
        when (uiState.settings.themeOption) {
            ReaderThemeOption.LIGHT -> Pair(Color.White, Color(0xFF222222))
            ReaderThemeOption.DARK -> Pair(Color(0xFF1a1a2e), Color(0xFFe0e0e0))
            ReaderThemeOption.SEPIA -> Pair(Color(0xFFF3EAD3), Color(0xFF3b2f1e))
            ReaderThemeOption.NIGHT -> Pair(Color(0xFF0d0d0d), Color(0xFFaaaaaa))
        }
    }
    val (bgColor, _) = themeColors

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        when {
            uiState.error != null -> {
                ErrorScreen(message = uiState.error!!, onBack = onBack)
            }
            uiState.book == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                // ── WebView Reader ─────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxSize()) {
                    EpubWebView(
                        html = uiState.currentChapterHtml,
                        isLoading = uiState.isChapterLoading,
                        bgColor = bgColor,
                        onScrollChanged = { pos -> viewModel.saveProgress(pos) },
                        onCenterTap = { viewModel.toggleControls() },
                        onSwipeLeft = { viewModel.nextChapter() },
                        onSwipeRight = { viewModel.previousChapter() },
                        webViewRef = webViewRef,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Chapter loading indicator
                    if (uiState.isChapterLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                        )
                    }

                    // ── Top Bar ────────────────────────────────────────────────
                    AnimatedVisibility(
                        visible = uiState.showControls,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        ReaderTopBar(
                            title = uiState.book?.title ?: "",
                            chapterTitle = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title ?: "",
                            onBack = onBack,
                            onChapters = { viewModel.toggleChapterPanel() },
                            onBookmark = { viewModel.addBookmark() },
                            onSearch = { /* TODO: in-book search */ },
                            onSettings = { viewModel.toggleSettingsPanel() }
                        )
                    }

                    // ── Bottom Bar ─────────────────────────────────────────────
                    AnimatedVisibility(
                        visible = uiState.showControls,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        ReaderBottomBar(
                            currentChapter = uiState.currentChapterIndex,
                            totalChapters = uiState.chapters.size,
                            onPrevChapter = { viewModel.previousChapter() },
                            onNextChapter = { viewModel.nextChapter() },
                            onFontDecrease = { viewModel.decreaseFontSize() },
                            onFontIncrease = { viewModel.increaseFontSize() },
                            onAutoScroll = { viewModel.toggleAutoScroll() },
                            isAutoScrolling = uiState.settings.autoScrollSpeed > 0
                        )
                    }

                    // ── Chapter Panel (side drawer) ────────────────────────────
                    AnimatedVisibility(
                        visible = uiState.showChapterPanel,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        ChapterPanel(
                            chapters = uiState.chapters,
                            currentChapterIndex = uiState.currentChapterIndex,
                            bookmarks = uiState.bookmarks,
                            onChapterSelected = { viewModel.loadChapter(it) },
                            onBookmarkSelected = { viewModel.navigateToBookmark(it) },
                            onBookmarkDeleted = { viewModel.deleteBookmark(it) },
                            onClose = { viewModel.closeAllPanels() }
                        )
                    }
                }

                // ── Settings Sheet ─────────────────────────────────────────────
                if (uiState.showSettingsPanel) {
                    ReaderSettingsSheet(
                        settings = uiState.settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onDismiss = { viewModel.closeAllPanels() }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    html: String?,
    isLoading: Boolean,
    bgColor: Color,
    onScrollChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    webViewRef: MutableState<WebView?>,
    modifier: Modifier = Modifier
) {
    var swipeStartX by remember { mutableFloatStateOf(0f) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    textZoom = 100
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // Inject scroll tracking JS
                        view.evaluateJavascript("""
                            (function() {
                                var lastScroll = 0;
                                window.addEventListener('scroll', function() {
                                    var y = Math.round(window.scrollY);
                                    if (Math.abs(y - lastScroll) > 50) {
                                        lastScroll = y;
                                        Android.onScroll(y);
                                    }
                                }, { passive: true });
                            })();
                        """.trimIndent(), null)
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onScroll(position: Int) { onScrollChanged(position) }
                }, "Android")

                webViewRef.value = this
            }
        },
        update = { webView ->
            if (html != null) {
                val bgHex = String.format("#%06X", 0xFFFFFF and bgColor.hashCode())
                webView.setBackgroundColor(
                    android.graphics.Color.parseColor(
                        when (bgColor) {
                            Color.White -> "#FFFFFF"
                            Color(0xFF1a1a2e) -> "#1a1a2e"
                            Color(0xFFF3EAD3) -> "#F3EAD3"
                            Color(0xFF0d0d0d) -> "#0d0d0d"
                            else -> "#FFFFFF"
                        }
                    )
                )
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width
                        when {
                            offset.x < w * 0.25f -> onSwipeRight()
                            offset.x > w * 0.75f -> onSwipeLeft()
                            else -> onCenterTap()
                        }
                    }
                )
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onBookmark: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onChapters) {
                Icon(Icons.Default.List, contentDescription = "Chapters")
            }
            IconButton(onClick = onBookmark) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.TextFormat, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
private fun ReaderBottomBar(
    currentChapter: Int,
    totalChapters: Int,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onFontDecrease: () -> Unit,
    onFontIncrease: () -> Unit,
    onAutoScroll: () -> Unit,
    isAutoScrolling: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column {
            // Chapter progress
            if (totalChapters > 0) {
                LinearProgressIndicator(
                    progress = { (currentChapter + 1).toFloat() / totalChapters },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous chapter
                IconButton(onClick = onPrevChapter, enabled = currentChapter > 0) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous chapter")
                }

                // Chapter info
                Text(
                    text = if (totalChapters > 0) "${currentChapter + 1} / $totalChapters" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Font size decrease
                IconButton(onClick = onFontDecrease) {
                    Icon(Icons.Default.TextDecrease, contentDescription = "Decrease font size")
                }

                // Auto-scroll toggle
                IconButton(onClick = onAutoScroll) {
                    Icon(
                        if (isAutoScrolling) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = if (isAutoScrolling) "Stop auto-scroll" else "Start auto-scroll",
                        tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Font size increase
                IconButton(onClick = onFontIncrease) {
                    Icon(Icons.Default.TextIncrease, contentDescription = "Increase font size")
                }

                // Next chapter
                IconButton(
                    onClick = onNextChapter,
                    enabled = totalChapters == 0 || currentChapter < totalChapters - 1
                ) {
                    Icon(Icons.Default.NavigateNext, contentDescription = "Next chapter")
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null,
            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Cannot open book", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Go back") }
    }
}

// ── ViewModel Factory ─────────────────────────────────────────────────────────

class ReaderViewModelFactory(
    private val context: android.content.Context,
    private val bookId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: androidx.lifecycle.viewmodel.CreationExtras
    ): T {
        val handle = SavedStateHandle(mapOf("bookId" to bookId))
        @Suppress("UNCHECKED_CAST")
        return ReaderViewModel(
            context.applicationContext as android.app.Application,
            handle
        ) as T
    }
}
