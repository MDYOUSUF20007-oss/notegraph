package com.notegraph.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val type: String, // TEXT, INK, IMAGE, AUDIO
    val data: String, // Serialized JSON or paths
    val orderIndex: Int,
    val audioTimestampMs: Long? = null
)