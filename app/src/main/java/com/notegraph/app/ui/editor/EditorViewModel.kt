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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
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

    private val json = Json { ignoreUnknownKeys = true }

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    /** Parsed multimedia blocks derived from the note's blocksJson. */
    private val _blocks = MutableStateFlow<List<NoteBlock>>(emptyList())
    val blocks: StateFlow<List<NoteBlock>> = _blocks.asStateFlow()

    /** Timestamp (System.currentTimeMillis) when an audio recording was started. */
    private val _audioRecordStartMs = MutableStateFlow(0L)
    val audioRecordStartMs: StateFlow<Long> = _audioRecordStartMs.asStateFlow()

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

    // ─── Block manipulation ──────────────────────────────────────

    fun addTextBlock() {
        val newBlock = NoteBlock.TextBlock(id = UUID.randomUUID().toString())
        appendBlock(newBlock)
    }

    fun addInkBlock() {
        val newBlock = NoteBlock.InkBlock(id = UUID.randomUUID().toString())
        appendBlock(newBlock)
    }

    fun addAudioBlock() {
        val blockId = UUID.randomUUID().toString()
        val filePath = generateAudioFilePath(appContext, blockId)
        val newBlock = NoteBlock.AudioBlock(id = blockId, filePath = filePath)
        appendBlock(newBlock)
    }

    fun addImageBlock() {
        val newBlock = NoteBlock.ImageBlock(id = UUID.randomUUID().toString())
        appendBlock(newBlock)
    }

    fun updateBlock(updated: NoteBlock) {
        _blocks.value = _blocks.value.map { if (it.id == updated.id) updated else it }
        persistBlocks()
    }

    fun removeBlock(blockId: String) {
        _blocks.value = _blocks.value.filter { it.id != blockId }
        persistBlocks()
    }

    fun onAudioRecordingStarted(startMs: Long) {
        _audioRecordStartMs.value = startMs
    }

    fun onAudioRecordingStopped() {
        _audioRecordStartMs.value = 0L
    }

    /**
     * Captures the timestamp-offset for a text block recorded while audio is playing.
     * Called from EditorScreen when the user types inside a text block during recording.
     */
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

    // ─── Private helpers ──────────────────────────────────────

    private fun appendBlock(block: NoteBlock) {
        _blocks.value = _blocks.value + block
        persistBlocks()
    }

    private fun persistBlocks() {
        _note.value?.let { current ->
            val blocksJsonStr = json.encodeToString(_blocks.value)
            val updated = current.copy(
                blocksJson = blocksJsonStr,
                updatedAt = System.currentTimeMillis()
            )
            _note.value = updated
            viewModelScope.launch { noteDao.updateNote(updated) }
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
