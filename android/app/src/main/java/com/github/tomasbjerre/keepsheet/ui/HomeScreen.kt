package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.tomasbjerre.keepsheet.data.Document
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import kotlinx.coroutines.launch

/**
 * Home screen (see specs/ui-flows.md#1-home). Capture, Merge, and Document
 * Detail aren't implemented yet (see the note in
 * android/app/build.gradle.kts for the libraries Capture needs), so their
 * actions here report that rather than doing nothing when tapped (#3).
 * Import (see specs/ui-flows.md#3-page-review) is implemented.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DocumentRepository,
    onPhotosSelectedForImport: (List<Uri>) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    val documentsFlow =
        remember(query) {
            if (query.isBlank()) repository.observeDocuments() else repository.search(query)
        }
    val documents by documentsFlow.collectAsState(initial = emptyList())

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) onPhotosSelectedForImport(uris)
        }

    fun notImplementedYet(action: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("$action isn't implemented yet.")
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("KeepSheet") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
        ) {
            HomeActionsRow(
                onScan = { notImplementedYet("Scan") },
                onImport = {
                    val imagesOnly = ActivityResultContracts.PickVisualMedia.ImageOnly
                    photoPickerLauncher.launch(PickVisualMediaRequest(imagesOnly))
                },
                onMerge = { notImplementedYet("Merge") },
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search documents") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            )

            if (documents.isEmpty()) {
                EmptyState(query)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(documents, key = { it.id }) { document ->
                        DocumentRow(document, onClick = { notImplementedYet("Opening a document") })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionsRow(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan") }
        Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        Button(onClick = onMerge, modifier = Modifier.weight(1f)) { Text("Merge") }
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (query.isBlank()) {
                "No documents yet — tap Scan to create your first PDF."
            } else {
                "No documents match \"$query\"."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    ) {
        Text(document.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${formatDate(document.createdAt)} · ${document.pageCount} pages · ${formatFileSize(document.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
