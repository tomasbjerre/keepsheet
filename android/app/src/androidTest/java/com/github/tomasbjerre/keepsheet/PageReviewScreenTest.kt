package com.github.tomasbjerre.keepsheet

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.tomasbjerre.keepsheet.ui.PHOTO_VIEWER_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.REVIEW_THUMBNAIL_TEST_TAG
import org.hamcrest.CoreMatchers.anyOf
import org.junit.After
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

        composeRule.onNodeWithText("View full size").performScrollTo().performClick()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TEST_TAG).assertExists()

        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TEST_TAG).assertDoesNotExist()
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

    private fun awaitEnabled(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
