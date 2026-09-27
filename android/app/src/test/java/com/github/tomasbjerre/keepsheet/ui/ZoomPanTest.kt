package com.github.tomasbjerre.keepsheet.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ZoomPanTest {
    @Test
    fun `zoom scale never goes below 1x or above the max`() {
        assertThat(clampZoomScale(0.4f)).isEqualTo(MIN_ZOOM_SCALE)
        assertThat(clampZoomScale(2f)).isEqualTo(2f)
        assertThat(clampZoomScale(50f)).isEqualTo(MAX_ZOOM_SCALE)
    }

    @Test
    fun `pan offset is pinned to zero when not zoomed in`() {
        assertThat(clampPanOffset(offset = 100f, viewportSize = 300f, scale = 1f)).isEqualTo(0f)
        assertThat(clampPanOffset(offset = -100f, viewportSize = 300f, scale = 1f)).isEqualTo(0f)
    }

    @Test
    fun `pan offset is clamped so the zoomed image always covers the viewport`() {
        // 2x zoom over a 300px viewport: the image is 600px, so the offset must stay in [-300, 0]
        // for the viewport to never see a gap past either edge of the image.
        assertThat(clampPanOffset(offset = 50f, viewportSize = 300f, scale = 2f)).isEqualTo(0f)
        assertThat(clampPanOffset(offset = -50f, viewportSize = 300f, scale = 2f)).isEqualTo(-50f)
        assertThat(clampPanOffset(offset = -1000f, viewportSize = 300f, scale = 2f)).isEqualTo(-300f)
    }

    @Test
    fun `a zero-size viewport never divides by zero`() {
        assertThat(clampPanOffset(offset = 10f, viewportSize = 0f, scale = 3f)).isEqualTo(0f)
    }
}
