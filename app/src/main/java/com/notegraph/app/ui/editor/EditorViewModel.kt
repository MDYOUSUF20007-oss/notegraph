package com.notegraph.app.ui.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val linkDao: LinkDao,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

    val json = Json { ignoreUnknownKeys = true }

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    private val _blocks = MutableStateFlow<List<NoteBlock>>(emptyList())
    val blocks: StateFlow<List<NoteBlock>> = _blocks.asStateFlow()

    private val _audioRecordStartMs = MutableStateFlow(0L)
    val audioRecordStartMs: StateFlow<Long> = _audioRecordStartMs.asStateFlow()

    /** One-shot event flow for showing "Note saved" Snackbar or Toast. */
    private val _saveEvent = MutableSharedFlow<String>()
    val saveEvent: SharedFlow<String> = _saveEvent.asSharedFlow()

    private var debouncedSaveJob: Job? = null

    val backlinks: StateFlow<List<LinkEntity>> = _note.flatMapLatest { currentNote ->
        if (currentNote != null) {
            linkDao.getBacklinksForNote(currentNote.id, currentNote.title)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = noteDao.getNoteById(noteId)
            _note.value = loaded
            loaded?.let {
                _blocks.value = try {
                    json.decodeFromString<List<NoteBlock>>(it.blocksJson)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    // ─── Public Note Editing Actions ─────────────────────────────

    fun updateTitle(newTitle: String) {
        _note.value?.let { current ->
            _note.value = current.copy(title = newTitle, updatedAt = System.currentTimeMillis())
            scheduleDebouncedSave()
        }
    }

    fun updateContent(newContent: String) {
        _note.value?.let { current ->
            _note.value = current.copy(content = newContent, updatedAt = System.currentTimeMillis())
            scheduleDebouncedSave()
            viewModelScope.launch { parseAndSaveWikiLinks(noteId, newContent) }
        }
    }

    // ─── Block Manipulation ──────────────────────────────────────

    fun addTextBlock() {
        appendBlock(NoteBlock.TextBlock(id = UUID.randomUUID().toString()))
    }

    fun addInkBlock() {
        appendBlock(NoteBlock.InkBlock(id = UUID.randomUUID().toString()))
    }

    fun addAudioBlock() {
        val blockId = UUID.randomUUID().toString()
        val filePath = generateAudioFilePath(appContext, blockId)
        appendBlock(NoteBlock.AudioBlock(id = blockId, filePath = filePath))
    }

    fun addImageBlock() {
        appendBlock(NoteBlock.ImageBlock(id = UUID.randomUUID().toString()))
    }

    fun updateBlock(updated: NoteBlock) {
        _blocks.value = _blocks.value.map { if (it.id == updated.id) updated else it }
        syncBlocksToNote()
        scheduleDebouncedSave()
    }

    fun removeBlock(blockId: String) {
        _blocks.value = _blocks.value.filter { it.id != blockId }
        syncBlocksToNote()
        scheduleDebouncedSave()
    }

    fun onAudioRecordingStarted(startMs: Long) {
        _audioRecordStartMs.value = startMs
    }

    fun onAudioRecordingStopped() {
        _audioRecordStartMs.value = 0L
    }

    fun stampTextBlockWithAudioTime(blockId: String) {
        val startMs = _audioRecordStartMs.value
        if (startMs <= 0L) return
        val offsetMs = System.currentTimeMillis() - startMs
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.TextBlock && block.audioTimestampMs == null) {
            updateBlock(block.copy(audioTimestampMs = offsetMs))
        }
    }

    fun updateInkStrokes(blockId: String, strokes: List<InkStroke>) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.InkBlock) {
            val strokesJson = json.encodeToString(strokes)
            updateBlock(block.copy(strokesJson = strokesJson))
        }
    }

    /**
     * Immediately persists note and blocks to Room.
     * Used by explicit Save button and onBack navigation (DisposableEffect/BackHandler).
     */
    fun saveNow(showEvent: Boolean = false) {
        debouncedSaveJob?.cancel()
        syncBlocksToNote()
        val currentNote = _note.value ?: return
        viewModelScope.launch {
            noteDao.updateNote(currentNote)
            parseAndSaveWikiLinks(currentNote.id, currentNote.content)
            if (showEvent) {
                _saveEvent.emit("Note saved")
            }
        }
    }

    // ─── Private Helpers ─────────────────────────────────────────

    private fun appendBlock(block: NoteBlock) {
        _blocks.value = _blocks.value + block
        syncBlocksToNote()
        scheduleDebouncedSave()
    }

    private fun syncBlocksToNote() {
        _note.value?.let { current ->
            val blocksJsonStr = json.encodeToString(_blocks.value)
            _note.value = current.copy(
                blocksJson = blocksJsonStr,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun scheduleDebouncedSave() {
        debouncedSaveJob?.cancel()
        debouncedSaveJob = viewModelScope.launch {
            delay(500L)
            syncBlocksToNote()
            _note.value?.let { note ->
                noteDao.updateNote(note)
            }
        }
    }

    private suspend fun parseAndSaveWikiLinks(noteId: String, content: String) {
        val regex = """\[\[(.*?)]]""".toRegex()
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
