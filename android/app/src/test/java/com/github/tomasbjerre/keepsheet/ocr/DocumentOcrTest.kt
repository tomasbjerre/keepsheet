package com.github.tomasbjerre.keepsheet.ocr

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.data.KeepSheetDatabase
import com.github.tomasbjerre.keepsheet.data.PageFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** See specs/capture-and-processing.md#text-recognition-ocr, against a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class DocumentOcrTest {
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
    fun tearDown() = database.close()

    private suspend fun documentWithPages(vararg paths: String): Long {
        val id = repository.createDocument(1_790_424_000_000L, "doc", "doc.pdf", DocumentSource.SCANNED)
        paths.forEachIndexed { i, p -> repository.appendPage(id, i, p, PageFilter.COLOR) }
        return id
    }

    @Test
    fun `stores per-page text and the concatenated document text, searchable afterwards`() =
        runTest {
            val id = documentWithPages("a.jpg", "b.jpg")
            val texts = mapOf("a.jpg" to "Faktura Bleking", "b.jpg" to "Summa 100 kr")

            recognizeDocument(repository, { texts[it] }, id)

            assertThat(repository.getPages(id).map { it.searchText })
                .containsExactly("Faktura Bleking", "Summa 100 kr")
            assertThat(repository.observeDocument(id).first()!!.searchText)
                .isEqualTo("Faktura Bleking\nSumma 100 kr")
            assertThat(repository.search("Summa").first().map { it.id }).containsExactly(id)
        }

    @Test
    fun `a page with no text keeps null and never fails the document`() =
        runTest {
            val id = documentWithPages("a.jpg", "blank.jpg")

            recognizeDocument(repository, { if (it == "a.jpg") "Hello" else "  " }, id)

            assertThat(repository.getPages(id).map { it.searchText }).containsExactly("Hello", null)
            assertThat(repository.observeDocument(id).first()!!.searchText).isEqualTo("Hello")
        }

    @Test
    fun `a failing recognizer leaves searchText null instead of throwing`() =
        runTest {
            val id = documentWithPages("a.jpg")

            val result = recognizeDocument(repository, { error("boom") }, id)

            assertThat(result).isNull()
            assertThat(repository.observeDocument(id).first()!!.searchText).isNull()
        }

    @Test
    fun `device languages map to bundled Tesseract languages with English fallback`() {
        assertThat(tesseractLanguages(listOf("sv", "en"))).isEqualTo("swe+eng")
        assertThat(tesseractLanguages(listOf("sv-SE"))).isEqualTo("swe")
        assertThat(tesseractLanguages(listOf("ja"))).isEqualTo("eng")
    }

    @Test
    fun `finishing recognition renames the document from the recognized text`() =
        runTest {
            val id = documentWithPages("a.jpg")

            recognizeDocument(repository, { "Blekinge Bygg AB\nFaktura\n2026-01-02" }, id)

            assertThat(repository.observeDocument(id).first()!!.name).isEqualTo("2026-01-02_Invoice_BlekingeBygg")
        }
}
