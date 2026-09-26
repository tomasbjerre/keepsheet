package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.File

/**
 * Concatenates [sources] (in the given order) into a single new PDF at [destination] —
 * see specs/merging.md#result. Each source's own pages are copied as-is, text layer
 * included, rather than rasterized/re-rendered — `android.graphics.pdf.PdfDocument`
 * alone can only draw new pages from scratch, so this needs PdfBox-Android's
 * page-level merge instead (see android/build.gradle.kts).
 *
 * [sources] works for both a Uri from KeepSheet's own document list (a `file://` Uri
 * over its `pdfPath`) and one picked from device storage (a `content://` Uri from
 * `ACTION_OPEN_DOCUMENT`) — [resolver] opens either the same way.
 */
fun mergePdfs(
    context: Context,
    resolver: ContentResolver,
    sources: List<Uri>,
    destination: File,
): Long {
    ensurePdfBoxInitialized(context)
    val streams = sources.map { uri -> resolver.openInputStream(uri) ?: error("Could not open $uri") }
    try {
        val merger = PDFMergerUtility()
        streams.forEach(merger::addSource)
        merger.destinationFileName = destination.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
    } finally {
        streams.forEach { it.close() }
    }
    return destination.length()
}

// PDFBoxResourceLoader.init() unpacks bundled font/CMap assets to disk — real I/O, not
// free. Doing that unconditionally on every app launch (KeepSheetApplication.onCreate(),
// which runs once per test method under the instrumentation orchestrator) rather than
// only when a merge is actually about to happen risked exactly the kind of main-thread
// stall that shows up as an ANR — this makes every non-Merge screen/test pay nothing for
// a library they never touch, and callers other than mergePdfs() never need to think
// about ordering, since this runs at most once regardless of how many merges happen.
@Volatile
private var pdfBoxInitialized = false
private val pdfBoxInitLock = Any()

private fun ensurePdfBoxInitialized(context: Context) {
    if (pdfBoxInitialized) return
    synchronized(pdfBoxInitLock) {
        if (pdfBoxInitialized) return
        PDFBoxResourceLoader.init(context.applicationContext)
        pdfBoxInitialized = true
    }
}
