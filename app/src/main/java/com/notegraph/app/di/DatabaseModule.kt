package com.notegraph.app.di

import android.content.Context
import androidx.room.Room
import com.notegraph.app.data.local.NoteGraphDatabase
import com.notegraph.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteGraphDatabase {
        return Room.databaseBuilder(
            context,
            NoteGraphDatabase::class.java,
            "notegraph.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides fun provideNoteDao(db: NoteGraphDatabase): NoteDao = db.noteDao()
    @Provides fun provideLinkDao(db: NoteGraphDatabase): LinkDao = db.linkDao()
    @Provides fun provideFolderDao(db: NoteGraphDatabase): FolderDao = db.folderDao()
    @Provides fun provideTagDao(db: NoteGraphDatabase): TagDao = db.tagDao()
}