package com.github.tomasbjerre.keepsheet.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * See specs/ui-flows.md#2-capture and specs/capture-and-processing.md#multi-page-capture.
 * [pages] is hoisted (owned by [KeepSheetApp]) rather than local state, so it survives
 * navigating to Page Review and back, per specs/ui-flows.md#3-page-review ("returns to
 * Capture with the session's pages intact").
 *
 * The live preview has no edge-detection overlay yet — that depends on an
 * edge-detection library (see android/app/build.gradle.kts), not added yet. Crop
 * fine-tuning and filters are Page Review's job (also not implemented yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    pages: List<CapturedPage>,
    onPagesChanged: (List<CapturedPage>) -> Unit,
    cacheDir: File,
    onDone: (List<Uri>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val captureDir = remember { File(cacheDir, "capture-session").apply { mkdirs() } }
    val env = remember { CaptureEnvironment(context, coroutineScope, snackbarHostState, imageCapture, captureDir) }
    val cameraAvailable = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val permission = rememberCameraPermissionState(context)

    var retakeTargetId by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val onBackRequested = { requestBack(pages.isNotEmpty(), onCancel) { showDiscardConfirm = true } }
    BackHandler(onBack = onBackRequested)

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) onPagesChanged(pages + uris.toCapturedPages())
        }

    Scaffold(
        topBar = { CaptureTopBar(onBack = onBackRequested) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CaptureScreenBody(
            innerPadding = innerPadding,
            pages = pages,
            cameraAvailable = cameraAvailable,
            permission = permission,
            imageCapture = imageCapture,
            retakeTargetId = retakeTargetId,
            onReorder = onPagesChanged,
            onRetake = { retakeTargetId = it },
            onCancelRetake = { retakeTargetId = null },
            onRemove = { id -> removePage(id, pages, onPagesChanged) },
            onImport = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onShutter = {
                env.captureShot(pages, onPagesChanged, retakeTargetId) { retakeTargetId = null }
            },
            onDone = { onDone(pages.map { it.uri }) },
        )
    }

    if (showDiscardConfirm) {
        DiscardSessionDialog(
            onConfirm = {
                showDiscardConfirm = false
                discardSession(pages, onPagesChanged, onCancel)
            },
            onDismiss = { showDiscardConfirm = false },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun CaptureScreenBody(
    innerPadding: PaddingValues,
    pages: List<CapturedPage>,
    cameraAvailable: Boolean,
    permission: CameraPermissionState,
    imageCapture: ImageCapture,
    retakeTargetId: String?,
    onReorder: (List<CapturedPage>) -> Unit,
    onRetake: (String) -> Unit,
    onCancelRetake: () -> Unit,
    onRemove: (String) -> Unit,
    onImport: () -> Unit,
    onShutter: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !cameraAvailable -> CaptureMessage("This device has no camera. Use Import below to add pages instead.")
                !permission.hasPermission ->
                    CameraPermissionRationale(
                        permanentlyDenied = permission.permanentlyDenied,
                        onGrant = permission.onRequest,
                        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
                    )
                else -> CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())
            }
            if (retakeTargetId != null) {
                RetakeBanner(onCancel = onCancelRetake, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
        CaptureThumbnailStrip(pages = pages, onReorder = onReorder, onRetake = onRetake, onRemove = onRemove)
        CaptureActionsRow(
            canCapture = cameraAvailable && permission.hasPermission,
            doneEnabled = pages.isNotEmpty(),
            onImport = onImport,
            onShutter = onShutter,
            onDone = onDone,
        )
    }
}

private fun requestBack(
    hasPages: Boolean,
    onCancel: () -> Unit,
    onShowDiscardConfirm: () -> Unit,
) {
    if (hasPages) onShowDiscardConfirm() else onCancel()
}

private fun discardSession(
    pages: List<CapturedPage>,
    onPagesChanged: (List<CapturedPage>) -> Unit,
    onCancel: () -> Unit,
) {
    pages.forEach { it.ownedFile?.delete() }
    onPagesChanged(emptyList())
    onCancel()
}

private fun removePage(
    id: String,
    pages: List<CapturedPage>,
    onPagesChanged: (List<CapturedPage>) -> Unit,
) {
    pages.firstOrNull { it.id == id }?.ownedFile?.delete()
    onPagesChanged(pages.filterNot { it.id == id })
}

private fun List<Uri>.toCapturedPages(): List<CapturedPage> =
    map { uri -> CapturedPage(id = UUID.randomUUID().toString(), uri = uri, ownedFile = null) }

/** Bundles Capture's stable, environment-level dependencies (as opposed to per-call state
 * like the current page list), so call sites don't have to thread all five through every
 * shutter press individually. */
private class CaptureEnvironment(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val imageCapture: ImageCapture,
    val captureDir: File,
)

private fun CaptureEnvironment.captureShot(
    pages: List<CapturedPage>,
    onPagesChanged: (List<CapturedPage>) -> Unit,
    retakeTargetId: String?,
    onRetakeHandled: () -> Unit,
) {
    val targetFile = File(captureDir, "${UUID.randomUUID()}.jpg")
    val id = retakeTargetId ?: UUID.randomUUID().toString()
    val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val newPage = CapturedPage(id = id, uri = Uri.fromFile(targetFile), ownedFile = targetFile)
                val existingIndex = pages.indexOfFirst { it.id == id }
                if (existingIndex >= 0) {
                    pages[existingIndex].ownedFile?.delete()
                    onPagesChanged(pages.toMutableList().also { it[existingIndex] = newPage })
                } else {
                    onPagesChanged(pages + newPage)
                }
                onRetakeHandled()
            }

            override fun onError(exception: ImageCaptureException) {
                onRetakeHandled()
                coroutineScope.launch { snackbarHostState.showSnackbar("Couldn't capture that page — try again.") }
            }
        },
    )
}

/** See specs/permissions-and-privacy.md#denied-or-restricted-permission. */
private class CameraPermissionState(
    initiallyGranted: Boolean,
    val onRequest: () -> Unit,
) {
    var hasPermission by mutableStateOf(initiallyGranted)
    var permanentlyDenied by mutableStateOf(false)
}

@Composable
private fun rememberCameraPermissionState(context: Context): CameraPermissionState {
    lateinit var state: CameraPermissionState
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            state.hasPermission = granted
            if (!granted) state.permanentlyDenied = !shouldShowCameraRationale(context)
        }
    state =
        remember {
            CameraPermissionState(hasCameraPermission(context)) { launcher.launch(Manifest.permission.CAMERA) }
        }
    RefreshPermissionOnResume(onRefresh = { state.hasPermission = hasCameraPermission(context) })
    return state
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun shouldShowCameraRationale(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Re-checks camera permission when the user returns from granting it in system Settings. */
@Composable
private fun RefreshPermissionOnResume(onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onRefresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView =
                PreviewView(ctx).apply {
                    // TextureView-backed rather than the default SurfaceView-backed mode: the
                    // latter renders via a hardware overlay that adb screencap (ScreenshotTest,
                    // manual verification) can't capture, showing solid black despite a working
                    // live feed on the device itself.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                { bindCamera(providerFuture.get(), lifecycleOwner, previewView, imageCapture) { cameraProvider = it } },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}

private fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture,
    onBound: (ProcessCameraProvider) -> Unit,
) {
    onBound(provider)
    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    runCatching {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Scan") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
private fun CaptureMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CameraPermissionRationale(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "KeepSheet needs camera access to scan pages. You can still add pages with Import below.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) { Text("Open app settings") }
        } else {
            Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Grant camera access") }
        }
    }
}

@Composable
private fun RetakeBanner(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.padding(8.dp), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Retaking — tap the shutter for a new shot", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private val THUMBNAIL_SIZE = 72.dp
private val THUMBNAIL_SPACING = 8.dp

/** Lets CaptureScreenTest count captured pages without depending on their (dynamic) ids. */
const val THUMBNAIL_TEST_TAG = "capture-thumbnail"

@Composable
private fun CaptureThumbnailStrip(
    pages: List<CapturedPage>,
    onReorder: (List<CapturedPage>) -> Unit,
    onRetake: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (pages.isEmpty()) {
        Text(
            "No pages yet — tap the shutter or Import to add one.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val density = LocalDensity.current
    val itemExtentPx = with(density) { (THUMBNAIL_SIZE + THUMBNAIL_SPACING).toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(THUMBNAIL_SPACING),
    ) {
        itemsIndexed(pages, key = { _, page -> page.id }) { _, page ->
            val isDragging = page.id == draggingId
            Box(
                modifier =
                    Modifier
                        .size(THUMBNAIL_SIZE)
                        .testTag(THUMBNAIL_TEST_TAG)
                        .graphicsLayer { translationX = if (isDragging) dragOffsetPx else 0f }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(page.id, pages) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = page.id
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val result =
                                        computeThumbnailDrag(dragAmount, dragOffsetPx, page, pages, itemExtentPx)
                                    dragOffsetPx = result.offsetPx
                                    result.reordered?.let(onReorder)
                                },
                            )
                        },
            ) {
                CaptureThumbnail(page = page, onRetake = { onRetake(page.id) }, onRemove = { onRemove(page.id) })
            }
        }
    }
}

private data class ThumbnailDragResult(
    val offsetPx: Float,
    val reordered: List<CapturedPage>?,
)

/** Pure so it's easy to reason about: given how far the drag has moved so far, decide
 * whether the dragged page has crossed into a neighboring slot and, if so, return pages
 * reordered accordingly (and the offset renormalized to that new slot). */
private fun computeThumbnailDrag(
    dragAmount: Offset,
    currentOffsetPx: Float,
    page: CapturedPage,
    pages: List<CapturedPage>,
    itemExtentPx: Float,
): ThumbnailDragResult {
    val newOffset = currentOffsetPx + dragAmount.x
    val currentIndex = pages.indexOfFirst { it.id == page.id }
    if (currentIndex == -1) return ThumbnailDragResult(newOffset, null)
    val slotShift = (newOffset / itemExtentPx).roundToInt()
    val targetIndex = (currentIndex + slotShift).coerceIn(0, pages.lastIndex)
    if (targetIndex == currentIndex) return ThumbnailDragResult(newOffset, null)
    val reordered = pages.toMutableList()
    val moved = reordered.removeAt(currentIndex)
    reordered.add(targetIndex, moved)
    return ThumbnailDragResult(newOffset - (targetIndex - currentIndex) * itemExtentPx, reordered)
}

@Composable
private fun CaptureThumbnail(
    page: CapturedPage,
    onRetake: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(THUMBNAIL_SIZE)) {
        AsyncImage(model = page.uri, contentDescription = null, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove page", tint = Color.White)
        }
        IconButton(
            onClick = onRetake,
            modifier = Modifier.align(Alignment.BottomEnd).size(24.dp).background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Retake page", tint = Color.White)
        }
    }
}

@Composable
private fun CaptureActionsRow(
    canCapture: Boolean,
    doneEnabled: Boolean,
    onImport: () -> Unit,
    onShutter: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        Button(onClick = onShutter, enabled = canCapture, modifier = Modifier.weight(1f)) { Text("Shutter") }
        Button(onClick = onDone, enabled = doneEnabled, modifier = Modifier.weight(1f)) { Text("Done") }
    }
}

@Composable
private fun DiscardSessionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard this scan?") },
        text = { Text("The pages you've captured so far will be lost.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep scanning") } },
    )
}
