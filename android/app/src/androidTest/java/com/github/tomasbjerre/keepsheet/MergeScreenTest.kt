package com.github.tomasbjerre.keepsheet

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.ui.MERGE_ACTION_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.MERGE_ROW_TEST_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * See specs/ui-flows.md#4-merge and specs/merging.md. Seeds documents directly through
 * the real repository/database (not the UI) since how they got there doesn't matter to
 * Merge — driving the whole Capture flow twice per test would only make this slower and
 * flakier for no benefit. Doesn't exercise "Add from device": that's the system document
 * picker, outside this app's process, the same reason Import's own photo picker isn't
 * driven directly either (see HomeScreenTest).
 */
@RunWith(AndroidJUnit4::class)
class MergeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mergingTwoDocumentsProducesOneWithTheirCombinedPageCount() {
        seedDocument("Alpha", pageCount = 2)
        seedDocument("Bravo", pageCount = 3)

        composeRule.onNodeWithText("Merge").performClick()
        addFromKeepSheet("Alpha", "Bravo")

        composeRule.onNodeWithTag(MERGE_ACTION_TEST_TAG).performClick()

        awaitText("5 pages", substring = true)
    }

    @Test
    fun addingTheSameDocumentTwiceIsPrevented() {
        seedDocument("Alpha", pageCount = 1)

        composeRule.onNodeWithText("Merge").performClick()
        composeRule.onNodeWithText("From KeepSheet").performClick()
        clickDialogRow("Alpha")
        clickDialogRow("Alpha")
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onAllNodesWithTag(MERGE_ROW_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun removingASourceDisablesMergeAgain() {
        seedDocument("Alpha", pageCount = 1)
        seedDocument("Bravo", pageCount = 1)

        composeRule.onNodeWithText("Merge").performClick()
        addFromKeepSheet("Alpha", "Bravo")
        composeRule.onNodeWithTag(MERGE_ACTION_TEST_TAG).assertIsEnabled()

        composeRule.onAllNodesWithContentDescription("Remove")[0].performClick()

        composeRule.onNodeWithTag(MERGE_ACTION_TEST_TAG).assertIsNotEnabled()
    }

    private fun addFromKeepSheet(vararg documentNames: String) {
        composeRule.onNodeWithText("From KeepSheet").performClick()
        documentNames.forEach { name -> clickDialogRow(name) }
        composeRule.onNodeWithText("Done").performClick()
    }

    /** The document's name shows up twice once staged (the dialog's own row for it, and
     * the running merge list underneath, still composed behind the dialog) — this scopes
     * the click to the dialog's copy. Also waits for it: the row comes from
     * DocumentRepository.observeDocuments(), a Room Flow whose first emission after
     * seedDocument() arrives asynchronously, not necessarily before the next frame. */
    private fun clickDialogRow(name: String) {
        val matcher = hasText(name) and hasAnyAncestor(isDialog())
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(matcher).performClick()
    }

    private fun seedDocument(
        name: String,
        pageCount: Int,
    ) {
        val app = composeRule.activity.application as KeepSheetApplication
        val documentsDir = File(composeRule.activity.filesDir, "documents").apply { mkdirs() }
        val pdfFile = File(documentsDir, "$name.pdf")
        val imagePaths =
            (0 until pageCount).map { index ->
                val imageFile = File(documentsDir, "$name-page-$index.jpg")
                writeTestJpeg(imageFile)
                imageFile.absolutePath
            }
        buildPdfFromImages(imagePaths, pdfFile)
        runBlocking {
            val documentId =
                app.repository.createDocument(System.currentTimeMillis(), name, pdfFile.absolutePath, DocumentSource.SCANNED)
            app.repository.finalizeDocument(documentId, pageCount, pdfFile.length())
        }
    }

    private fun writeTestJpeg(destination: File) {
        val bitmap = Bitmap.createBitmap(TEST_IMAGE_SIZE, TEST_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        FileOutputStream(destination).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
    }

    private fun awaitText(
        text: String,
        substring: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val TEST_IMAGE_SIZE = 64
    }
}
