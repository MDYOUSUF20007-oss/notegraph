package com.notegraph.app.data.local.dao

import androidx.room.*
import com.notegraph.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrash = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isTrash = 1 ORDER BY trashTimestamp DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isTrash = :isTrash, trashTimestamp = :timestamp WHERE id = :noteId")
    suspend fun setTrashState(noteId: String, isTrash: Boolean, timestamp: Long?)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteCompletely(noteId: String)
    
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<NoteEntity>>
}