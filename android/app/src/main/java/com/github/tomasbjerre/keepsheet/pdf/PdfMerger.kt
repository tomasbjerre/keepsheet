package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.File

/**
 * Concatenates [sources] (in the given order) into a single new PDF at [destination] —
 * see specs/merging.md#result. Each source's own pages are copied as-is, text layer
 * included, rather than rasterized/re-rendered — `android.graphics.pdf.PdfDocument`
 * alone can only draw new pages from scratch, so this needs PdfBox-Android's
 * page-level merge instead (see android/build.gradle.kts and
 * [com.tom_roush.pdfbox.android.PDFBoxResourceLoader], initialized once in
 * KeepSheetApplication).
 *
 * [sources] works for both a Uri from KeepSheet's own document list (a `file://` Uri
 * over its `pdfPath`) and one picked from device storage (a `content://` Uri from
 * `ACTION_OPEN_DOCUMENT`) — [resolver] opens either the same way.
 */
fun mergePdfs(
    resolver: ContentResolver,
    sources: List<Uri>,
    destination: File,
): Long {
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
