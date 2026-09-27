package com.github.tomasbjerre.keepsheet.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.pdf.Corners
import com.github.tomasbjerre.keepsheet.pdf.Point
import com.github.tomasbjerre.keepsheet.pdf.applyFilter
import com.github.tomasbjerre.keepsheet.pdf.applyRotation
import com.github.tomasbjerre.keepsheet.pdf.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

const val CROP_EDITOR_TEST_TAG = "cropEditor"

private const val PREVIEW_MAX_SIDE = 1200
private const val HANDLE_RADIUS_DP = 12
private const val GRAB_RADIUS_DP = 40

/**
 * Shows a page photo with its crop quadrilateral on top — see
 * specs/ui-flows.md#3-page-review: the detected crop is visible before it's applied, and
 * each corner can be dragged to adjust it. With null [corners] (nothing detected, or the
 * user chose the full photo) the whole photo is shown with no overlay.
 *
 * [rotationDegrees] (specs/capture-and-processing.md#page-rotation) and [filter]
 * (specs/capture-and-processing.md#document-filters) are both applied to the preview
 * itself, not just remembered — the whole point of showing it live here is that what will
 * be applied to the saved page is never a guess (keepsheet#52, keepsheet#70).
 *
 * Pinch-to-zoom and drag-to-pan (keepsheet#53) let a person inspect fine detail — e.g. whether
 * a filter choice keeps a photo on the page legible — without that detail being too small to
 * judge at the preview's normal size. Zoom is anchored on the viewport's center rather than the
 * pinch gesture's own centroid: simpler and just as usable for "zoom in to look closely," and
 * avoids the extra bookkeeping precise finger-anchored zoom would need. A single-finger drag
 * still adjusts a crop corner when one is grabbed (unchanged from before); it only pans the
 * zoomed image when no corner is grabbed, so zoom never steals the existing crop gesture.
 * Zoom/pan reset whenever [uri] or [rotationDegrees] changes (a different page, or a rotation
 * that changes what's being looked at) — a leftover zoomed-in viewport on a freshly shown image
 * would be confusing, not helpful.
 */
@Suppress("LongMethod") // Compose screen: state hoisting keeps this one flat function readable.
@Composable
fun CropEditor(
    uri: Uri,
    corners: Corners?,
    rotationDegrees: Int,
    filter: PageFilter,
    onCornersChange: (Corners) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, uri, rotationDegrees, filter) {
        value =
            withContext(Dispatchers.IO) {
                decodeScaled(resolver, uri, PREVIEW_MAX_SIDE)
                    ?.let { applyRotation(it, rotationDegrees) }
                    ?.let { filtered(it, filter) }
                    ?.asImageBitmap()
            }
    }
    val bitmap = image
    val currentCorners by rememberUpdatedState(corners)
    val currentOnChange by rememberUpdatedState(onCornersChange)
    val color = MaterialTheme.colorScheme.primary

    var scale by remember(uri, rotationDegrees) { mutableStateOf(MIN_ZOOM_SCALE) }
    var panOffset by remember(uri, rotationDegrees) { mutableStateOf(Offset.Zero) }
    val currentScale by rememberUpdatedState(scale)
    val currentPan by rememberUpdatedState(panOffset)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: 1f)
                .testTag(CROP_EDITOR_TEST_TAG)
                .pointerInput(uri) {
                    handleGestures(
                        corners = { currentCorners },
                        onCornersChange = { currentOnChange(it) },
                        scale = { currentScale },
                        onScaleChange = { scale = it },
                        panOffset = { currentPan },
                        onPanChange = { panOffset = it },
                    )
                },
    ) {
        if (bitmap != null) {
            val imageSize =
                IntSize(
                    (size.width * scale).toInt().coerceAtLeast(1),
                    (size.height * scale).toInt().coerceAtLeast(1),
                )
            val imageOffset = IntOffset(panOffset.x.toInt(), panOffset.y.toInt())
            drawImage(bitmap, dstOffset = imageOffset, dstSize = imageSize)
        }
        val quad =
            corners?.toList()?.map {
                Offset(it.x * size.width * scale + panOffset.x, it.y * size.height * scale + panOffset.y)
            }
        if (quad != null) {
            val outline =
                Path().apply {
                    quad.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    close()
                }
            drawPath(outline, color, style = Stroke(width = 3.dp.toPx()))
            quad.forEach { drawCircle(color, radius = HANDLE_RADIUS_DP.dp.toPx(), center = it) }
        }
    }
}

/**
 * One pointer: drags the nearest crop corner if one is within grab range, otherwise pans the
 * image if it's zoomed in. Two or more pointers: pinch-to-zoom (anchored on the viewport
 * center) plus whatever pan the pinch itself carries. See [CropEditor]'s doc for why zoom is
 * center-anchored rather than centroid-anchored.
 */
private suspend fun PointerInputScope.handleGestures(
    corners: () -> Corners?,
    onCornersChange: (Corners) -> Unit,
    scale: () -> Float,
    onScaleChange: (Float) -> Unit,
    panOffset: () -> Offset,
    onPanChange: (Offset) -> Unit,
) {
    fun applyPan(newOffset: Offset) {
        onPanChange(
            Offset(
                clampPanOffset(newOffset.x, size.width.toFloat(), scale()),
                clampPanOffset(newOffset.y, size.height.toFloat(), scale()),
            ),
        )
    }

    awaitEachGesture {
        var grabbedCorner = -1
        // True for the remainder of a gesture once it has seen 2+ pointers, so a pinch that
        // drops back to one finger starts a fresh single-finger phase (re-picks the nearest
        // corner from where that finger now is) rather than reusing whatever grab state a
        // single-finger phase before the pinch might have left behind.
        var everMultiTouch = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                everMultiTouch = true
                grabbedCorner = -1
                event.changes.forEach { it.consume() }
                val oldScale = scale()
                val newScale = clampZoomScale(oldScale * event.calculateZoom())
                val center = Offset(size.width / 2f, size.height / 2f)
                val contentCenterBefore = (center - panOffset()) / oldScale
                applyPan(center - contentCenterBefore * newScale + event.calculatePan())
                onScaleChange(newScale)
            } else {
                val change = pressed.first()
                if (everMultiTouch || change.previousPressed.not()) {
                    everMultiTouch = false
                    grabbedCorner =
                        nearestCorner(corners(), change.position, size, scale(), panOffset(), GRAB_RADIUS_DP.dp.toPx())
                }
                val current = corners()
                // positionChange() must be read before consume() — it returns Offset.Zero
                // for a change already marked consumed, so consuming first here would
                // always report a zero drag and silently do nothing (keepsheet#69).
                val drag = change.positionChange()
                when {
                    current != null && grabbedCorner >= 0 -> {
                        change.consume()
                        val old = current.toList()[grabbedCorner]
                        val moved =
                            Point(
                                (old.x + drag.x / (size.width * scale())).coerceIn(0f, 1f),
                                (old.y + drag.y / (size.height * scale())).coerceIn(0f, 1f),
                            )
                        onCornersChange(current.with(grabbedCorner, moved))
                    }
                    scale() > MIN_ZOOM_SCALE -> {
                        change.consume()
                        applyPan(panOffset() + drag)
                    }
                }
            }
        }
    }
}

private fun nearestCorner(
    corners: Corners?,
    touch: Offset,
    size: IntSize,
    scale: Float,
    panOffset: Offset,
    maxDistance: Float,
): Int {
    val distances =
        corners
            ?.toList()
            ?.map {
                val x = it.x * size.width * scale + panOffset.x
                val y = it.y * size.height * scale + panOffset.y
                hypot(x - touch.x, y - touch.y)
            }.orEmpty()
    val nearest = distances.indices.minByOrNull { distances[it] } ?: return -1
    return if (distances[nearest] <= maxDistance) nearest else -1
}

/** [PageFilter.COLOR] is a no-op (see [applyFilter]) — skip the pixel round-trip entirely
 * for it rather than decode/re-encode a bitmap that ends up unchanged. Internal rather than
 * private: [PhotoViewerDialog] reuses this same bitmap-filtering step. */
internal fun filtered(
    bitmap: Bitmap,
    filter: PageFilter,
): Bitmap {
    if (filter == PageFilter.COLOR) return bitmap
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()
    applyFilter(pixels, width, height, filter)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
