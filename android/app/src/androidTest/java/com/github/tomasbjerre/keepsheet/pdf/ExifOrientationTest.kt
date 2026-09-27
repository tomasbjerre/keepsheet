package com.github.tomasbjerre.keepsheet.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * See specs/capture-and-processing.md#automatic-cropping-and-straightening — a photo's own
 * orientation must be applied the same way everywhere it's decoded (keepsheet#50), not left
 * for only some callers (Coil's thumbnails) to correct.
 */
@RunWith(AndroidJUnit4::class)
class ExifOrientationTest {
    private val resolver: ContentResolver =
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Test
    fun decodeScaledSwapsDimensionsForA90DegreeExifRotation() {
        val uri = writeJpegWithOrientation(WIDTH, HEIGHT, ExifInterface.ORIENTATION_ROTATE_90)

        val decoded = decodeScaled(resolver, uri, maxSide = MAX_SIDE)!!

        assertEquals(HEIGHT, decoded.width)
        assertEquals(WIDTH, decoded.height)
    }

    @Test
    fun decodeScaledKeepsDimensionsButFlipsPixelsForA180DegreeExifRotation() {
        val uri = writeJpegWithOrientation(WIDTH, HEIGHT, ExifInterface.ORIENTATION_ROTATE_180, markTopLeft = true)

        val decoded = decodeScaled(resolver, uri, maxSide = MAX_SIDE)!!

        assertEquals(WIDTH, decoded.width)
        assertEquals(HEIGHT, decoded.height)
        // The marker, stored at the top-left corner, is now upright at the bottom-right.
        assertRed(decoded.getPixel(WIDTH - MARGIN, HEIGHT - MARGIN))
        assertBlue(decoded.getPixel(MARGIN, MARGIN))
    }

    @Test
    fun decodeScaledLeavesAnUnrotatedPhotoUntouched() {
        val uri = writeJpegWithOrientation(WIDTH, HEIGHT, ExifInterface.ORIENTATION_NORMAL, markTopLeft = true)

        val decoded = decodeScaled(resolver, uri, maxSide = MAX_SIDE)!!

        assertEquals(WIDTH, decoded.width)
        assertEquals(HEIGHT, decoded.height)
        assertRed(decoded.getPixel(MARGIN, MARGIN))
    }

    // JPEG is lossy, so a compressed solid-color block isn't pixel-exact — these check
    // which channel dominates rather than requiring an exact Color.RED/Color.BLUE match.
    private fun assertRed(pixel: Int) {
        val dominant = Color.red(pixel) > Color.green(pixel) + MIN_CHANNEL_GAP && Color.red(pixel) > Color.blue(pixel) + MIN_CHANNEL_GAP
        assertTrue("expected a red-dominant pixel, was ${Integer.toHexString(pixel)}", dominant)
    }

    private fun assertBlue(pixel: Int) {
        val dominant = Color.blue(pixel) > Color.red(pixel) + MIN_CHANNEL_GAP && Color.blue(pixel) > Color.green(pixel) + MIN_CHANNEL_GAP
        assertTrue("expected a blue-dominant pixel, was ${Integer.toHexString(pixel)}", dominant)
    }

    private fun writeJpegWithOrientation(
        width: Int,
        height: Int,
        orientation: Int,
        markTopLeft: Boolean = false,
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        if (markTopLeft) {
            for (x in 0 until width / 4) {
                for (y in 0 until height / 4) bitmap.setPixel(x, y, Color.RED)
            }
        }
        val file = File.createTempFile("exif-orientation-test", ".jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private companion object {
        const val WIDTH = 80
        const val HEIGHT = 40
        const val MARGIN = 5
        const val MAX_SIDE = 1000
        const val JPEG_QUALITY = 90
        const val MIN_CHANNEL_GAP = 100
    }
}
