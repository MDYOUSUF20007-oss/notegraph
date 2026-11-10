package com.notegraph.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT NOT NULL DEFAULT '📁'")
            db.execSQL("ALTER TABLE folders ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#A855F7'")
            db.execSQL("ALTER TABLE folders ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes (folderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isTrash ON notes (isTrash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes (updatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_links_sourceNoteId ON links (sourceNoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_links_targetNoteId ON links (targetNoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_links_targetNoteTitle ON links (targetNoteTitle)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteGraphDatabase {
        return Room.databaseBuilder(
            context,
            NoteGraphDatabase::class.java,
            "notegraph.db"
        )
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideNoteDao(db: NoteGraphDatabase): NoteDao = db.noteDao()
    @Provides fun provideLinkDao(db: NoteGraphDatabase): LinkDao = db.linkDao()
    @Provides fun provideFolderDao(db: NoteGraphDatabase): FolderDao = db.folderDao()
    @Provides fun provideTagDao(db: NoteGraphDatabase): TagDao = db.tagDao()
}
