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
}
