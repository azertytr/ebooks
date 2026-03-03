package com.ebooks.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.FileType
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.ui.components.BookGridCard
import com.ebooks.reader.ui.components.BookListItem
import com.ebooks.reader.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMenuFor by remember { mutableStateOf<Book?>(null) }
    var searchActive by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    // Import result feedback
    LaunchedEffect(uiState.importProgress) {
        when (val state = uiState.importProgress) {
            is ImportState.Success -> {
                viewModel.resetImportState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searchActive) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onSearch = {},
                    active = true,
                    onActiveChange = { active ->
                        if (!active) {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }
                    },
                    placeholder = { Text("Search books…") },
                    leadingIcon = {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Search suggestions could go here
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "My Library",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        IconButton(onClick = {
                            viewModel.setViewMode(
                                when (uiState.viewMode) {
                                    ViewMode.LIST -> ViewMode.GRID
                                    ViewMode.GRID -> ViewMode.BOOKSHELF
                                    ViewMode.BOOKSHELF -> ViewMode.LIST
                                }
                            )
                        }) {
                            Icon(
                                when (uiState.viewMode) {
                                    ViewMode.LIST -> Icons.Default.GridView
                                    ViewMode.GRID -> Icons.Default.ViewModule
                                    ViewMode.BOOKSHELF -> Icons.Default.ViewList
                                },
                                contentDescription = "Change view"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePicker.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/pdf",
                            "text/plain",
                            "application/x-fictionbook+xml"
                        )
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Book") }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.books.isEmpty() -> {
                    EmptyLibrary(
                        onAddBook = {
                            filePicker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain"))
                        }
                    )
                }
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.GRID, ViewMode.BOOKSHELF -> {
                            val columns = if (uiState.viewMode == ViewMode.BOOKSHELF) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.books, key = { it.id }) { book ->
                                    BookGridCard(
                                        book = book,
                                        onClick = { onOpenBook(book.id) },
                                        onLongClick = { showMenuFor = book }
                                    )
                                }
                            }
                        }
                        ViewMode.LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.books, key = { it.id }) { book ->
                                    BookListItem(
                                        book = book,
                                        onClick = { onOpenBook(book.id) },
                                        onLongClick = { showMenuFor = book }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Import loading overlay
            if (uiState.importProgress is ImportState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(elevation = CardDefaults.cardElevation(8.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Importing book…")
                        }
                    }
                }
            }
        }
    }

    // ── Sort Bottom Sheet ─────────────────────────────────────────────────────
    if (showSortSheet) {
        SortSheet(
            currentSort = uiState.sortOrder,
            onSortSelected = { viewModel.setSortOrder(it); showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }

    // ── Filter Bottom Sheet ───────────────────────────────────────────────────
    if (showFilterSheet) {
        FilterSheet(
            currentStatus = uiState.filterStatus,
            currentFileType = uiState.filterFileType,
            onStatusSelected = { viewModel.setFilterStatus(it) },
            onFileTypeSelected = { viewModel.setFilterFileType(it) },
            onDismiss = { showFilterSheet = false }
        )
    }

    // ── Book Context Menu ─────────────────────────────────────────────────────
    showMenuFor?.let { book ->
        BookContextMenu(
            book = book,
            onDismiss = { showMenuFor = null },
            onOpen = { onOpenBook(book.id); showMenuFor = null },
            onMarkStatus = { status ->
                viewModel.updateReadingStatus(book.id, status)
                showMenuFor = null
            },
            onDelete = {
                viewModel.deleteBook(book)
                showMenuFor = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        val options = listOf(
            SortOrder.TITLE to "Book name",
            SortOrder.AUTHOR to "Author",
            SortOrder.DATE to "Import date",
            SortOrder.RECENT to "Recent list"
        )
        options.forEach { (sort, label) ->
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = {
                    if (sort == currentSort) {
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = androidx.compose.ui.Modifier.clickable { onSortSelected(sort) }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    currentStatus: ReadingStatus?,
    currentFileType: String?,
    onStatusSelected: (ReadingStatus?) -> Unit,
    onFileTypeSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Library filter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            Text("Reading status", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentStatus == null,
                    onClick = { onStatusSelected(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = currentStatus == ReadingStatus.UNREAD,
                    onClick = { onStatusSelected(ReadingStatus.UNREAD) },
                    label = { Text("Unread") }
                )
                FilterChip(
                    selected = currentStatus == ReadingStatus.READING,
                    onClick = { onStatusSelected(ReadingStatus.READING) },
                    label = { Text("Reading") }
                )
                FilterChip(
                    selected = currentStatus == ReadingStatus.READ,
                    onClick = { onStatusSelected(ReadingStatus.READ) },
                    label = { Text("Read") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("File type", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentFileType == null,
                    onClick = { onFileTypeSelected(null) },
                    label = { Text("All") }
                )
                listOf("epub", "pdf", "txt", "fb2").forEach { type ->
                    FilterChip(
                        selected = currentFileType == type,
                        onClick = { onFileTypeSelected(type) },
                        label = { Text(type.uppercase()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookContextMenu(
    book: Book,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMarkStatus: (ReadingStatus) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 1) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Open") },
                    leadingContent = { Icon(Icons.Default.MenuBook, null) },
                    modifier = androidx.compose.ui.Modifier.clickable(onClick = onOpen)
                )
                HorizontalDivider()
                Text("Mark as:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.values().forEach { status ->
                        FilterChip(
                            selected = book.readingStatus == status,
                            onClick = { onMarkStatus(status) },
                            label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp))
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = androidx.compose.ui.Modifier.clickable { showDeleteDialog = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete book?") },
            text = { Text("Remove \"${book.title}\" from your library?") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Your library is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add your first book by tapping the + button",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddBook) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Book")
        }
    }
}

// For the clickable modifier used in dialogs
private fun androidx.compose.ui.Modifier.clickable(onClick: () -> Unit) =
    this.then(androidx.compose.foundation.clickable { onClick() })
