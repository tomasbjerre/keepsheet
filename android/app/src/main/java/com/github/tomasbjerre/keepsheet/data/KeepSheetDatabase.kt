package com.github.tomasbjerre.keepsheet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Schema version 1 — no real user has data yet, so there's nothing to
 * migrate from. The first schema change must add an explicit Migration
 * (see wisp's WispDatabase for the pattern) plus a migration test (see
 * wisp's WispDatabaseMigrationTest) — see specs/data-model.md#data-integrity-on-start
 * and AGENTS.md.
 */
@Database(entities = [Document::class, Page::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class KeepSheetDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun pageDao(): PageDao

    companion object {
        fun build(context: Context): KeepSheetDatabase =
            Room
                .databaseBuilder(context.applicationContext, KeepSheetDatabase::class.java, "keepsheet.db")
                // Safety net only, not the primary path — see
                // specs/data-model.md#data-integrity-on-start. Only catches a schema
                // version this app never actually shipped, where there's no real data
                // to preserve anyway. Once a real Migration exists (see the class doc
                // above), it always takes precedence over this.
                .fallbackToDestructiveMigration()
                .build()
    }
}
