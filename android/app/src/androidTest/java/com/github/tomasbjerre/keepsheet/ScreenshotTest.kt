package com.github.tomasbjerre.keepsheet

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.ui.MERGE_PICKER_ROW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Not a correctness test — drives the real app to capture one screenshot
 * per screen/state in specs/ui-flows.md for the Play Store listing and
 * README, the same way wisp's own ScreenshotTest does. Output lands under
 * /sdcard/keepsheet-screenshots (not the app's own storage, which
 * `connectedAndroidTest` wipes by uninstalling the app when the run
 * finishes) — the CI workflow pulls it from there via `adb pull`.
 *
 * Camera permission is pre-granted (see CaptureScreenTest) so Capture's
 * screenshot shows the real live preview rather than the permission
 * rationale state.
 *
 * When you add a screen or a state to a screen, add a capture for it here
 * in the same change — see ../../../../../../../AGENTS.md.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun captureScreenshots() {
        dismissSystemAnrIfPresent()
        composeRule.waitForIdle()
        screenshot("1-home")

        composeRule.onNodeWithText("Scan").performClick()
        composeRule.waitForIdle()
        screenshot("2-capture")

        awaitEnabled("Shutter")
        composeRule.onNodeWithText("Shutter").performClick()
        awaitTagCount(THUMBNAIL_TEST_TAG, 1)
        composeRule.onNodeWithText("Done").performClick()
        awaitEnabled("Save")
        screenshot("6-page-review")
        composeRule.onNodeWithText("Crop manually").performClick()
        composeRule.waitForIdle()
        screenshot("7-page-review-crop")
        composeRule.onNodeWithText("Full photo").performClick()
        composeRule.onNodeWithText("Save").performClick()
        awaitTagCount(PAGE_PREVIEW_TEST_TAG, 1)
        screenshot("3-document-detail")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Information").performClick()
        composeRule.waitForIdle()
        screenshot("4-information")
        composeRule.onNodeWithText("Close").performClick()

        // A second document, seeded directly (see MergeScreenTest) rather than via a
        // second full Scan session, just so Merge has two documents to show staged.
        seedSecondDocument()
        composeRule.onNodeWithText("Merge").performClick()
        composeRule.onNodeWithText("From KeepSheet").performClick()
        awaitTagCount(MERGE_PICKER_ROW_TEST_TAG, 2)
        // Both rows stay visible (just disabled + checkmarked) once added, so this is
        // "add row 0, then row 1" — not two clicks racing to add the same one.
        composeRule.onAllNodesWithTag(MERGE_PICKER_ROW_TEST_TAG)[0].performClick()
        composeRule.onAllNodesWithTag(MERGE_PICKER_ROW_TEST_TAG)[1].performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        screenshot("5-merge")
    }

    private fun seedSecondDocument() {
        val app = composeRule.activity.application as KeepSheetApplication
        val documentsDir = File(composeRule.activity.filesDir, "documents").apply { mkdirs() }
        val pdfFile = File(documentsDir, "screenshot-second.pdf")
        val imageFile = File(documentsDir, "screenshot-second-page-0.jpg")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        FileOutputStream(imageFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        buildPdfFromImages(listOf(imageFile.absolutePath), pdfFile)
        runBlocking {
            val documentId =
                app.repository.createDocument(System.currentTimeMillis(), "Second document", pdfFile.absolutePath, DocumentSource.SCANNED)
            app.repository.finalizeDocument(documentId, 1, pdfFile.length())
        }
    }

    private fun awaitEnabled(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTagCount(
        tag: String,
        count: Int,
    ) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size == count
        }
    }

    // Via the shell, not app-code File I/O: scoped storage silently blocks the app
    // process itself from writing raw /sdcard paths, but the shell (uiautomator's
    // executeShellCommand) isn't subject to that.
    private fun screenshot(name: String) {
        dismissSystemAnrIfPresent()
        device.executeShellCommand("mkdir -p $SCREENSHOT_DIR")
        device.executeShellCommand("screencap -p $SCREENSHOT_DIR/$name.png")
    }

    /** Occasionally a "System UI isn't responding" dialog covers the screen on a loaded
     * (e.g. software-rendered) emulator — dismiss it rather than capture it by accident. */
    private fun dismissSystemAnrIfPresent() {
        val waitButton = device.findObject(UiSelector().textContains("Wait"))
        if (waitButton.exists()) {
            waitButton.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val SCREENSHOT_DIR = "/sdcard/keepsheet-screenshots"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
