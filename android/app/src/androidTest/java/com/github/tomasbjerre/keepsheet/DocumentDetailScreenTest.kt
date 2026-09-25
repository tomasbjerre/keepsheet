package com.github.tomasbjerre.keepsheet

import android.Manifest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.tomasbjerre.keepsheet.ui.DOCUMENT_NAME_FIELD_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.PAGE_PREVIEW_TEST_TAG
import com.github.tomasbjerre.keepsheet.ui.THUMBNAIL_TEST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * End-to-end: scans a page, saves it, and exercises Document Detail (see
 * specs/ui-flows.md#5-document-detail) — page preview (real PdfRenderer output, not a
 * mock), rename, and delete. Share isn't exercised here since it hands off to the
 * system share sheet, outside this app's process.
 */
@RunWith(AndroidJUnit4::class)
class DocumentDetailScreenTest {
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

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
        renameTo("Shared Name")

        awaitText("Shared Name (2)")
    }

    private fun scanAndSaveOnePage() {
        composeRule.onNodeWithText("Scan").performClick()
        awaitShutterEnabled()
        composeRule.onNodeWithText("Shutter").performClick()
        awaitThumbnailCount(1)
        composeRule.onNodeWithText("Done").performClick()
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
     * for the (possibly de-duplicated) result to come back from the database. */
    private fun renameTo(name: String) {
        composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performTextReplacement(name)
        composeRule.onNodeWithTag(DOCUMENT_NAME_FIELD_TEST_TAG).performImeAction()
        awaitText(name)
    }

    private fun pressBackToHome() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        awaitText("Merge")
    }

    private fun awaitShutterEnabled() {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText("Shutter") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
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
        const val TIMEOUT_MILLIS = 15_000L
    }
}
