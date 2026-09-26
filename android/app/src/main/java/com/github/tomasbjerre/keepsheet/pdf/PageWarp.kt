package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri

/**
 * Flattens the quadrilateral [corners] of [bitmap] into an upright rectangle — the
 * perspective correction of specs/capture-and-processing.md#automatic-cropping-and-straightening.
 */
fun warpToPage(
    bitmap: Bitmap,
    corners: Corners,
): Bitmap {
    val (width, height) = corners.outputSize(bitmap.width, bitmap.height)
    val source =
        corners.toList().flatMap { listOf(it.x * bitmap.width, it.y * bitmap.height) }.toFloatArray()
    val destination =
        floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
    val matrix = Matrix()
    check(matrix.setPolyToPoly(source, 0, destination, 0, POINT_COUNT)) { "Degenerate crop corners" }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.eraseColor(Color.WHITE)
    Canvas(output).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return output
}

/** Decodes [uri] scaled down so its longest side is at most [maxSide] pixels. */
fun decodeScaled(
    resolver: ContentResolver,
    uri: Uri,
    maxSide: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** Runs [detectPaperCorners] on a downscaled copy of the photo at [uri]; null if not confident. */
fun detectCornersInImage(
    resolver: ContentResolver,
    uri: Uri,
): Corners? {
    val bitmap = decodeScaled(resolver, uri, DETECTION_MAX_SIDE) ?: return null
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luminance =
            IntArray(pixels.size) {
                val p = pixels[it]
                (Color.red(p) * LUMA_R + Color.green(p) * LUMA_G + Color.blue(p) * LUMA_B) / LUMA_SCALE
            }
        return detectPaperCorners(luminance, bitmap.width, bitmap.height)
    } finally {
        bitmap.recycle()
    }
}

private const val POINT_COUNT = 4
private const val DETECTION_MAX_SIDE = 400
private const val LUMA_R = 299
private const val LUMA_G = 587
private const val LUMA_B = 114
private const val LUMA_SCALE = 1000
