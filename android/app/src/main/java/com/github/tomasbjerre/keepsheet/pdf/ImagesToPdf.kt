package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.data.PaperFormat
import java.io.File
import java.io.FileOutputStream

/**
 * Copies [source] into [destination] as a JPEG, decoding through
 * [resolver] — a photo picker's Uri isn't guaranteed to stay readable
 * once this session ends, so every imported page gets its own persistent
 * copy (see specs/data-model.md#page's `imagePath`).
 *
 * The decoded photo is oriented per its own EXIF tag before anything else touches it (see
 * [applyExifOrientation]), then [rotationDegrees] — the user's own choice in Page Review
 * (specs/capture-and-processing.md#page-rotation) — is applied on top of that. [corners]
 * were chosen against that same EXIF-then-rotated orientation in Page Review's crop preview
 * (keepsheet#50, keepsheet#52), so cropping pixels in any other orientation here would grab
 * the wrong region entirely. [corners], when given, are then flattened into an upright page
 * (specs/capture-and-processing.md#automatic-cropping-and-straightening); then [filter] is
 * applied to the pixels (specs/capture-and-processing.md#document-filters).
 */
fun copyImageForPage(
    resolver: ContentResolver,
    source: Uri,
    destination: File,
    filter: PageFilter = PageFilter.COLOR,
    rotationDegrees: Int = 0,
    corners: Corners? = null,
) {
    val decoded =
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode image at $source")
    val oriented = applyExifOrientation(decoded, resolver, source)
    val rotated = applyRotation(oriented, rotationDegrees)
    val bitmap = filtered(cropped(rotated, corners), filter)
    try {
        FileOutputStream(destination).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
    } finally {
        bitmap.recycle()
    }
}

private fun cropped(
    bitmap: Bitmap,
    corners: Corners?,
): Bitmap {
    if (corners == null) return bitmap
    val warped = warpToPage(bitmap, corners)
    bitmap.recycle()
    return warped
}

private fun filtered(
    bitmap: Bitmap,
    filter: PageFilter,
): Bitmap {
    if (filter == PageFilter.COLOR) return bitmap
    val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    bitmap.recycle()
    val pixels = IntArray(mutable.width * mutable.height)
    mutable.getPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
    applyFilter(pixels, mutable.width, mutable.height, filter)
    mutable.setPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
    return mutable
}

/**
 * Builds a one-page-per-image PDF at [destination] and returns the file's size in bytes.
 * Each page is a standard, printer-friendly [format] page (specs/capture-and-
 * processing.md#printer-friendly-pages) — not sized to whatever pixel dimensions the
 * source image happens to have — with its image scaled to fit inside a margin, preserving
 * its own aspect ratio and orientation rather than the image filling the whole page.
 */
fun buildPdfFromImages(
    imagePaths: List<String>,
    destination: File,
    format: PaperFormat = PaperFormat.A4,
): Long {
    val document = PdfDocument()
    try {
        imagePaths.forEachIndexed { index, path ->
            val bitmap = BitmapFactory.decodeFile(path) ?: error("Could not decode image at $path")
            try {
                val (pageWidth, pageHeight) = format.pageSize(bitmap.width, bitmap.height)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val fit = fitContentRect(bitmap.width, bitmap.height, pageWidth, pageHeight)
                val destRect = RectF(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height)
                page.canvas.drawBitmap(bitmap, null, destRect, null)
                document.finishPage(page)
            } finally {
                bitmap.recycle()
            }
        }
        FileOutputStream(destination).use { out -> document.writeTo(out) }
    } finally {
        document.close()
    }
    return destination.length()
}

private const val JPEG_QUALITY = 90
