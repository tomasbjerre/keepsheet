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
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.pdf.copyImageForPage
import com.github.tomasbjerre.keepsheet.pdf.defaultFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reached from Capture's Done action or Home's Import (see
 * specs/ui-flows.md#3-page-review). Filter picking is here; crop adjustment,
 * reorder, and retake aren't implemented here yet — Capture's own
 * thumbnail strip offers reorder/retake for a page before it ever reaches
 * this screen, but per spec those same actions belong here too (still to
 * do, alongside crop/filter — see android/app/build.gradle.kts).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { PageReviewTopBar(enabled = !saving, onBack = onCancel) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
        ) {
            Text("${pages.size} page(s)")
            PageThumbnails(pages, selected, onSelect = { selected = it })
            if (pages.isNotEmpty()) {
                FilterPicker(
                    current = filters[selected],
                    onPick = { picked -> filters = filters.toMutableList().also { it[selected] = picked } },
                    onApplyToAll = { filters = List(pages.size) { filters[selected] } },
                )
            }
            SaveButton(
                enabled = pages.isNotEmpty() && !saving,
                saving = saving,
                onClick = {
                    saving = true
                    coroutineScope.launch {
                        val documentId =
                            saveAsDocument(pages, filters, source, repository, filesDir, context.contentResolver)
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
                copyImageForPage(contentResolver, pages[index], destination, filter)
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
