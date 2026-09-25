package com.github.tomasbjerre.keepsheet

import android.Manifest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Exercises Capture's real CameraX shutter (see
 * specs/capture-and-processing.md#multi-page-capture and
 * specs/ui-flows.md#2-capture) against the emulator's/device's actual
 * camera. Camera permission is pre-granted via [GrantPermissionRule] so a
 * system permission dialog never blocks this run — the denial-handling
 * behavior itself (specs/permissions-and-privacy.md) isn't what this test
 * is about.
 */
@RunWith(AndroidJUnit4::class)
class CaptureScreenTest {
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // GrantPermissionRule must run (and grant the permission) before the activity in
    // composeRule launches, hence outer/around rather than two independent @Rules.
    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun capturingAPageEnablesDone() {
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Done").assertIsNotEnabled()

        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)
        composeRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun retakeReplacesThePageInsteadOfAddingANewOne() {
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)

        composeRule.onNodeWithContentDescription("Retake page").performClick()
        composeRule.onNodeWithText("Retaking — tap the shutter for a new shot").assertExists()

        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)
    }

    @Test
    fun removingThePageDisablesDoneAgain() {
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)

        composeRule.onNodeWithContentDescription("Remove page").performClick()
        awaitThumbnailCount(0)
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun backWithPagesCapturedAsksForConfirmationBeforeDiscarding() {
        composeRule.onNodeWithText("Scan").performClick()
        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard this scan?").assertExists()

        composeRule.onNodeWithText("Keep scanning").performClick()
        composeRule.onNodeWithText("Done").assertIsEnabled()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithText("No documents yet — tap Scan to create your first PDF.").assertExists()
    }

    private fun awaitThumbnailCount(count: Int) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(THUMBNAIL_TEST_TAG).fetchSemanticsNodes().size == count
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
