package com.ebooks.reader.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per reading session — a continuous period the user had a book open.
 * [startTime] and [endTime] are Unix milliseconds.
 * A session shorter than a few seconds (e.g., accidental opens) is discarded
 * by the repository before insertion.
 */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = Book::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ReadingSession(
    @PrimaryKey val id: String,
    val bookId: String,
    val startTime: Long,
    val endTime: Long,
    /** How many distinct chapters were visited during this session. */
    val chaptersVisited: Int = 1
)
