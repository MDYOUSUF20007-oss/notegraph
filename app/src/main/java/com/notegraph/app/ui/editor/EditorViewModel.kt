package com.notegraph.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val linkDao: LinkDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    val backlinks: StateFlow<List<LinkEntity>> = _note.flatMapLatest { currentNote ->
        if (currentNote != null) {
            linkDao.getBacklinksForNote(currentNote.id, currentNote.title)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _note.value = noteDao.getNoteById(noteId)
        }
    }

    fun updateTitle(newTitle: String) {
        _note.value?.let { current ->
            val updated = current.copy(title = newTitle, updatedAt = System.currentTimeMillis())
            _note.value = updated
            viewModelScope.launch { noteDao.updateNote(updated) }
        }
    }

    fun updateContent(newContent: String) {
        _note.value?.let { current ->
            val updated = current.copy(content = newContent, updatedAt = System.currentTimeMillis())
            _note.value = updated
            viewModelScope.launch {
                noteDao.updateNote(updated)
                parseAndSaveWikiLinks(current.id, newContent)
            }
        }
    }

    private suspend fun parseAndSaveWikiLinks(noteId: String, content: String) {
        val regex = "\[\[(.*?)]]".toRegex()
        val matches = regex.findAll(content)
        val links = matches.map { match ->
            val targetTitle = match.groupValues[1]
            LinkEntity(
                sourceNoteId = noteId,
                targetNoteTitle = targetTitle,
                targetNoteId = null,
                isResolved = false
            )
        }.toList()

        linkDao.deleteLinksForNote(noteId)
        if (links.isNotEmpty()) {
            linkDao.insertLinks(links)
        }
    }
}