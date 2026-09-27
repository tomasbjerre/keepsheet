package com.github.tomasbjerre.keepsheet.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.keepsheet.SamplePages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Runs the real on-device engine — see specs/capture-and-processing.md#text-recognition-ocr. */
@RunWith(AndroidJUnit4::class)
class TesseractTextRecognizerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun imageWithText(text: String): File {
        val bitmap = Bitmap.createBitmap(900, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint().apply {
                color = Color.BLACK
                textSize = 90f
                isAntiAlias = true
            }
        canvas.drawText(text, 30f, 120f, paint)
        val file = File(context.cacheDir, "ocr-test.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Test
    fun recognizesPrintedText() {
        val text = TesseractTextRecognizer(context).recognize(imageWithText("Invoice 2026").absolutePath)

        assertTrue("recognized: $text", text.orEmpty().contains("Invoice", ignoreCase = true))
    }

    // Real, non-flattened photo (see keepsheet#55/SamplePages) rather than the clean
    // synthetic render above: a genuine smoke test that the on-device engine copes with
    // an actual camera photo (uneven lighting, background, slight tilt), not just
    // perfect canvas-rendered text. Recognized text on real input varies by device/OCR
    // model version, so this intentionally asserts "recognized something substantial"
    // rather than an exact/specific word — see recognizesPrintedText above for exact
    // matching against controlled input.
    @Test
    fun recognizesTextInARealPhotographedPage() {
        val image = SamplePages.copyToCache(context, SamplePages.COVER)

        val text = TesseractTextRecognizer(context).recognize(image.absolutePath)

        assertTrue("recognized: $text", text.orEmpty().trim().length > MIN_REAL_PHOTO_TEXT_LENGTH)
    }

    @Test
    fun blankImageYieldsNoUsableText() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val file = File(context.cacheDir, "blank.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val text = TesseractTextRecognizer(context).recognize(file.absolutePath)

        assertEquals("", text.orEmpty().trim())
    }

    private companion object {
        const val MIN_REAL_PHOTO_TEXT_LENGTH = 10
    }
}
