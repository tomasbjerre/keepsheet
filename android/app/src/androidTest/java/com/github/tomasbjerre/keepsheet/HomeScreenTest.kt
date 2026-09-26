package com.github.tomasbjerre.keepsheet

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for #3: Home's Scan/Import/Merge actions must do
 * *something* when tapped (see specs/ui-flows.md#1-home) — silently doing
 * nothing reads as a broken app. Scan, Import, and Merge all navigate to
 * real screens — see CaptureScreenTest and MergeScreenTest for their own
 * behavior once there.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scanOpensCapture() {
        composeRule.onNodeWithText("Scan").performClick()
        awaitText("Done")
    }

    @Test
    fun mergeOpensMergeScreen() {
        composeRule.onNodeWithText("Merge").performClick()
        awaitText("No files selected yet — add at least two PDFs to merge.")
    }

    @Test
    fun showsTheEmptyStateWithNoDocuments() {
        awaitText("No documents yet — tap Scan to create your first PDF.")
    }

    @Test
    fun informationDialogShowsVersionDeviceAndLinks() {
        // See specs/ui-flows.md#feedback-and-support and .github/ISSUE_TEMPLATE/bug_report.yml
        // — doesn't tap the links themselves, since those leave the app for a browser.
        composeRule.onNodeWithContentDescription("Information").performClick()

        awaitText("Report a problem or request a feature")
        composeRule.onNodeWithText("User manual").assertExists()
        composeRule.onNodeWithText("${Build.MODEL}, Android ${Build.VERSION.RELEASE}").assertExists()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Report a problem or request a feature").assertDoesNotExist()
    }

    // The snackbar shown on tap is posted from a coroutine (see KeepSheetApp),
    // so it can land a frame or two after performClick returns.
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
