package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import java.io.File

private const val ROUTE_HOME = "home"
private const val ROUTE_PAGE_REVIEW = "pageReview"

/** See specs/ui-flows.md#navigation. Only Home and Page Review exist so far. */
@Composable
fun KeepSheetApp(
    repository: DocumentRepository,
    filesDir: File,
) {
    val navController = rememberNavController()
    var pendingPages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                repository = repository,
                onPhotosSelectedForImport = { uris ->
                    pendingPages = uris
                    navController.navigate(ROUTE_PAGE_REVIEW)
                },
            )
        }
        composable(ROUTE_PAGE_REVIEW) {
            PageReviewScreen(
                pages = pendingPages,
                repository = repository,
                filesDir = filesDir,
                onSaved = {
                    pendingPages = emptyList()
                    // Should land on the new document's Document Detail screen (see
                    // specs/ui-flows.md#3-page-review) — not implemented yet, so this
                    // returns to Home instead, where the new document is now visible.
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
                onCancel = {
                    pendingPages = emptyList()
                    navController.popBackStack()
                },
            )
        }
    }
}
