package com.github.tomasbjerre.keepsheet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert
    suspend fun insert(document: Document): Long

    @Update
    suspend fun update(document: Document)

    @Delete
    suspend fun delete(document: Document)

    /** See specs/data-model.md#document-lifetime — cascades to delete every Page row
     * too (Page's foreign key is ON DELETE CASCADE), but not their image files; the
     * caller must delete those (and every pdfPath) first, while it still has the paths. */
    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    /** See specs/data-model.md#required-queries — most recent first. */
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): Document?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<Document?>

    /** See specs/data-model.md#required-queries and specs/ui-flows.md#1-home. */
    @Query(
        "SELECT * FROM documents WHERE name LIKE '%' || :term || '%' " +
            "OR searchText LIKE '%' || :term || '%' ORDER BY createdAt DESC",
    )
    fun search(term: String): Flow<List<Document>>
}
