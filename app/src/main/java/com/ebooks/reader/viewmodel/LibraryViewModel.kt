package com.ebooks.reader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.repository.BookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder { TITLE, AUTHOR, DATE, RECENT }
enum class ViewMode { LIST, GRID, BOOKSHELF }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val filterStatus: ReadingStatus? = null,
    val filterFileType: String? = null,
    val searchQuery: String = "",
    val importProgress: ImportState = ImportState.Idle
)

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Success(val book: Book) : ImportState()
    data class Error(val message: String) : ImportState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    private val _filterStatus = MutableStateFlow<ReadingStatus?>(null)
    private val _filterFileType = MutableStateFlow<String?>(null)
    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)

    // Group flows to stay within the 5-parameter typed combine() overload
    private val booksWithSort = _sortOrder.flatMapLatest { sort ->
        getBooksFlow(sort).map { books -> Pair(sort, books) }
    }
    private val filterParams = combine(_filterStatus, _filterFileType, _viewMode, _searchQuery) {
            status, fileType, viewMode, query -> arrayOf<Any?>(status, fileType, viewMode, query)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithSort,
        filterParams,
        _importState
    ) { (sort, books), fp, importState ->
        @Suppress("UNCHECKED_CAST")
        val status   = fp[0] as ReadingStatus?
        val fileType = fp[1] as String?
        val viewMode = fp[2] as ViewMode
        val query    = fp[3] as String

        val filtered = books.filter { book ->
            (status == null || book.readingStatus == status) &&
            (fileType == null || book.fileType == fileType) &&
            (query.isBlank() || book.title.contains(query, ignoreCase = true) ||
             book.author.contains(query, ignoreCase = true))
        }
        LibraryUiState(
            books = filtered,
            sortOrder = sort,
            viewMode = viewMode,
            filterStatus = status,
            filterFileType = fileType,
            searchQuery = query,
            importProgress = importState
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryUiState(isLoading = true)
    )

    private fun getBooksFlow(sort: SortOrder): Flow<List<Book>> = when (sort) {
        SortOrder.TITLE -> repository.getBooksByTitle()
        SortOrder.AUTHOR -> repository.getBooksByAuthor()
        SortOrder.DATE -> repository.getBooksByDate()
        SortOrder.RECENT -> repository.getBooksByRecent()
    }

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun setFilterStatus(status: ReadingStatus?) { _filterStatus.value = status }
    fun setFilterFileType(fileType: String?) { _filterFileType.value = fileType }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            val book = repository.importBook(uri)
            _importState.value = if (book != null) {
                ImportState.Success(book)
            } else {
                ImportState.Error("Failed to import book. Check the file format.")
            }
        }
    }

    fun resetImportState() { _importState.value = ImportState.Idle }

    fun deleteBook(book: Book, deleteFile: Boolean = false) {
        viewModelScope.launch { repository.deleteBook(book, deleteFile) }
    }

    fun updateReadingStatus(bookId: String, status: ReadingStatus) {
        viewModelScope.launch { repository.updateReadingStatus(bookId, status) }
    }
}
