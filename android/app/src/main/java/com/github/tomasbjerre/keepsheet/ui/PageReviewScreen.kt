package com.github.tomasbjerre.keepsheet.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.tomasbjerre.keepsheet.data.DocumentBuilder
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.pdf.Corners
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.pdf.copyImageForPage
import com.github.tomasbjerre.keepsheet.pdf.defaultFilter
import com.github.tomasbjerre.keepsheet.pdf.detectCornersInImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reached from Capture's Done action or Home's Import (see
 * specs/ui-flows.md#3-page-review). Filter picking and crop adjustment are here;
 * reorder, and retake aren't implemented here yet — Capture's own
 * thumbnail strip offers reorder/retake for a page before it ever reaches
 * this screen, but per spec those same actions belong here too (still to
 * do, alongside crop/filter — see android/app/build.gradle.kts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // Compose screen: state hoisting keeps this one flat function readable.
@Composable
fun PageReviewScreen(
    pages: List<Uri>,
    source: DocumentSource,
    repository: DocumentRepository,
    filesDir: File,
    onSaved: (documentId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(List(pages.size) { defaultFilter(null) }) }
    var selected by remember { mutableIntStateOf(0) }
    var corners by remember { mutableStateOf<List<Corners?>>(List(pages.size) { null }) }
    var detecting by remember { mutableStateOf(true) }
    LaunchedEffect(pages) {
        corners = detectAll(context.contentResolver, pages)
        detecting = false
    }

    Scaffold(
        topBar = { PageReviewTopBar(enabled = !saving, onBack = onCancel) },
        bottomBar = {
            SaveButton(
                enabled = pages.isNotEmpty() && !saving && !detecting,
                saving = saving,
                onClick = {
                    saving = true
                    coroutineScope.launch {
                        val documentId =
                            saveAsDocument(
                                pages,
                                filters,
                                corners,
                                source,
                                repository,
                                filesDir,
                                context.contentResolver,
                            )
                        // Explicit, rather than relying on withContext(Dispatchers.IO) above
                        // to hand back to whatever dispatched this coroutine: onSaved()
                        // navigates, and NavController requires the main thread for that.
                        withContext(Dispatchers.Main) {
                            saving = false
                            onSaved(documentId)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text("${pages.size} page(s)")
            PageThumbnails(pages, selected, onSelect = { selected = it })
            if (pages.isNotEmpty()) {
                PageEditor(
                    uri = pages[selected],
                    corners = corners[selected],
                    filter = filters[selected],
                    detecting = detecting,
                    onCornersChange = { corners = corners.toMutableList().also { list -> list[selected] = it } },
                    onRedetect = {
                        redetect(coroutineScope, context.contentResolver, pages[selected]) { found ->
                            corners = corners.toMutableList().also { list -> list[selected] = found }
                        }
                    },
                    onPickFilter = { picked -> filters = filters.toMutableList().also { it[selected] = picked } },
                    onApplyFilterToAll = { filters = List(pages.size) { filters[selected] } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReviewTopBar(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text("Review pages") },
        navigationIcon = {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Composable
private fun PageThumbnails(
    pages: List<Uri>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(pages) { index, uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Page ${index + 1}",
                modifier =
                    Modifier
                        .size(96.dp)
                        .border(if (index == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary)
                        .clickable { onSelect(index) },
            )
        }
    }
}

/**
 * See specs/capture-and-processing.md#automatic-cropping-and-straightening: the detected
 * crop is only a starting point the user reviews (and adjusts) in Page Review.
 */
private suspend fun detectAll(
    resolver: ContentResolver,
    pages: List<Uri>,
): List<Corners?> = withContext(Dispatchers.IO) { pages.map { detectCornersInImage(resolver, it) } }

private fun redetect(
    scope: CoroutineScope,
    resolver: ContentResolver,
    uri: Uri,
    onFound: (Corners?) -> Unit,
) {
    scope.launch { onFound(withContext(Dispatchers.IO) { detectCornersInImage(resolver, uri) }) }
}

@Composable
private fun PageEditor(
    uri: Uri,
    corners: Corners?,
    filter: PageFilter,
    detecting: Boolean,
    onCornersChange: (Corners?) -> Unit,
    onRedetect: () -> Unit,
    onPickFilter: (PageFilter) -> Unit,
    onApplyFilterToAll: () -> Unit,
) {
    CropSection(uri, corners, detecting, onCornersChange, onRedetect)
    FilterPicker(current = filter, onPick = onPickFilter, onApplyToAll = onApplyFilterToAll)
}

@Composable
private fun CropSection(
    uri: Uri,
    corners: Corners?,
    detecting: Boolean,
    onCornersChange: (Corners?) -> Unit,
    onRedetect: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        CropEditor(uri = uri, corners = corners, onCornersChange = { onCornersChange(it) })
        Text(
            when {
                detecting -> "Looking for the page edges…"
                corners == null -> "No page edges found — using the full photo."
                else -> "Drag the corners to adjust the crop."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRedetect, enabled = !detecting) { Text("Detect edges") }
            TextButton(onClick = { onCornersChange(Corners.inset()) }, enabled = !detecting && corners == null) {
                Text("Crop manually")
            }
            TextButton(onClick = { onCornersChange(null) }, enabled = corners != null) { Text("Full photo") }
        }
    }
}

@Composable
private fun FilterPicker(
    current: PageFilter,
    onPick: (PageFilter) -> Unit,
    onApplyToAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageFilter.entries.forEach { filter ->
            FilterChip(selected = filter == current, onClick = { onPick(filter) }, label = { Text(filter.label()) })
        }
    }
    TextButton(onClick = onApplyToAll) { Text("Apply to all pages") }
}

private fun PageFilter.label() =
    when (this) {
        PageFilter.COLOR -> "Color"
        PageFilter.GRAYSCALE -> "Grayscale"
        PageFilter.BLACK_AND_WHITE -> "Black & white"
    }

@Composable
private fun SaveButton(
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text("Save")
        }
    }
}

private suspend fun saveAsDocument(
    pages: List<Uri>,
    filters: List<PageFilter>,
    corners: List<Corners?>,
    source: DocumentSource,
    repository: DocumentRepository,
    filesDir: File,
    contentResolver: ContentResolver,
): Long {
    val builder =
        DocumentBuilder(
            repository = repository,
            pagesDir = File(filesDir, "pages"),
            documentsDir = File(filesDir, "documents"),
            importPage = { index, filter, destination ->
                copyImageForPage(contentResolver, pages[index], destination, filter, corners[index])
            },
            buildPdf = { imagePaths, destination -> buildPdfFromImages(imagePaths, destination) },
        )
    return withContext(Dispatchers.IO) {
        builder.build(
            pageCount = pages.size,
            filters = filters,
            source = source,
            finalizedAt = System.currentTimeMillis(),
        )
    }
}
