package com.github.tomasbjerre.keepsheet.pdf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** See specs/capture-and-processing.md#page-rotation. Pure degree arithmetic — [applyRotation]'s
 * actual bitmap transform needs real Android graphics APIs and is covered by the instrumented
 * `ImagesToPdfTest` instead (see android/README.md#testing). */
class PageRotationTest {
    @Test
    fun `rotating clockwise steps through the four right angles and wraps around`() {
        assertThat(rotatedClockwise(0)).isEqualTo(90)
        assertThat(rotatedClockwise(90)).isEqualTo(180)
        assertThat(rotatedClockwise(180)).isEqualTo(270)
        assertThat(rotatedClockwise(270)).isEqualTo(0)
    }

    @Test
    fun `rotating counter-clockwise steps through the four right angles and wraps around`() {
        assertThat(rotatedCounterClockwise(0)).isEqualTo(270)
        assertThat(rotatedCounterClockwise(270)).isEqualTo(180)
        assertThat(rotatedCounterClockwise(180)).isEqualTo(90)
        assertThat(rotatedCounterClockwise(90)).isEqualTo(0)
    }

    @Test
    fun `clockwise then counter-clockwise returns to the original rotation`() {
        val original = 90
        assertThat(rotatedCounterClockwise(rotatedClockwise(original))).isEqualTo(original)
    }

    @Test
    fun `normalizes degrees outside 0 until 360 into range`() {
        assertThat(normalizedDegrees(360)).isEqualTo(0)
        assertThat(normalizedDegrees(450)).isEqualTo(90)
        assertThat(normalizedDegrees(-90)).isEqualTo(270)
    }
}
