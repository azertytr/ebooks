package com.ebooks.reader.data.db

import androidx.room.TypeConverter
import com.ebooks.reader.data.db.entities.ReadingStatus

class Converters {
    @TypeConverter
    fun fromReadingStatus(value: ReadingStatus): String = value.name

    @TypeConverter
    fun toReadingStatus(value: String): ReadingStatus =
        ReadingStatus.valueOf(value)
}
