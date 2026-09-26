package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.tomasbjerre.keepsheet.pdf.Corners
import com.github.tomasbjerre.keepsheet.pdf.Point
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
 */
@Composable
fun CropEditor(
    uri: Uri,
    corners: Corners?,
    onCornersChange: (Corners) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeScaled(resolver, uri, PREVIEW_MAX_SIDE)?.asImageBitmap() }
    }
    val bitmap = image
    val currentCorners by rememberUpdatedState(corners)
    val currentOnChange by rememberUpdatedState(onCornersChange)
    val color = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: 1f)
                .testTag(CROP_EDITOR_TEST_TAG)
                .pointerInput(uri) { dragCorners({ currentCorners }, { currentOnChange(it) }) },
    ) {
        if (bitmap != null) {
            drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
        val quad = corners?.toList()?.map { Offset(it.x * size.width, it.y * size.height) }
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

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragCorners(
    corners: () -> Corners?,
    onChange: (Corners) -> Unit,
) {
    var grabbed = -1
    detectDragGestures(
        onDragStart = { start ->
            grabbed = nearestCorner(corners(), start, size, GRAB_RADIUS_DP.dp.toPx())
        },
        onDragEnd = { grabbed = -1 },
        onDragCancel = { grabbed = -1 },
    ) { change, drag ->
        val current = corners()
        if (current != null && grabbed >= 0) {
            change.consume()
            val old = current.toList()[grabbed]
            val moved =
                Point(
                    (old.x + drag.x / size.width).coerceIn(0f, 1f),
                    (old.y + drag.y / size.height).coerceIn(0f, 1f),
                )
            onChange(current.with(grabbed, moved))
        }
    }
}

private fun nearestCorner(
    corners: Corners?,
    touch: Offset,
    size: IntSize,
    maxDistance: Float,
): Int {
    val distances =
        corners?.toList()?.map { hypot(it.x * size.width - touch.x, it.y * size.height - touch.y) }.orEmpty()
    val nearest = distances.indices.minByOrNull { distances[it] } ?: return -1
    return if (distances[nearest] <= maxDistance) nearest else -1
}
