package com.github.tomasbjerre.keepsheet.ui

/**
 * Pure zoom/pan math for [CropEditor]'s pinch-to-zoom preview (specs/ui-flows.md#3-page-review,
 * keepsheet#53) — kept free of Compose/Android types so it's a plain unit test, not an
 * instrumented one.
 */
const val MIN_ZOOM_SCALE = 1f
const val MAX_ZOOM_SCALE = 5f

/** Never below fit-to-view (1x, no zooming out past the whole page) or above [MAX_ZOOM_SCALE]. */
fun clampZoomScale(scale: Float): Float = scale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)

/**
 * Clamps a pan offset (along one axis) so a [scale]d image, drawn at that offset within a
 * [viewportSize]-sized viewport, always covers the viewport — panning can never leave a blank
 * gap at an edge.
 */
fun clampPanOffset(
    offset: Float,
    viewportSize: Float,
    scale: Float,
): Float {
    if (viewportSize <= 0f) return 0f
    val minOffset = viewportSize * (1f - scale)
    return offset.coerceIn(minOf(minOffset, 0f), 0f)
}
