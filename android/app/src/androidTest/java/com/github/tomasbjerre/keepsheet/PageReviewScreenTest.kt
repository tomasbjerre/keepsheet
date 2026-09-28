package com.github.tomasbjerre.keepsheet

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.IntSize
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.keepsheet.data.PaperFormat
import com.github.tomasbjerre.keepsheet.data.PaperFormatPreference
import com.github.tomasbjerre.keepsheet.ui.CROP_EDITOR_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PHOTO_VIEWER_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.REVIEW_THUMBNAIL_TEST_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.anyOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises Page Review's per-page thumbnail selection and full-screen photo viewer (see
 * specs/ui-flows.md#3-page-review). Reached via Home's Import (real sample pages, see
 * keepsheet#55/SamplePages), the same
 * ActivityResult-stubbing approach InstructionVideoTest uses, so this needs no camera
 * permission. The reorder drag's own math (PageReviewScreen's computeReviewDrag/moved) is
 * covered separately by the plain JVM PageReviewDragTest — driving the actual long-press
 * gesture through the emulator's pointer input pipeline is what's exercised here indirectly
 * via selection, since the gesture itself needs real hardware-like timing that's flaky to
 * simulate reliably in this harness.
 */
@RunWith(AndroidJUnit4::class)
class PageReviewScreenTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rule = composeRule

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    /** Selection is tracked per-page (by uri), not by slot — tapping a different thumbnail
     * must show THAT page's own filter, not whatever the previously selected page had. */
    @Test
    fun selectingAPageShowsItsOwnFilterNotThePreviouslySelectedPagesFilter() {
        importTwoSamplePages()

        composeRule.onNodeWithText("Color").performScrollTo().performClick()
        composeRule.onNodeWithText("Color").assertIsSelected()

        // Scrolled the thumbnail strip out of view above — scrollTo() on a LazyRow item
        // doesn't propagate to the outer Column, so scroll to a direct Column child
        // (right above the strip) to bring the whole top of the screen back into view.
        composeRule.onNodeWithText("2 page(s)").performScrollTo()
        composeRule.onAllNodesWithTag(REVIEW_THUMBNAIL_TEST_TAG)[1].performClick()
        composeRule.onNodeWithText("Black & white").performScrollTo().assertIsSelected()

        composeRule.onNodeWithText("2 page(s)").performScrollTo()
        composeRule.onAllNodesWithTag(REVIEW_THUMBNAIL_TEST_TAG)[0].performClick()
        composeRule.onNodeWithText("Color").performScrollTo().assertIsSelected()
    }

    /** See specs/ui-flows.md#3-page-review (keepsheet#67): a read-only full-screen view of
     * the selected page, opened and closed from the crop section. */
    @Test
    fun viewFullSizeOpensAndClosesThePhotoViewer() {
        importTwoSamplePages()

        // Filtering the (larger, full-screen-sized) image takes longer than the crop
        // preview's own smaller one — wait for it rather than a fixed assertExists(), and tap
        // again if the first tap never opened the viewer at all.
        composeRule.actUntil(
            action = { composeRule.onNodeWithText("View full size").performScrollTo().performClick() },
            done = { composeRule.onAllNodesWithTag(PHOTO_VIEWER_TEST_TAG).fetchSemanticsNodes().isNotEmpty() },
        )

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TEST_TAG).assertDoesNotExist()
    }

    /** Regression test for keepsheet#69: a real single-finger drag (down/move/up, no
     * long-press involved — CropEditor's own pointerInput, not detectDragGesturesAfterLongPress)
     * must actually move the grabbed corner. The underlying bug was reading
     * change.positionChange() after change.consume() — it returns Offset.Zero once a change
     * is consumed, so the drag silently did nothing every time despite correctly picking a
     * corner to grab. Verified against the real saved output (not the drawn overlay, which
     * has no semantics to assert on): dragging a corner inward must shrink the page below
     * what the default 10%-inset crop alone would produce. */
    @Test
    fun draggingACropCornerActuallyMovesIt() {
        importTwoSamplePages()

        // Guarantee a known starting quad (10% inset) regardless of whether automatic
        // detection already found something for this real photographed sample page.
        if (composeRule.onAllNodes(hasText("Full photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Full photo").performScrollTo().performClick()
        }
        composeRule.onNodeWithText("Crop manually").performScrollTo().performClick()

        val cropEditor = composeRule.onNodeWithTag(CROP_EDITOR_TEST_TAG).performScrollTo()
        awaitCropEditorSizeSettled()
        cropEditor.dragACropCornerInward()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(PAGE_PREVIEW_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        val croppedWidth =
            runBlocking {
                val app = composeRule.activity.application as KeepSheetApplication
                val document =
                    app.repository
                        .observeDocuments()
                        .first()
                        .first()
                val page = app.repository.getPages(document.id).first()
                BitmapFactory.decodeFile(page.imagePath).width
            }
        // SamplePages.COVER is 900px wide; an undragged 10%-inset crop is exactly 720px
        // ((0.9 - 0.1) * 900). Compose's test touch injection doesn't reproduce the full
        // requested drag distance as faithfully as a real finger does (confirmed by hand
        // against a real device while diagnosing this bug), so this only asserts the drag
        // had *some* effect — before the fix it had none at all, landing on exactly 720.
        assertTrue("cropped width was $croppedWidth, expected < 710 (720 = drag had no effect)", croppedWidth < 710)
    }

    /** See specs/capture-and-processing.md#automatic-cropping-and-straightening
     * (keepsheet#68): re-running detection always gives explicit feedback, even when the
     * outcome doesn't change anything visible in the crop preview — otherwise a
     * no-change outcome looks identical to the tap having done nothing at all. Checks for
     * either outcome's message since whether this real photographed sample page's edges
     * are detected isn't the point of this test. */
    @Test
    fun detectEdgesAlwaysShowsFeedback() {
        importTwoSamplePages()

        composeRule.onNodeWithText("Detect edges").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText("Found the page edges.") or hasText("Detection found nothing — still using the full photo."))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** See specs/capture-and-processing.md#printer-friendly-pages (keepsheet#72): the page
     * size choice is remembered (via [PaperFormatPreference]) across documents until
     * changed again. */
    @Test
    fun pageSizeChoicePersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PaperFormatPreference(context).format = PaperFormat.A4 // a known starting state

        importTwoSamplePages()

        composeRule.onNodeWithText("Page size: A4").assertExists()
        composeRule.onNodeWithText("Page size: A4").performClick()
        composeRule.onNodeWithText("Letter").performClick()

        composeRule.onNodeWithText("Page size: Letter").assertExists()
        assertEquals(PaperFormat.LETTER, PaperFormatPreference(context).format)
    }

    private fun importTwoSamplePages() {
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").performClick()

        stubPhotoPickerWith(SamplePages.COVER, SamplePages.PAGE_05)
        composeRule.onNodeWithText("Import").performClick()
        awaitEnabled("Save")
    }

    private fun stubPhotoPickerWith(vararg assetNames: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uris =
            assetNames.mapIndexed { index, name ->
                Uri.fromFile(SamplePages.copyToCache(context, name, "page-review-test-import-$index.jpg"))
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

    /** CropEditor's canvas is square until its bitmap has been decoded, then takes the photo's
     * aspect ratio, moving every corner with it — a drag aimed at a corner before that lands
     * on empty canvas and does nothing. Nothing observable says "decoded", so wait for the
     * size to hold still instead. */
    private fun awaitCropEditorSizeSettled() {
        var lastSize = IntSize.Zero
        var lastChangeMillis = SystemClock.uptimeMillis()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            val size = composeRule.onNodeWithTag(CROP_EDITOR_TEST_TAG).fetchSemanticsNode().size
            val now = SystemClock.uptimeMillis()
            if (size != lastSize) {
                lastSize = size
                lastChangeMillis = now
            }
            size.height > 0 && now - lastChangeMillis >= SIZE_SETTLED_MILLIS
        }
    }

    private fun awaitEnabled(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 60_000L
        const val SIZE_SETTLED_MILLIS = 1_500L
    }
}
