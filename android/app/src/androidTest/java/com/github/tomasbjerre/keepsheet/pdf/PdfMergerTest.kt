package com.github.tomasbjerre.keepsheet.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Exercises the real PdfBox-Android merge (specs/merging.md#result) — the part
 * DocumentMergerTest fakes out, since this needs a real device/emulator the same way
 * ImagesToPdfTest does for BitmapFactory/PdfDocument. `KeepSheetApplication.onCreate()`
 * has already called `PDFBoxResourceLoader.init()` by the time this test runs, since it
 * runs inside the real app process.
 */
@RunWith(AndroidJUnit4::class)
class PdfMergerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workDir: File

    @Before
    fun setUp() {
        workDir = File(context.cacheDir, "pdf-merger-test")
        workDir.mkdirs()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun mergePdfs_concatenatesEveryPageOfEverySourceInOrder() {
        val first = buildTestPdf(File(workDir, "first.pdf"), pageCount = 2, color = Color.RED)
        val second = buildTestPdf(File(workDir, "second.pdf"), pageCount = 3, color = Color.BLUE)
        val destination = File(workDir, "merged.pdf")

        val sizeBytes =
            mergePdfs(context.contentResolver, listOf(Uri.fromFile(first), Uri.fromFile(second)), destination)

        assertTrue(destination.exists())
        assertEquals(destination.length(), sizeBytes)
        assertEquals(5, countPdfPages(destination))
    }

    @Test
    fun mergePdfs_leavesTheOriginalSourceFilesUntouched() {
        // See specs/merging.md#selecting-files-and-order: "left untouched".
        val first = buildTestPdf(File(workDir, "first.pdf"), pageCount = 1, color = Color.RED)
        val second = buildTestPdf(File(workDir, "second.pdf"), pageCount = 1, color = Color.BLUE)
        val firstBytesBefore = first.readBytes()
        val secondBytesBefore = second.readBytes()

        mergePdfs(context.contentResolver, listOf(Uri.fromFile(first), Uri.fromFile(second)), File(workDir, "merged.pdf"))

        assertTrue(first.readBytes().contentEquals(firstBytesBefore))
        assertTrue(second.readBytes().contentEquals(secondBytesBefore))
    }

    private fun buildTestPdf(
        destination: File,
        pageCount: Int,
        color: Int,
    ): File {
        val imagePaths =
            (0 until pageCount).map { index ->
                val imageFile = File(workDir, "${destination.nameWithoutExtension}-page-$index.jpg")
                writeTestJpeg(imageFile, color)
                imageFile.absolutePath
            }
        buildPdfFromImages(imagePaths, destination)
        return destination
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
