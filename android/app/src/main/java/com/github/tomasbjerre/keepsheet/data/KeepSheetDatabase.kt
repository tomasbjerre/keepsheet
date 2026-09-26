package com.github.tomasbjerre.keepsheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * See specs/data-model.md#document-lifetime: no document survives a fresh app start
 * anyway, so a schema change never needs to carry old data forward — see
 * [build]'s fallbackToDestructiveMigration() below.
 */
@Database(entities = [Document::class, Page::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class KeepSheetDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun pageDao(): PageDao

    companion object {
        fun build(context: Context): KeepSheetDatabase =
            Room
                .databaseBuilder(context.applicationContext, KeepSheetDatabase::class.java, "keepsheet.db")
                // Deliberate, not a fallback-of-last-resort — see the class doc above
                // and specs/data-model.md#document-lifetime.
                .fallbackToDestructiveMigration()
                .build()
    }
}
