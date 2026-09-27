package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Rotates/flips [bitmap] per [uri]'s own EXIF orientation tag, recycling [bitmap] if a new
 * one was produced. A raw `BitmapFactory` decode (used throughout this package for crop
 * detection, the crop preview, and the final saved page) ignores EXIF entirely, while
 * Coil's `AsyncImage` (the thumbnail strips in CaptureScreen/PageReviewScreen) applies it
 * automatically — without this, the same photo renders upright in one place and however
 * the sensor captured it in another (keepsheet#50), and automatic cropping
 * (specs/capture-and-processing.md#automatic-cropping-and-straightening) detects edges
 * against the wrong orientation entirely.
 */
fun applyExifOrientation(
    bitmap: Bitmap,
    resolver: ContentResolver,
    uri: Uri,
): Bitmap {
    val orientation =
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = exifMatrix(orientation) ?: return bitmap
    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}

/**
 * The transform for an [ExifInterface] `TAG_ORIENTATION` value; null means "already upright."
 * Expressed as a rotation plus an optional horizontal flip — every one of the 8 EXIF
 * orientations is one of those combined (TRANSPOSE/TRANSVERSE are a rotation with a flip),
 * which keeps this simpler than one `Matrix`-building branch per orientation.
 */
fun exifMatrix(orientation: Int): Matrix? {
    val (degrees, flip) = exifRotationAndFlip(orientation) ?: return null
    return Matrix().apply {
        if (degrees != 0f) postRotate(degrees)
        if (flip) postScale(-1f, 1f)
    }
}

private fun exifRotationAndFlip(orientation: Int): Pair<Float, Boolean>? =
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90 to false
        ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180 to false
        ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270 to false
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> ROTATE_180 to true
        ExifInterface.ORIENTATION_TRANSPOSE -> ROTATE_90 to true
        ExifInterface.ORIENTATION_TRANSVERSE -> ROTATE_270 to true
        else -> null
    }

private const val ROTATE_90 = 90f
private const val ROTATE_180 = 180f
private const val ROTATE_270 = 270f
