package com.github.tomasbjerre.keepsheet.data

import androidx.room.TypeConverter

/** Room has no native enum-column support, so both enums here are stored as their name. */
class Converters {
    @TypeConverter
    fun fromDocumentSource(value: DocumentSource): String = value.name

    @TypeConverter
    fun toDocumentSource(value: String): DocumentSource = DocumentSource.valueOf(value)

    @TypeConverter
    fun fromPageFilter(value: PageFilter): String = value.name

    @TypeConverter
    fun toPageFilter(value: String): PageFilter = PageFilter.valueOf(value)
}
