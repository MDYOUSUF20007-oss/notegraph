package com.notegraph.app.data.local.dao

import androidx.room.*
import com.notegraph.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE noteId = :noteId")
    suspend fun deleteTagsForNote(noteId: String)
}