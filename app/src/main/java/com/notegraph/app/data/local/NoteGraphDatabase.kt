package com.notegraph.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.notegraph.app.data.local.dao.*
import com.notegraph.app.data.local.entity.*

@Database(
    entities = [
        NoteEntity::class,
        BlockEntity::class,
        LinkEntity::class,
        TagEntity::class,
        PropertyEntity::class,
        FolderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoteGraphDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun linkDao(): LinkDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
}