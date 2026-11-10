package com.notegraph.app.data.local.dao

import androidx.room.*
import com.notegraph.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrash = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllNotesOnce(): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesIncludingTrashOnce(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND title = :title LIMIT 1")
    suspend fun getNoteByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isTrash = 1 ORDER BY trashTimestamp DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unfileNotesInFolder(folderId: String)

    @Query("UPDATE notes SET isTrash = :isTrash, trashTimestamp = :timestamp, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun setTrashState(
        noteId: String,
        isTrash: Boolean,
        timestamp: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteCompletely(noteId: String)
    
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR blocksJson LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotesWithBlocks(query: String): Flow<List<NoteEntity>>
}
