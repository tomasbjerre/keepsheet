package com.github.tomasbjerre.keepsheet.pdf

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * A page's user-chosen rotation in Page Review (specs/capture-and-processing.md#page-
 * rotation), applied on top of whatever orientation [applyExifOrientation] already
 * normalized the photo to. Always a right angle — free-angle rotation isn't offered — so
 * this is plain 90°-step arithmetic, not a general angle.
 */
private const val FULL_TURN = 360
private const val QUARTER_TURN = 90

/** The next rotation clockwise, wrapping from 270° back to 0°. */
fun rotatedClockwise(degrees: Int): Int = (normalizedDegrees(degrees) + QUARTER_TURN) % FULL_TURN

/** The next rotation counter-clockwise, wrapping from 0° back to 270°. */
fun rotatedCounterClockwise(degrees: Int): Int = (normalizedDegrees(degrees) - QUARTER_TURN + FULL_TURN) % FULL_TURN

/** Normalizes any integer degree value into `[0, 360)`. */
fun normalizedDegrees(degrees: Int): Int = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

/**
 * Rotates [bitmap] clockwise by [degrees] (normalized to a multiple of 90), recycling it if
 * a new bitmap was produced. A no-op (returns [bitmap] unchanged) at 0°.
 */
fun applyRotation(
    bitmap: Bitmap,
    degrees: Int,
): Bitmap {
    val normalized = normalizedDegrees(degrees)
    if (normalized == 0) return bitmap
    val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
