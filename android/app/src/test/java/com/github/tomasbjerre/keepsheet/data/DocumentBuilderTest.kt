package com.github.tomasbjerre.keepsheet.data

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
 * Verifies DocumentBuilder's ordering/naming/persistence logic against a
 * real in-memory database, with fake importPage/buildPdf standing in for
 * the real image decoding/PDF writing (which needs real Android graphics
 * APIs — see android/README.md#testing and pdf/ImagesToPdf.kt).
 */
@RunWith(RobolectricTestRunner::class)
class DocumentBuilderTest {
    private lateinit var database: KeepSheetDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var pagesDir: File
    private lateinit var documentsDir: File
    private val importedPages = mutableListOf<Pair<Int, File>>()
    private val builtPdfs = mutableListOf<Pair<List<String>, File>>()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KeepSheetDatabase::class.java)
                .build()
        repository = DocumentRepository(database.documentDao(), database.pageDao())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        pagesDir = File(context.cacheDir, "test-pages")
        documentsDir = File(context.cacheDir, "test-documents")
    }

    @After
    fun tearDown() {
        database.close()
        pagesDir.deleteRecursively()
        documentsDir.deleteRecursively()
    }

    private fun builder() =
        DocumentBuilder(
            repository = repository,
            pagesDir = pagesDir,
            documentsDir = documentsDir,
            importPage = { index, destination ->
                importedPages += index to destination
                destination.writeText("fake image $index")
            },
            buildPdf = { paths, destination ->
                builtPdfs += paths to destination
                destination.writeText("fake pdf")
                destination.length()
            },
        )

    @Test
    fun `builds a document with its pages in order`() =
        runTest {
            val documentId = builder().build(pageCount = 3, source = DocumentSource.SCANNED, finalizedAt = 1_000)

            val pages = repository.getPages(documentId)
            assertThat(pages.map { it.sequence }).containsExactly(0, 1, 2)
            assertThat(importedPages.map { it.first }).containsExactly(0, 1, 2)
        }

    @Test
    fun `refuses to build a document with zero pages`() =
        runTest {
            val result =
                runCatching { builder().build(pageCount = 0, source = DocumentSource.SCANNED, finalizedAt = 1_000) }

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `names the document using the fallback pattern and finalizes it`() =
        runTest {
            // See specs/file-naming.md#rules.
            val documentId = builder().build(pageCount = 1, source = DocumentSource.IMPORTED, finalizedAt = 1_000)

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.name).contains("_Document_Untitled")
            assertThat(document.source).isEqualTo(DocumentSource.IMPORTED)
            assertThat(document.pageCount).isEqualTo(1)
            assertThat(document.sizeBytes).isGreaterThan(0)
        }

    @Test
    fun `two documents finalized at the same moment get distinct names`() =
        runTest {
            val first = builder().build(pageCount = 1, source = DocumentSource.IMPORTED, finalizedAt = 1_000)
            val second = builder().build(pageCount = 1, source = DocumentSource.IMPORTED, finalizedAt = 1_000)

            val firstName = repository.observeDocument(first).first()!!.name
            val secondName = repository.observeDocument(second).first()!!.name
            assertThat(firstName).isNotEqualTo(secondName)
        }

    @Test
    fun `builds the pdf from every page's persisted image path, in order`() =
        runTest {
            builder().build(pageCount = 2, source = DocumentSource.SCANNED, finalizedAt = 1_000)

            val (paths, _) = builtPdfs.single()
            assertThat(paths).hasSize(2)
            assertThat(File(paths[0]).readText()).isEqualTo("fake image 0")
            assertThat(File(paths[1]).readText()).isEqualTo("fake image 1")
        }
}
