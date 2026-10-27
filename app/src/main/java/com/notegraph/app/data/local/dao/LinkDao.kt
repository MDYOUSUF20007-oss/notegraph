package com.notegraph.app.data.local.dao

import androidx.room.*
import com.notegraph.app.data.local.entity.LinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links WHERE sourceNoteId = :noteId")
    fun getLinksForNote(noteId: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE targetNoteTitle = :title OR targetNoteId = :noteId")
    fun getBacklinksForNote(noteId: String, title: String): Flow<List<LinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("DELETE FROM links WHERE sourceNoteId = :noteId")
    suspend fun deleteLinksForNote(noteId: String)

    @Query("SELECT * FROM links")
    fun getAllLinks(): Flow<List<LinkEntity>>
}