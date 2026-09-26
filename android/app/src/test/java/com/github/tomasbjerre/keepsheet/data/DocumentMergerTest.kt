package com.github.tomasbjerre.keepsheet.data

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Verifies DocumentMerger's naming/persistence logic against a real in-memory database,
 * with a fake mergePdfs/countPages standing in for the real PdfBox-Android merge (which
 * needs real Android APIs — see android/README.md#testing and pdf/PdfMerger.kt).
 */
@RunWith(RobolectricTestRunner::class)
class DocumentMergerTest {
    private lateinit var database: KeepSheetDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var documentsDir: File
    private val mergedSources = mutableListOf<Pair<List<Uri>, File>>()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KeepSheetDatabase::class.java)
                .build()
        repository = DocumentRepository(database.documentDao(), database.pageDao())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        documentsDir = File(context.cacheDir, "test-documents")
    }

    @After
    fun tearDown() {
        database.close()
        documentsDir.deleteRecursively()
    }

    private fun merger(pageCount: Int = 5) =
        DocumentMerger(
            repository = repository,
            documentsDir = documentsDir,
            mergePdfs = { sources, destination ->
                mergedSources += sources to destination
                destination.writeText("fake merged pdf")
                destination.length()
            },
            countPages = { pageCount },
        )

    private fun uris(count: Int) = (0 until count).map { Uri.parse("content://source/$it") }

    @Test
    fun `refuses to merge fewer than two files`() =
        runTest {
            val result = runCatching { merger().merge(sources = uris(1), mergedAt = 1_000) }

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `merges the given sources in order into one MERGED document`() =
        runTest {
            val sources = uris(3)

            val documentId = merger().merge(sources, mergedAt = 1_000)

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.source).isEqualTo(DocumentSource.MERGED)
            assertThat(mergedSources.single().first).isEqualTo(sources)
        }

    @Test
    fun `a merged document has no pages of its own`() =
        runTest {
            // See specs/data-model.md#page.
            val documentId = merger().merge(uris(2), mergedAt = 1_000)

            assertThat(repository.getPages(documentId)).isEmpty()
        }

    @Test
    fun `finalizes the document with the merged page count and size`() =
        runTest {
            val documentId = merger(pageCount = 7).merge(uris(2), mergedAt = 1_000)

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.pageCount).isEqualTo(7)
            assertThat(document.sizeBytes).isGreaterThan(0)
        }

    @Test
    fun `names the document using the fallback pattern`() =
        runTest {
            // See specs/file-naming.md#rules and specs/merging.md#result.
            val documentId = merger().merge(uris(2), mergedAt = 1_000)

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.name).contains("_Document_Untitled")
        }
}
