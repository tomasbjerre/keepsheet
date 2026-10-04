package com.github.tomasbjerre.keepsheet

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.github.tomasbjerre.keepsheet.ui.DOCUMENT_NAME_FIELD_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end: scans a page, saves it, and exercises Document Detail (see
 * specs/ui-flows.md#5-document-detail) — page preview (real PdfRenderer output, not a
 * mock), rename, delete, and Save (keepsheet#88). Share isn't exercised here since it
 * hands off to the system share sheet, outside this app's process — Save is, since
 * (unlike Share) its correctness is about what ends up written to the picked
 * destination, which this test owns and can actually inspect.
 */
@RunWith(AndroidJUnit4::class)
class DocumentDetailScreenTest {
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    @Test
    fun savingADocumentOpensDetailWithARenderedPageAndSupportsRenameAndDelete() {
        scanAndSaveOnePage()

        // Landed on Document Detail directly (specs/ui-flows.md#3-page-review: Save
        // navigates there) with a real rendered page preview, not a placeholder.
        composeRule.onNodeWithText("1 pages", substring = true).assertExists()
        awaitPagePreviewCount(1)

        renameTo("My Renamed Document")

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this document?").assertExists()
        composeRule.onNodeWithText("Delete").performClick()

        awaitText("No documents yet — tap Scan to create your first PDF.")
    }

    @Test
    fun renamingToAnExistingNameAppendsANumericSuffix() {
        scanAndSaveOnePage()
        renameTo("Shared Name")
        pressBackToHome()

        scanAndSaveOnePage()
        renameTo("Shared Name", expectedName = "Shared Name (2)")
    }

    /** See specs/ui-flows.md#5-document-detail: the suggested name OCR applies in the background
     * (here forced, so the timing isn't left to how fast the emulator's OCR happens to be) must
     * not replace a name that is being typed. */
    @Test
    fun aSuggestedNameArrivingWhileTypingDoesNotReplaceWhatWasTyped() {
        scanAndSaveOnePage()
        composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performTextReplacement("Typed by hand")

        val nameBefore = persistedDocumentNames().single()
        runBlocking {
            val app = composeRule.activity.application as KeepSheetApplication
            val document =
                app.repository
                    .observeDocuments()
                    .first()
                    .single()
            app.repository.applySuggestedName(document.id, "Suggested by OCR")
        }
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { persistedDocumentNames().single() != nameBefore }

        // Give the new name time to reach the screen (it arrives through an asynchronous
        // database flow), then check the field still holds what was typed.
        repeat(SETTLE_CHECKS) {
            Thread.sleep(SETTLE_CHECK_MILLIS)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).assertTextEquals("Typed by hand")
        }

        composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performImeAction()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { persistedDocumentNames().single() == "Typed by hand" }
    }

    /** keepsheet#88: the picked destination — anywhere the platform's document picker can
     * reach, including removable/SD-card storage, which this test can't actually attach, so
     * it stands in for "a location the user chose" with a plain cache file — receives
     * exactly the saved document's own PDF bytes, every time Save is tapped (not just once),
     * confirming the picker is launched fresh rather than reusing a stale destination. */
    @Test
    fun saveWritesTheDocumentsPdfToEachChosenLocation() {
        scanAndSaveOnePage()

        stubSavePickerWith("first-save-destination.pdf")
        composeRule.onNodeWithContentDescription("Save").performClick()
        awaitText("Saved.")
        assertSavedPdfMatches("first-save-destination.pdf")

        stubSavePickerWith("second-save-destination.pdf")
        composeRule.onNodeWithContentDescription("Save").performClick()
        awaitText("Saved.")
        assertSavedPdfMatches("second-save-destination.pdf")
    }

    /** keepsheet#97: a name typed into the field but not yet committed — that commit is
     * asynchronous — must still be what Save suggests as the filename, not the stale
     * `document.name` from before the edit. Tapping Save directly, with no intervening
     * IME-Done/blur, is exactly the sequence that used to race the commit and lose. */
    @Test
    fun saveSuggestsTheNameCurrentlyInTheFieldEvenWhenNotYetCommitted() {
        scanAndSaveOnePage()

        stubSavePickerWith("wherever-the-user-picks.pdf")
        composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performTextReplacement("Brand New Name")
        composeRule.onNodeWithContentDescription("Save").performClick()

        Intents.intended(
            allOf(
                hasAction(Intent.ACTION_CREATE_DOCUMENT),
                hasExtra(Intent.EXTRA_TITLE, "Brand New Name.pdf"),
            ),
        )
    }

    private fun stubSavePickerWith(destinationFileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(context.cacheDir, destinationFileName)
        val resultData = Intent().setData(Uri.fromFile(destination))
        Intents
            .intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultData))
    }

    private fun assertSavedPdfMatches(destinationFileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(context.cacheDir, destinationFileName)
        assertArrayEquals(File(persistedPdfPath()).readBytes(), destination.readBytes())
    }

    private fun persistedPdfPath(): String =
        runBlocking {
            val app = composeRule.activity.application as KeepSheetApplication
            app.repository
                .observeDocuments()
                .first()
                .single()
                .pdfPath
        }

    private fun scanAndSaveOnePage() {
        composeRule.onNodeWithText("Scan").performClick()
        awaitShutterEnabled()
        composeRule.onNodeWithContentDescription("Shutter").performClick()
        awaitThumbnailCount(1)
        composeRule.onNodeWithText("Done").performClick()
        awaitSaveEnabled()
        composeRule.onNodeWithText("Save").performClick()
        awaitDocumentDetailLoaded()
    }

    /** Building the PDF (Save) and Document Detail's own initial load (observing the
     * document, then rendering its first page preview) are both async — nothing here is
     * ready to interact with until the name field itself exists. */
    private fun awaitDocumentDetailLoaded() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Types [name] into the name field, commits it via the keyboard's Done action (which
     * triggers the actual save — see DocumentDetailScreen's DocumentNameField), and waits
     * for the result — [name], or [expectedName] when it gets de-duplicated — to be
     * persisted. Checks the database rather than the field: the field shows what was typed
     * whether or not it ever got saved.
     *
     * Retried because the OCR-suggested name (KeepSheetApplication, applied in the background
     * after Save) reseeds the field when it lands, wiping what was just typed before it is
     * committed — a race a slow emulator loses often enough to matter. Once the user's own
     * rename is persisted, a suggested name never overwrites it. */
    private fun renameTo(
        name: String,
        expectedName: String = name,
    ) {
        composeRule.actUntil(
            action = {
                composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performTextReplacement(name)
                composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performImeAction()
            },
            done = { persistedDocumentNames().contains(expectedName) },
        )
        awaitText(expectedName)
    }

    private fun persistedDocumentNames(): List<String> =
        runBlocking {
            val app = composeRule.activity.application as KeepSheetApplication
            app.repository
                .observeDocuments()
                .first()
                .map { it.name }
        }

    private fun pressBackToHome() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        awaitText("Merge")
    }

    /** Page Review disables Save until page-edge detection has finished. */
    private fun awaitSaveEnabled() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("Save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitShutterEnabled() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescription("Shutter") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitThumbnailCount(count: Int) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(THUMBNAIL_TEST_TAG).fetchSemanticsNodes().size == count
        }
    }

    private fun awaitPagePreviewCount(count: Int) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(PAGE_PREVIEW_TEST_TAG).fetchSemanticsNodes().size == count
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 60_000L
        const val SETTLE_CHECKS = 10
        const val SETTLE_CHECK_MILLIS = 200L
    }
}
