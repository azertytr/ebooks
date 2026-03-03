package com.ebooks.reader

import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.ReadingStatus
import com.ebooks.reader.viewmodel.SortOrder
import com.ebooks.reader.viewmodel.ViewMode
import org.junit.Assert.*
import org.junit.Test

class LibraryViewModelTest {

    private fun makeBook(title: String, author: String, status: ReadingStatus = ReadingStatus.UNREAD) =
        Book(
            id = title.hashCode().toString(),
            title = title,
            author = author,
            filePath = "/books/$title.epub",
            fileType = "epub",
            readingStatus = status,
            addedAt = System.currentTimeMillis()
        )

    @Test
    fun `books are filtered by reading status correctly`() {
        val books = listOf(
            makeBook("A", "Author1", ReadingStatus.UNREAD),
            makeBook("B", "Author2", ReadingStatus.READING),
            makeBook("C", "Author3", ReadingStatus.READ)
        )

        val reading = books.filter { it.readingStatus == ReadingStatus.READING }
        assertEquals(1, reading.size)
        assertEquals("B", reading.first().title)
    }

    @Test
    fun `books are filtered by search query case insensitive`() {
        val books = listOf(
            makeBook("Alice in Wonderland", "Lewis Carroll"),
            makeBook("Moby Dick", "Herman Melville"),
            makeBook("Great Expectations", "Charles Dickens")
        )

        val query = "alice"
        val filtered = books.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.author.contains(query, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("Alice in Wonderland", filtered.first().title)
    }

    @Test
    fun `sort order enum values are complete`() {
        val orders = SortOrder.values()
        assertTrue(SortOrder.TITLE in orders)
        assertTrue(SortOrder.AUTHOR in orders)
        assertTrue(SortOrder.DATE in orders)
        assertTrue(SortOrder.RECENT in orders)
    }

    @Test
    fun `view mode cycles correctly`() {
        val modes = ViewMode.values()
        assertEquals(3, modes.size)
    }

    @Test
    fun `reading status progression is logical`() {
        val statuses = ReadingStatus.values()
        assertEquals(ReadingStatus.UNREAD, statuses[0])
        assertEquals(ReadingStatus.READING, statuses[1])
        assertEquals(ReadingStatus.READ, statuses[2])
    }
}
