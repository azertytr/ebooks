package com.ebooks.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.db.entities.Bookmark
import com.ebooks.reader.data.db.entities.ReadingProgress

@Database(
    entities = [Book::class, ReadingProgress::class, Bookmark::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebook_reader.db"
                )
                    // When schema changes without a migration path, wipe and rebuild the
                    // database rather than crashing.  Reading progress is re-seeded on next
                    // launch; users simply start fresh — acceptable at version 1.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
