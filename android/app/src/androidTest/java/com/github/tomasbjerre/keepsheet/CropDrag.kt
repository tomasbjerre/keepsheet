package com.github.tomasbjerre.keepsheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput

private const val START_INSET = 0.1f
private const val END_INSET = 0.9f
private const val DRAG_FRACTION = 0.3f
private const val DRAG_STEPS = 10
private const val EDGE_MARGIN_PX = 24f

/**
 * Drags a crop corner of the [CropEditor][com.github.tomasbjerre.keepsheet.ui.CropEditor]
 * canvas this is called on 30% of the canvas inward, as a person's finger would: press on the
 * corner, move in several steps, lift.
 *
 * Assumes the default 10%-inset quad ("Crop manually"). Picks the top-left corner, or the
 * bottom-right one if the top-left isn't on screen: on a small screen (CI's emulator is
 * 320x640) the canvas is taller than the scrolling area, so part of it is clipped, and a touch
 * aimed at a clipped corner would land on the page behind it and scroll the page instead.
 *
 * Touch injection positions are relative to the node's *visible* bounds (`boundsInRoot`,
 * clipped), not its full layout box — so the corner is located in root coordinates from the
 * unclipped `positionInRoot` and then translated, rather than as a fraction of `width`/`height`.
 */
fun SemanticsNodeInteraction.dragACropCornerInward() {
    val node = fetchSemanticsNode()
    val origin = node.positionInRoot
    val visible = node.boundsInRoot.deflate(EDGE_MARGIN_PX)

    fun corner(inset: Float) = origin + Offset(node.size.width * inset, node.size.height * inset)

    val (start, direction) =
        if (visible.contains(corner(START_INSET))) {
            corner(START_INSET) to 1f
        } else {
            corner(END_INSET) to -1f
        }
    val startLocal = start - node.boundsInRoot.topLeft
    val step =
        Offset(
            node.size.width * DRAG_FRACTION * direction / DRAG_STEPS,
            node.size.height * DRAG_FRACTION * direction / DRAG_STEPS,
        )

    performTouchInput {
        down(startLocal)
        repeat(DRAG_STEPS) { moveBy(step) }
        up()
    }
}
