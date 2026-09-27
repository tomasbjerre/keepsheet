package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.pdf.applyRotation
import com.github.tomasbjerre.keepsheet.pdf.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PHOTO_VIEWER_TEST_TAG = "photoViewer"

private const val VIEWER_MAX_SIDE = 2000

/**
 * Read-only full-screen view of a single page (see specs/ui-flows.md#3-page-review,
 * keepsheet#67) — opened from [CropSection]'s "View full size" action. Same pinch-to-zoom
 * and drag-to-pan as [CropEditor]'s preview (reusing its pure [clampZoomScale]/
 * [clampPanOffset] math), but with no crop overlay and nothing to grab, so there's no risk
 * of moving a crop corner while just looking closely at a page. [rotationDegrees] and
 * [filter] match whatever's currently shown in the crop preview, so this is the exact same
 * image, just bigger — not a guess at what the saved page will look like.
 */
@Composable
fun PhotoViewerDialog(
    uri: Uri,
    rotationDegrees: Int,
    filter: PageFilter,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val resolver = LocalContext.current.contentResolver
        val image by
            produceState<ImageBitmap?>(null, uri, rotationDegrees, filter) {
                value =
                    withContext(Dispatchers.IO) {
                        decodeScaled(resolver, uri, VIEWER_MAX_SIDE)
                            ?.let { applyRotation(it, rotationDegrees) }
                            ?.let { filtered(it, filter) }
                            ?.asImageBitmap()
                    }
            }
        var scale by remember(uri, rotationDegrees) { mutableStateOf(MIN_ZOOM_SCALE) }
        var panOffset by remember(uri, rotationDegrees) { mutableStateOf(Offset.Zero) }
        val bitmap = image

        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .testTag(PHOTO_VIEWER_TEST_TAG)
                            .pointerInput(uri) {
                                handleZoomPan(
                                    scale = { scale },
                                    onScaleChange = { scale = it },
                                    panOffset = { panOffset },
                                    onPanChange = { panOffset = it },
                                )
                            },
                ) {
                    val imageSize =
                        IntSize(
                            (size.width * scale).toInt().coerceAtLeast(1),
                            (size.height * scale).toInt().coerceAtLeast(1),
                        )
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(panOffset.x.toInt(), panOffset.y.toInt()),
                        dstSize = imageSize,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/** Pinch-to-zoom (anchored on the viewport center) and drag-to-pan once zoomed in — the same
 * gesture [CropEditor] uses, minus the crop-corner-grabbing branch this read-only viewer has
 * no use for. */
private suspend fun PointerInputScope.handleZoomPan(
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
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                event.changes.forEach { it.consume() }
                val oldScale = scale()
                val newScale = clampZoomScale(oldScale * event.calculateZoom())
                val center = Offset(size.width / 2f, size.height / 2f)
                val contentCenterBefore = (center - panOffset()) / oldScale
                applyPan(center - contentCenterBefore * newScale + event.calculatePan())
                onScaleChange(newScale)
            } else if (scale() > MIN_ZOOM_SCALE) {
                val change = pressed.first()
                // positionChange() must be read before consume() — it returns Offset.Zero
                // for a change already marked consumed (keepsheet#69, same bug in CropEditor).
                val drag = change.positionChange()
                change.consume()
                applyPan(panOffset() + drag)
            }
        }
    }
}
