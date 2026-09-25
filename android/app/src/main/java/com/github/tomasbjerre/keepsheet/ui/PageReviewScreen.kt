package com.github.tomasbjerre.keepsheet.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.tomasbjerre.keepsheet.data.DocumentBuilder
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.pdf.copyImageForPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reached from Capture's Done action or Home's Import (see
 * specs/ui-flows.md#3-page-review). Crop adjustment, filter picking,
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
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

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
            PageThumbnails(pages)
            SaveButton(
                enabled = pages.isNotEmpty() && !saving,
                saving = saving,
                onClick = {
                    saving = true
                    coroutineScope.launch {
                        saveAsDocument(pages, source, repository, filesDir, context.contentResolver)
                        saving = false
                        onSaved()
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
private fun PageThumbnails(pages: List<Uri>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pages) { uri ->
            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(96.dp))
        }
    }
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
    source: DocumentSource,
    repository: DocumentRepository,
    filesDir: File,
    contentResolver: ContentResolver,
) {
    val builder =
        DocumentBuilder(
            repository = repository,
            pagesDir = File(filesDir, "pages"),
            documentsDir = File(filesDir, "documents"),
            importPage = { index, destination -> copyImageForPage(contentResolver, pages[index], destination) },
            buildPdf = { imagePaths, destination -> buildPdfFromImages(imagePaths, destination) },
        )
    withContext(Dispatchers.IO) {
        builder.build(
            pageCount = pages.size,
            source = source,
            finalizedAt = System.currentTimeMillis(),
        )
    }
}
