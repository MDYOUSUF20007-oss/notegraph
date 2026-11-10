package com.notegraph.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    @ColumnInfo(defaultValue = "📁")
    val icon: String = "📁",
    @ColumnInfo(defaultValue = "#A855F7")
    val colorHex: String = "#A855F7",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
