package com.github.tomasbjerre.keepsheet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for #3: Home's Scan/Import/Merge actions must do
 * *something* when tapped (see specs/ui-flows.md#1-home), even before
 * Capture/Import/Merge screens exist — silently doing nothing reads as a
 * broken app, not an unfinished one.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scanImportAndMergeReportNotImplementedYetInsteadOfDoingNothing() {
        composeRule.onNodeWithText("Scan").performClick()
        awaitText("Scan isn't implemented yet.")

        composeRule.onNodeWithText("Import").performClick()
        awaitText("Import isn't implemented yet.")

        composeRule.onNodeWithText("Merge").performClick()
        awaitText("Merge isn't implemented yet.")
    }

    @Test
    fun showsTheEmptyStateWithNoDocuments() {
        awaitText("No documents yet — tap Scan to create your first PDF.")
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
