package com.notegraph.app.ui.settings

import androidx.lifecycle.ViewModel
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteDao: NoteDao
) : ViewModel() {

    suspend fun getAllNotesOnce(): List<NoteEntity> {
        return noteDao.getAllNotesOnce()
    }

    suspend fun restoreNotes(notes: List<NoteEntity>) {
        for (note in notes) {
            noteDao.insertNote(note)
        }
    }
}
