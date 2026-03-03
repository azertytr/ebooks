package com.ebooks.reader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.ebooks.reader.data.db.AppDatabase
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.FileType
import com.ebooks.reader.data.db.entities.ReadingProgress
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.data.parser.EpubParser
import com.ebooks.reader.data.parser.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.bookDao()
    private val epubParser = EpubParser(context)

    // ── Book Queries ──────────────────────────────────────────────────────────

    fun getBooksByTitle(): Flow<List<Book>> = dao.getAllBooksByTitle()
    fun getBooksByAuthor(): Flow<List<Book>> = dao.getAllBooksByAuthor()
    fun getBooksByDate(): Flow<List<Book>> = dao.getAllBooksByDate()
    fun getBooksByRecent(): Flow<List<Book>> = dao.getAllBooksByRecent()
    fun getBooksByStatus(status: ReadingStatus): Flow<List<Book>> = dao.getBooksByStatus(status)
    fun getBooksByType(fileType: String): Flow<List<Book>> = dao.getBooksByType(fileType)

    suspend fun getBookById(id: String): Book? = dao.getBookById(id)

    // ── Import ────────────────────────────────────────────────────────────────

    suspend fun importBook(uri: Uri): Book? = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri) ?: return@withContext null
        val extension = fileName.substringAfterLast(".", "").lowercase()
        val fileType = FileType.fromExtension(extension) ?: return@withContext null

        // Check for existing book
        val existing = dao.getBookByPath(uri.toString())
        if (existing != null) return@withContext existing

        val fileSize = getFileSize(uri)
        val bookId = UUID.randomUUID().toString()

        return@withContext when (fileType) {
            FileType.EPUB -> importEpub(uri, bookId, fileSize)
            FileType.PDF -> importPdf(uri, bookId, fileSize, fileName)
            FileType.TXT, FileType.FB2 -> importTextBook(uri, bookId, fileSize, fileName, fileType)
            FileType.CBZ -> importCbz(uri, bookId, fileSize, fileName)
        }
    }

    private suspend fun importEpub(uri: Uri, bookId: String, fileSize: Long): Book? {
        val epubBook = epubParser.parse(uri) ?: return null
        val coverPath = epubBook.coverBytes?.let { saveCover(bookId, it) }

        val book = Book(
            id = bookId,
            title = epubBook.title,
            author = epubBook.author,
            filePath = uri.toString(),
            fileType = FileType.EPUB.extension,
            coverPath = coverPath,
            fileSize = fileSize,
            description = epubBook.description,
            publisher = epubBook.publisher,
            language = epubBook.language,
            totalChapters = epubBook.chapters.size
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importPdf(uri: Uri, bookId: String, fileSize: Long, fileName: String): Book {
        val title = fileName.removeSuffix(".pdf")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = FileType.PDF.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importTextBook(uri: Uri, bookId: String, fileSize: Long, fileName: String, fileType: FileType): Book {
        val title = fileName.removeSuffix(".${fileType.extension}")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = fileType.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    private suspend fun importCbz(uri: Uri, bookId: String, fileSize: Long, fileName: String): Book {
        val title = fileName.removeSuffix(".cbz")
        val book = Book(
            id = bookId,
            title = title,
            author = "Unknown",
            filePath = uri.toString(),
            fileType = FileType.CBZ.extension,
            fileSize = fileSize
        )
        dao.insertBook(book)
        return book
    }

    // ── Cover Management ──────────────────────────────────────────────────────

    private fun saveCover(bookId: String, bytes: ByteArray): String? = runCatching {
        val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
        val coverFile = File(coversDir, "$bookId.jpg")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching null
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        coverFile.absolutePath
    }.getOrNull()

    fun rebuildCovers() {
        // Trigger re-import of cover images for all books
        // Useful if cover files were deleted
    }

    // ── Book Updates ──────────────────────────────────────────────────────────

    suspend fun updateBook(book: Book) = dao.updateBook(book)

    suspend fun deleteBook(book: Book, deleteFile: Boolean = false) {
        dao.deleteBook(book)
        dao.deleteReadingProgress(book.id)
        dao.deleteAllBookmarks(book.id)
        // Delete cover
        book.coverPath?.let { File(it).delete() }
        if (deleteFile) {
            // Only delete if the file is in app internal storage
            val uri = Uri.parse(book.filePath)
            if (uri.scheme == "file" && book.filePath.startsWith(context.filesDir.absolutePath)) {
                File(book.filePath).delete()
            }
        }
    }

    suspend fun updateReadingStatus(bookId: String, status: ReadingStatus) =
        dao.updateReadingStatus(bookId, status)

    suspend fun updateLastRead(bookId: String) =
        dao.updateLastRead(bookId, System.currentTimeMillis())

    // ── Reading Progress ──────────────────────────────────────────────────────

    suspend fun getReadingProgress(bookId: String): ReadingProgress? =
        dao.getReadingProgress(bookId)

    suspend fun saveReadingProgress(progress: ReadingProgress) =
        dao.saveReadingProgress(progress)

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    fun getBookmarks(bookId: String): Flow<List<Bookmark>> = dao.getBookmarks(bookId)

    suspend fun addBookmark(bookmark: Bookmark) = dao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: Bookmark) = dao.deleteBookmark(bookmark)

    // ── EPUB Content ──────────────────────────────────────────────────────────

    suspend fun getChapterHtml(bookId: String, chapterHref: String, theme: ReaderTheme): String? =
        withContext(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@withContext null
            val uri = Uri.parse(book.filePath)
            epubParser.getChapterHtml(uri, chapterHref, theme)
        }

    // Accept the already-fetched Book to avoid an extra DB round-trip
    suspend fun parseEpubBook(book: Book): com.ebooks.reader.data.parser.EpubBook? =
        withContext(Dispatchers.IO) {
            epubParser.parse(Uri.parse(book.filePath))
        }

    // ── File Utilities ────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            if (sizeIndex >= 0) return cursor.getLong(sizeIndex)
        }
        return 0L
    }
}
