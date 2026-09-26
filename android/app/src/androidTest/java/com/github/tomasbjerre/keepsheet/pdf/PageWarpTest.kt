package com.github.tomasbjerre.keepsheet.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** See specs/capture-and-processing.md#automatic-cropping-and-straightening. */
@RunWith(AndroidJUnit4::class)
class PageWarpTest {
    @Test
    fun cropsToTheGivenQuadrilateralAndFlattensIt() {
        val photo = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) {
            for (y in 0 until 200) photo.setPixel(x, y, if (x < 100) Color.RED else Color.BLUE)
        }
        val leftHalf = Corners(Point(0f, 0f), Point(0.5f, 0f), Point(0.5f, 1f), Point(0f, 1f))

        val page = warpToPage(photo, leftHalf)

        assertEquals(100, page.width)
        assertEquals(200, page.height)
        assertEquals(Color.RED, page.getPixel(10, 10))
        assertEquals(Color.RED, page.getPixel(90, 190))
    }

    @Test
    fun straightensATiltedQuadrilateralIntoARectangle() {
        val photo = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val tilted = Corners(Point(0.2f, 0.1f), Point(0.9f, 0.2f), Point(0.85f, 0.9f), Point(0.15f, 0.8f))

        val page = warpToPage(photo, tilted)

        val (width, height) = tilted.outputSize(200, 200)
        assertEquals(width, page.width)
        assertEquals(height, page.height)
    }
}
