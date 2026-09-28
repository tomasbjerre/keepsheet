package com.github.tomasbjerre.keepsheet

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule

private const val ACT_ATTEMPTS = 3
private const val ACT_ATTEMPT_TIMEOUT_MILLIS = 20_000L

/**
 * Performs [action], then waits for its effect ([done]) to show up; if it doesn't within
 * [attemptTimeoutMillis], performs [action] again, up to [attempts] times in total. Only the
 * last attempt's timeout fails the test.
 *
 * For actions whose effect is observable but whose delivery isn't guaranteed on a loaded,
 * software-rendered emulator: a tap injected mid-frame that never reaches its target, or
 * text typed into a field that a background update (e.g. the OCR-suggested name, see
 * KeepSheetApplication) then reseeds. The wait is what makes this cheap — a healthy run
 * never pays for a retry.
 *
 * [action] must be safe to repeat: an action that already took effect but was merely slow
 * to show it is performed again.
 */
fun ComposeTestRule.actUntil(
    attempts: Int = ACT_ATTEMPTS,
    attemptTimeoutMillis: Long = ACT_ATTEMPT_TIMEOUT_MILLIS,
    action: () -> Unit,
    done: () -> Boolean,
) {
    repeat(attempts - 1) {
        action()
        try {
            waitUntil(attemptTimeoutMillis, done)
            return
        } catch (_: ComposeTimeoutException) {
            // Not there yet — act again.
        }
    }
    action()
    waitUntil(attemptTimeoutMillis, done)
}
