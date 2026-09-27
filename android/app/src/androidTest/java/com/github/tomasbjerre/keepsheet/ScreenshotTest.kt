package com.github.tomasbjerre.keepsheet

import android.Manifest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.ui.CROP_EDITOR_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.FILTER_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.MERGE_PICKER_ROW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PHOTO_VIEWER_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

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

        awaitShutterEnabled()
        composeRule.onNodeWithContentDescription("Shutter").performClick()
        awaitTagCount(THUMBNAIL_TEST_TAG, 1)
        composeRule.onNodeWithText("Done").performClick()
        awaitEnabled("Save")
        screenshot("6-page-review")
        // Not a numbered Play listing slot (see AGENTS.md/release pipeline convention) — just
        // documents each filter chip's live preview thumbnail (specs/capture-and-
        // processing.md#document-filters, keepsheet#49) making grayscale vs. black-and-white
        // visually obvious rather than a guess from the label alone.
        composeRule.onNodeWithText("Grayscale").performScrollTo()
        // useUnmergedTree: each preview Image is merged into its parent FilterChip's own
        // semantics node (a chip is one actionable unit), so the default merged-tree query
        // onAllNodesWithTag() other awaitTagCount() calls in this file use would never see
        // these three separately.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithTag(FILTER_PREVIEW_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size == PageFilter.entries.size
        }
        screenshot("page-review-filters")
        // Not a numbered Play listing slot — documents pinch-to-zoom on the crop preview
        // (specs/ui-flows.md#3-page-review, keepsheet#53) actually taking effect, not just
        // compiling. A real two-finger gesture, not a direct state poke, so this exercises the
        // same gesture-recognition code path a person's fingers would.
        composeRule.onNodeWithTag(CROP_EDITOR_TEST_TAG).performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(120f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(120f, 0f),
            )
        }
        composeRule.waitForIdle()
        screenshot("page-review-zoomed")
        // Not a numbered Play listing slot — documents the full-screen photo viewer
        // (specs/ui-flows.md#3-page-review, keepsheet#67): read-only, no crop overlay.
        composeRule.onNodeWithText("View full size").performScrollTo().performClick()
        // Filtering the (larger, full-screen-sized) image takes longer than the crop
        // preview's own smaller one — wait for it rather than a fixed assertExists().
        awaitTagCount(PHOTO_VIEWER_TEST_TAG, 1)
        screenshot("page-review-viewer")
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.waitForIdle()
        // Not a numbered Play listing slot (see AGENTS.md/release pipeline convention) — just
        // documents the rotate control (specs/capture-and-processing.md#page-rotation,
        // keepsheet#52) taking visible effect in the crop preview above it.
        composeRule.onNodeWithText("Rotate right").performScrollTo().performClick()
        composeRule.waitForIdle()
        screenshot("page-review-rotated")
        // Back to upright — the rest of this flow (Document Detail, Merge) should keep
        // showing the page the way it always has, not rotated from here on.
        composeRule.onNodeWithText("Rotate left").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Crop manually").performScrollTo().performClick()
        composeRule.waitForIdle()
        screenshot("7-page-review-crop")
        // Not a numbered Play listing slot — documents dragging a crop corner actually
        // moving it (specs/capture-and-processing.md#automatic-cropping-and-straightening,
        // keepsheet#69: a corner used to silently ignore every drag).
        composeRule.onNodeWithTag(CROP_EDITOR_TEST_TAG).performTouchInput {
            down(Offset(visibleSize.width * 0.1f, visibleSize.height * 0.1f))
            moveBy(Offset(visibleSize.width * 0.3f, visibleSize.height * 0.3f))
            up()
        }
        composeRule.waitForIdle()
        screenshot("page-review-crop-dragged")
        composeRule.onNodeWithText("Full photo").performScrollTo().performClick()
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

    // A real photographed page (see keepsheet#55/SamplePages) rather than a plain color
    // swatch, so the Merge/Document Detail screenshots this seeds show something that
    // actually looks like a scanned document.
    private fun seedSecondDocument() {
        val app = composeRule.activity.application as KeepSheetApplication
        val documentsDir = File(composeRule.activity.filesDir, "documents").apply { mkdirs() }
        val pdfFile = File(documentsDir, "screenshot-second.pdf")
        val imageFile = SamplePages.copyToCache(composeRule.activity, SamplePages.PAGE_05, "screenshot-second-page-0.jpg")
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

    /** The shutter (keepsheet#47) is an icon with no text — [awaitEnabled] can't find it. */
    private fun awaitShutterEnabled() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescription("Shutter") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
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
