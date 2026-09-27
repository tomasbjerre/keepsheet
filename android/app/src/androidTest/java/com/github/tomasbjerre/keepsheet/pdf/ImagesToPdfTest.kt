package com.github.tomasbjerre.keepsheet.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.keepsheet.SamplePages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Exercises the real Android graphics APIs (BitmapFactory, PdfDocument)
 * this app's Import flow depends on — the parts DocumentBuilderTest fakes
 * out, since Robolectric's fidelity for real image decoding/PDF byte
 * output isn't something to rely on. Needs a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class ImagesToPdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = File(context.cacheDir, "images-to-pdf-test")
        workDir.mkdirs()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun copyImageForPage_decodesAndPersistsAJpegCopy() {
        val source = writeTestJpeg(File(workDir, "source.jpg"), Color.RED)
        val destination = File(workDir, "page-0.jpg")

        copyImageForPage(context.contentResolver, Uri.fromFile(source), destination)

        assertTrue(destination.exists())
        assertTrue(destination.length() > 0)
    }

    @Test
    fun copyImageForPage_rotates90DegreesAndSwapsWidthAndHeight() {
        // See specs/capture-and-processing.md#page-rotation (keepsheet#52). A real,
        // non-square photo — a square source can't tell a genuine 90° rotation apart from a
        // no-op, since width and height would be equal either way.
        val source = SamplePages.copyToCache(context, SamplePages.COVER)
        val unrotated = File(workDir, "unrotated.jpg")
        val rotated = File(workDir, "rotated.jpg")

        copyImageForPage(context.contentResolver, Uri.fromFile(source), unrotated, rotationDegrees = 0)
        copyImageForPage(context.contentResolver, Uri.fromFile(source), rotated, rotationDegrees = 90)

        val unrotatedBitmap = BitmapFactory.decodeFile(unrotated.absolutePath)
        val rotatedBitmap = BitmapFactory.decodeFile(rotated.absolutePath)
        assertEquals(unrotatedBitmap.width, rotatedBitmap.height)
        assertEquals(unrotatedBitmap.height, rotatedBitmap.width)
        unrotatedBitmap.recycle()
        rotatedBitmap.recycle()
    }

    @Test
    fun copyImageForPage_rotating360DegreesIsANoOpOnDimensions() {
        val source = SamplePages.copyToCache(context, SamplePages.COVER)
        val original = File(workDir, "original.jpg")
        val fullTurn = File(workDir, "full-turn.jpg")

        copyImageForPage(context.contentResolver, Uri.fromFile(source), original, rotationDegrees = 0)
        copyImageForPage(context.contentResolver, Uri.fromFile(source), fullTurn, rotationDegrees = 360)

        val originalBitmap = BitmapFactory.decodeFile(original.absolutePath)
        val fullTurnBitmap = BitmapFactory.decodeFile(fullTurn.absolutePath)
        assertEquals(originalBitmap.width, fullTurnBitmap.width)
        assertEquals(originalBitmap.height, fullTurnBitmap.height)
        originalBitmap.recycle()
        fullTurnBitmap.recycle()
    }

    @Test
    fun buildPdfFromImages_writesAOnePagePdfPerImage() {
        val page0 = writeTestJpeg(File(workDir, "page-0.jpg"), Color.RED)
        val page1 = writeTestJpeg(File(workDir, "page-1.jpg"), Color.BLUE)
        val pdfFile = File(workDir, "document.pdf")

        val sizeBytes = buildPdfFromImages(listOf(page0.absolutePath, page1.absolutePath), pdfFile)

        assertTrue(pdfFile.exists())
        assertEquals(pdfFile.length(), sizeBytes)
        // A real PDF file, not just arbitrary bytes.
        assertEquals("%PDF-", pdfFile.readBytes().copyOfRange(0, 5).decodeToString())
    }

    private fun writeTestJpeg(
        destination: File,
        color: Int,
    ): File {
        val bitmap = Bitmap.createBitmap(TEST_IMAGE_SIZE, TEST_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(destination).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        return destination
    }

    private companion object {
        const val TEST_IMAGE_SIZE = 64
    }
}
