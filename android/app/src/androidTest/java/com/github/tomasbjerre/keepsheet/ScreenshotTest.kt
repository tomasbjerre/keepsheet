package com.github.tomasbjerre.keepsheet

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

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
    }
}
