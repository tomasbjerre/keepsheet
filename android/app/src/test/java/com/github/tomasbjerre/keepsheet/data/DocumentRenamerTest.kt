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

/**
 * See specs/file-naming.md#rules and specs/ui-flows.md#5-document-detail: a user rename
 * goes through the same sanitizing/de-duplicating [renameDocument] wrapper an
 * auto-suggested name does, rather than [DocumentRepository.renameDocument] directly (which
 * just persists whatever it's given).
 */
@RunWith(RobolectricTestRunner::class)
class DocumentRenamerTest {
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
    fun `strips characters illegal in file names`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Untitled", "/tmp/untitled.pdf", DocumentSource.SCANNED)

            renameDocument(repository, documentId, "Invoice: Blekinge/Bygg?")

            val renamed = repository.observeDocument(documentId).first()!!
            assertThat(renamed.name).isEqualTo("Invoice BlekingeBygg")
        }

    @Test
    fun `appends a numeric suffix when the new name collides with another document`() =
        runTest {
            repository.createDocument(1_000, "Invoice", "/tmp/a.pdf", DocumentSource.SCANNED)
            val documentId = repository.createDocument(2_000, "Untitled", "/tmp/b.pdf", DocumentSource.SCANNED)

            renameDocument(repository, documentId, "Invoice")

            val renamed = repository.observeDocument(documentId).first()!!
            assertThat(renamed.name).isEqualTo("Invoice (2)")
        }

    @Test
    fun `does not collide with its own current name`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Invoice", "/tmp/a.pdf", DocumentSource.SCANNED)

            renameDocument(repository, documentId, "Invoice")

            val renamed = repository.observeDocument(documentId).first()!!
            assertThat(renamed.name).isEqualTo("Invoice")
        }

    @Test
    fun `a blank or fully-illegal name is ignored rather than clearing the document's name`() =
        runTest {
            val documentId = repository.createDocument(1_000, "Invoice", "/tmp/a.pdf", DocumentSource.SCANNED)

            renameDocument(repository, documentId, "   ")

            val document = repository.observeDocument(documentId).first()!!
            assertThat(document.name).isEqualTo("Invoice")
        }
}
