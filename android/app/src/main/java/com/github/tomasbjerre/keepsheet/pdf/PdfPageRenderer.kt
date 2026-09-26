package com.github.tomasbjerre.keepsheet.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Renders every page of [pdfFile] as a thumbnail [Bitmap], scaled so its longer side is at
 * most [maxDimensionPx] (aspect ratio preserved) — used for Document Detail's page preview
 * (specs/ui-flows.md#5-document-detail). Works for a scanned, imported, or merged document
 * alike: by the time a [com.github.tomasbjerre.keepsheet.data.Document] exists, it's just a
 * PDF file on disk, regardless of how it was produced.
 */
fun renderPdfPageThumbnails(
    pdfFile: File,
    maxDimensionPx: Int,
): List<Bitmap> {
    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            return (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page -> page.renderThumbnail(maxDimensionPx) }
            }
        }
    }
}

/** See specs/data-model.md#document — `pageCount` is stored, not recomputed on every
 * read, so a freshly built (e.g. merged, see specs/merging.md#result) PDF needs this
 * once to learn how many pages it ended up with. */
fun countPdfPages(pdfFile: File): Int {
    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
    }
}

private fun PdfRenderer.Page.renderThumbnail(maxDimensionPx: Int): Bitmap {
    val scale = maxDimensionPx.toFloat() / maxOf(width, height)
    val bitmap =
        Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    // PDF pages can have transparent backgrounds; a page preview should still read as a
    // white sheet of paper rather than showing through to whatever's behind it.
    bitmap.eraseColor(Color.WHITE)
    render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap
}
