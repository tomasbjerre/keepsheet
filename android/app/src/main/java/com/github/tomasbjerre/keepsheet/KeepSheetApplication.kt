package com.github.tomasbjerre.keepsheet

import android.app.Application
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.KeepSheetDatabase

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
    }
}
