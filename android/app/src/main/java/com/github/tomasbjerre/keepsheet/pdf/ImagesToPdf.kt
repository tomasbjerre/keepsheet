package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Copies [source] into [destination] as a JPEG, decoding through
 * [resolver] — a photo picker's Uri isn't guaranteed to stay readable
 * once this session ends, so every imported page gets its own persistent
 * copy (see specs/data-model.md#page's `imagePath`).
 *
 * No crop/straighten/filter is applied yet — see
 * specs/capture-and-processing.md, still to be implemented (an
 * edge-detection library, see android/app/build.gradle.kts). The image is
 * stored as captured/imported.
 */
fun copyImageForPage(
    resolver: ContentResolver,
    source: Uri,
    destination: File,
) {
    val bitmap =
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode image at $source")
    try {
        FileOutputStream(destination).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
    } finally {
        bitmap.recycle()
    }
}

/**
 * Builds a one-page-per-image PDF at [destination] — each page sized to
 * match its source image — and returns the file's size in bytes.
 */
fun buildPdfFromImages(
    imagePaths: List<String>,
    destination: File,
): Long {
    val document = PdfDocument()
    try {
        imagePaths.forEachIndexed { index, path ->
            val bitmap = BitmapFactory.decodeFile(path) ?: error("Could not decode image at $path")
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
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
