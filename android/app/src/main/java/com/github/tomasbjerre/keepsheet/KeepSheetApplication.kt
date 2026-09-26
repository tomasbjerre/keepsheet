package com.github.tomasbjerre.keepsheet

import android.app.Application
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.KeepSheetDatabase
import com.github.tomasbjerre.keepsheet.ocr.TesseractTextRecognizer
import com.github.tomasbjerre.keepsheet.ocr.recognizeDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Manual, framework-free service locator. The app is small enough that a
 * DI framework would add more ceremony than it removes.
 */
class KeepSheetApplication : Application() {
    lateinit var repository: DocumentRepository
        private set

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * See specs/capture-and-processing.md#text-recognition-ocr: fire-and-forget after a
     * document is finalized, so finalizing never waits on OCR and leaving the screen that
     * saved it doesn't cancel it. Best-effort — failures are swallowed by [recognizeDocument].
     */
    fun startTextRecognition(documentId: Long) {
        val recognizer = TesseractTextRecognizer(this)
        backgroundScope.launch { recognizeDocument(repository, recognizer, documentId) }
    }

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
