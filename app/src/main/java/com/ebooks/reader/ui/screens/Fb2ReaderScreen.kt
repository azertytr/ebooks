package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.parser.Fb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A screen that renders FB2 (FictionBook) ebook files.
 * FB2 is an XML-based format popular in Russian-speaking countries.
 * Content is rendered as HTML in a WebView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Fb2ReaderScreen(bookId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("FB2 Book") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(bookId) {
        withContext(Dispatchers.IO) {
            try {
                val book = AppDatabase.getInstance(context).bookDao().getBookById(bookId)
                if (book == null) {
                    error = "Book not found."
                    isLoading = false
                    return@withContext
                }
                title = book.title

                val parser = Fb2Parser(context)
                val uri = Uri.parse(book.filePath)
                val fb2Book = parser.parse(uri)

                if (fb2Book == null) {
                    error = "Failed to parse FB2 file."
                    isLoading = false
                    return@withContext
                }

                webViewRef.value?.loadDataWithBaseURL(
                    null,
                    fb2Book.htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
                isLoading = false
            } catch (e: Exception) {
                error = "Error loading book: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            webViewClient = WebViewClient()
                            webViewRef.value = this
                        }
                    }
                )
            }
        }
    }
}
