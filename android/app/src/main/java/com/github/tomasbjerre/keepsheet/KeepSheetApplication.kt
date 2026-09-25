package com.github.tomasbjerre.keepsheet

import android.app.Application
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.KeepSheetDatabase
import kotlinx.coroutines.runBlocking

/**
 * Manual, framework-free service locator. The app is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class KeepSheetApplication : Application() {
    lateinit var repository: DocumentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = KeepSheetDatabase.build(this)
        repository = DocumentRepository(database.documentDao(), database.pageDao())

        // See specs/data-model.md#document-lifetime: KeepSheet keeps no permanent
        // archive, so every document/page (and any not-yet-a-document capture temp
        // file) is wiped before anything — Home included — ever renders. runBlocking
        // is fine here: this runs exactly once, synchronously, before any UI exists to
        // block, and is fast (local DB rows plus a handful of file deletes).
        runBlocking { repository.clearAll() }
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
}
