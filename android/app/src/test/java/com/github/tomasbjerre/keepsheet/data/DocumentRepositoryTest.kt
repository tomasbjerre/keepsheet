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
 * Exercises the storage layer against a real in-memory SQLite database (via
 * Room + Robolectric) rather than mocked DAOs, so these tests verify the
 * actual queries required by specs/data-model.md#required-queries.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {
    private lateinit var database: KeepSheetDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KeepSheetDatabase::class.java)
                .build()
        repository = DocumentRepository(database.documentDao(), database.pageDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `pages are appended incrementally during capture, not only at the end`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)

            repository.appendPage(documentId, 0, "/tmp/page-0.jpg", PageFilter.BLACK_AND_WHITE)
            assertThat(repository.getPages(documentId)).hasSize(1)

            repository.appendPage(documentId, 1, "/tmp/page-1.jpg", PageFilter.BLACK_AND_WHITE)
            assertThat(repository.getPages(documentId)).hasSize(2)
        }

    @Test
    fun `documents are listed most recent first`() =
        runTest {
            val older = repository.createDocument(1_000, "Older", "/tmp/older.pdf", DocumentSource.SCANNED)
            val newer = repository.createDocument(2_000, "Newer", "/tmp/newer.pdf", DocumentSource.SCANNED)

            val documents = repository.observeDocuments().first()

            assertThat(documents.map { it.id }).containsExactly(newer, older)
        }

    @Test
    fun `a document's pages load in sequence order`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)
            // Insert out of sequence order to prove the query orders them, not the insert order.
            repository.appendPage(documentId, 2, "/tmp/page-2.jpg", PageFilter.COLOR)
            repository.appendPage(documentId, 0, "/tmp/page-0.jpg", PageFilter.COLOR)
            repository.appendPage(documentId, 1, "/tmp/page-1.jpg", PageFilter.COLOR)

            val pages = repository.getPages(documentId)

            assertThat(pages.map { it.sequence }).containsExactly(0, 1, 2)
        }

    @Test
    fun `finalizing a document persists its page count and size`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)

            repository.finalizeDocument(documentId, pageCount = 3, sizeBytes = 45_000)

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.pageCount).isEqualTo(3)
            assertThat(document.sizeBytes).isEqualTo(45_000L)
        }

    @Test
    fun `a document can be renamed`() =
        runTest {
            // See specs/ui-flows.md#5-document-detail and specs/file-naming.md#rules.
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)

            repository.renameDocument(documentId, "2026-09-25_Invoice_BlekingeBygg")

            val renamed = repository.observeDocument(documentId).first()!!
            assertThat(renamed.name).isEqualTo("2026-09-25_Invoice_BlekingeBygg")
        }

    @Test
    fun `recognized text becomes searchable once OCR finishes`() =
        runTest {
            // See specs/capture-and-processing.md#text-recognition-ocr — best-effort,
            // filled in asynchronously after the document already exists.
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)
            assertThat(repository.search("Blekinge").first()).isEmpty()

            repository.updateSearchText(documentId, "Invoice from Blekinge Bygg AB")

            assertThat(repository.search("Blekinge").first().map { it.id }).containsExactly(documentId)
        }

    @Test
    fun `search matches on name as well as recognized text`() =
        runTest {
            val documentId =
                repository.createDocument(1_000, "Blekinge Bygg invoice", "/tmp/invoice.pdf", DocumentSource.SCANNED)

            assertThat(repository.search("Blekinge").first().map { it.id }).containsExactly(documentId)
        }

    @Test
    fun `search is case-insensitive`() =
        runTest {
            val documentId =
                repository.createDocument(1_000, "Blekinge Bygg invoice", "/tmp/invoice.pdf", DocumentSource.SCANNED)

            assertThat(repository.search("blekinge").first().map { it.id }).containsExactly(documentId)
        }

    @Test
    fun `search excludes documents that match neither name nor recognized text`() =
        runTest {
            repository.createDocument(1_000, "Blekinge Bygg invoice", "/tmp/invoice.pdf", DocumentSource.SCANNED)

            assertThat(repository.search("Volvo").first()).isEmpty()
        }

    @Test
    fun `deleting a document removes its pages too`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)
            repository.appendPage(documentId, 0, "/tmp/page-0.jpg", PageFilter.COLOR)
            val document = repository.observeDocument(documentId).first()!!

            repository.deleteDocument(document)

            assertThat(repository.getPages(documentId)).isEmpty()
            assertThat(repository.observeDocument(documentId).first()).isNull()
        }

    @Test
    fun `deleting a document removes its underlying pdf and page image files`() =
        runTest {
            // See specs/permissions-and-privacy.md#data-handling: "no orphaned data left behind".
            val pdfFile = File.createTempFile("document", ".pdf")
            val pageFile = File.createTempFile("page", ".jpg")
            val documentId = repository.createDocument(1_000, "Untitled", pdfFile.absolutePath, DocumentSource.SCANNED)
            repository.appendPage(documentId, 0, pageFile.absolutePath, PageFilter.COLOR)
            val document = repository.observeDocument(documentId).first()!!

            repository.deleteDocument(document)

            assertThat(pdfFile).doesNotExist()
            assertThat(pageFile).doesNotExist()
        }

    @Test
    fun `a document can be deleted by id`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)

            repository.deleteDocumentById(documentId)

            assertThat(repository.observeDocument(documentId).first()).isNull()
        }

    @Test
    fun `a merged document has no pages of its own`() =
        runTest {
            // See specs/merging.md#result — pages come from the source PDFs directly,
            // not re-derived as Page rows.
            val documentId = repository.createDocument(1_000, "Merged", "/tmp/merged.pdf", DocumentSource.MERGED)

            assertThat(repository.getPages(documentId)).isEmpty()
        }
}
