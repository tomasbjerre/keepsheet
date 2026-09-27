package com.github.tomasbjerre.keepsheet

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
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
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.ui.CROP_EDITOR_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.FILTER_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.MERGE_PICKER_ROW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.anyOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a correctness test — drives a clean, deliberate walkthrough of the app's main
 * screens for the instruction video attached to every GitHub Release (keepsheet#54),
 * the same way wisp's own InstructionVideoTest does. The CI workflow
 * (../../../../../../../.github/workflows/instrumented_android.yml) records the
 * device's screen for the duration of this one test only — not the whole instrumented
 * suite — so what plays back is a coherent demo, not incidental test noise from
 * unrelated tests.
 *
 * Import (rather than the live Shutter) is used to get pages into the session so the
 * video shows a real, recognizable document (keepsheet#55's photographed brochure
 * pages) instead of the emulator's synthetic webcam test pattern — the system photo
 * picker itself isn't driven (its UI varies by API level/OEM and would make this
 * flaky), its ActivityResult is stubbed with Espresso Intents to return real sample
 * pages directly, the same outcome a person picking real photos would produce.
 *
 * [pause] briefly holds each interesting state so a human watching the recording can
 * actually see it, rather than the walkthrough flashing past at test speed.
 */
@RunWith(AndroidJUnit4::class)
class InstructionVideoTest {
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    @Test
    fun walkthrough() {
        dismissSystemAnrIfPresent()
        composeRule.waitForIdle()
        pause()

        composeRule.onNodeWithText("Scan").performClick()
        composeRule.waitForIdle()
        pause()

        stubPhotoPickerWith(SamplePages.COVER)
        composeRule.onNodeWithText("Import").performClick()
        awaitTagCount(THUMBNAIL_TEST_TAG, 1)
        pause()

        composeRule.onNodeWithText("Done").performClick()
        awaitEnabled("Save")
        pause(LONG_PAUSE_MILLIS)

        // The filter chips' live previews (keepsheet#49) make the difference between
        // grayscale and black-and-white obvious on this exact page, not a generic icon.
        composeRule.onNodeWithText("Grayscale").performScrollTo()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(FILTER_PREVIEW_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Grayscale").performScrollTo().performClick()
        pause()
        composeRule.onNodeWithText("Color").performScrollTo().performClick()
        pause()

        // Rotate (keepsheet#52): visibly turns the preview, then back to upright so the
        // rest of the walkthrough shows the page the way it's actually meant to be read.
        composeRule.onNodeWithText("Rotate right").performScrollTo().performClick()
        pause()
        composeRule.onNodeWithText("Rotate left").performScrollTo().performClick()
        pause()

        // Pinch-to-zoom (keepsheet#53) on the crop preview.
        composeRule.onNodeWithTag(CROP_EDITOR_TEST_TAG).performTouchInput {
            val center = Offset(visibleSize.width / 2f, visibleSize.height / 2f)
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(150f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(150f, 0f),
            )
        }
        composeRule.waitForIdle()
        pause(LONG_PAUSE_MILLIS)

        composeRule.onNodeWithText("Save").performClick()
        // A real, full-resolution photographed page (vs. the tiny synthetic images other
        // tests finalize) takes noticeably longer to crop/filter/write — a longer timeout
        // than the default, not a sign anything is actually wrong.
        awaitTagCount(PAGE_PREVIEW_TEST_TAG, 1, timeoutMillis = FINALIZE_TIMEOUT_MILLIS)
        pause(LONG_PAUSE_MILLIS)

        composeRule.onNodeWithContentDescription("Back").performClick()
        pause()

        // A second document, seeded directly (see ScreenshotTest/MergeScreenTest) from a
        // real sample page, just so Merge has two real-looking documents to show staged.
        seedSecondDocument()
        composeRule.onNodeWithText("Merge").performClick()
        pause()
        composeRule.onNodeWithText("From KeepSheet").performClick()
        awaitTagCount(MERGE_PICKER_ROW_TEST_TAG, 2)
        composeRule.onAllNodesWithTag(MERGE_PICKER_ROW_TEST_TAG)[0].performClick()
        pause()
        composeRule.onAllNodesWithTag(MERGE_PICKER_ROW_TEST_TAG)[1].performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        pause(LONG_PAUSE_MILLIS)
    }

    /**
     * Stubs the next `PickMultipleVisualMedia` ActivityResult (Capture's Import button)
     * to return [assetNames] copied out of this test APK's own assets as the picked
     * pages — the same outcome a person picking real photos from their gallery would
     * produce, without depending on the system photo picker's own UI.
     */
    private fun stubPhotoPickerWith(vararg assetNames: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uris =
            assetNames.mapIndexed { index, name ->
                Uri.fromFile(SamplePages.copyToCache(context, name, "instruction-video-import-$index.jpg"))
            }
        val resultData =
            Intent().apply {
                clipData =
                    ClipData.newRawUri("pages", uris.first()).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
            }
        Intents
            .intending(
                anyOf(
                    hasAction(Intent.ACTION_PICK),
                    hasAction(Intent.ACTION_GET_CONTENT),
                    hasAction("android.provider.action.PICK_IMAGES"),
                ),
            ).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultData))
    }

    // A real photographed page (keepsheet#55/SamplePages) rather than a plain color
    // swatch, so Merge shows something that actually looks like a scanned document.
    private fun seedSecondDocument() {
        val app = composeRule.activity.application as KeepSheetApplication
        val documentsDir = File(composeRule.activity.filesDir, "documents").apply { mkdirs() }
        val pdfFile = File(documentsDir, "instruction-video-second.pdf")
        val imageFile = SamplePages.copyToCache(composeRule.activity, SamplePages.PAGE_05, "instruction-video-second-page-0.jpg")
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
        timeoutMillis: Long = TIMEOUT_MILLIS,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size == count
        }
    }

    /** Holds a state on screen long enough for a human watching the recording to see it. */
    private fun pause(millis: Long = PAUSE_MILLIS) = Thread.sleep(millis)

    private fun dismissSystemAnrIfPresent() {
        val waitButton = device.findObject(UiSelector().textContains("Wait"))
        if (waitButton.exists()) {
            waitButton.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val FINALIZE_TIMEOUT_MILLIS = 45_000L
        const val PAUSE_MILLIS = 700L
        const val LONG_PAUSE_MILLIS = 1_500L
    }
}
