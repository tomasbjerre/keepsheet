package com.github.tomasbjerre.keepsheet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PageDao {
    @Insert
    suspend fun insert(page: Page): Long

    /** See specs/data-model.md#required-queries — sequence order, not insert order. */
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY sequence ASC")
    suspend fun getForDocument(documentId: Long): List<Page>

    /** See specs/capture-and-processing.md#text-recognition-ocr. */
    @Query("UPDATE pages SET searchText = :searchText WHERE id = :pageId")
    suspend fun updateSearchText(
        pageId: Long,
        searchText: String?,
    )

    /** See specs/data-model.md#document-lifetime — every page's image file must be
     * deleted before the rows are, so this reads them all first. */
    @Query("SELECT * FROM pages")
    suspend fun getAll(): List<Page>
}
