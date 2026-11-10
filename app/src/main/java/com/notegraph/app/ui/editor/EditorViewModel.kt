package com.notegraph.app.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class BacklinkItem(
    val sourceNoteId: String,
    val sourceNoteTitle: String
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val linkDao: LinkDao,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

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

    val allNotes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingLinks: StateFlow<List<LinkEntity>> = linkDao.getLinksForNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backlinks: StateFlow<List<BacklinkItem>> = _note.flatMapLatest { currentNote ->
        if (currentNote != null) {
            combine(
                linkDao.getBacklinksForNote(currentNote.id, currentNote.title),
                noteDao.getAllNotes()
            ) { links, allNotes ->
                val noteMap = allNotes.associateBy { it.id }
                val resultList = mutableListOf<BacklinkItem>()
                val seenSourceIds = mutableSetOf<String>()

                // 1. From database LinkEntity table
                for (link in links) {
                    val src = noteMap[link.sourceNoteId]
                    if (src != null && src.id != currentNote.id && seenSourceIds.add(src.id)) {
                        resultList.add(
                            BacklinkItem(
                                sourceNoteId = src.id,
                                sourceNoteTitle = src.title.ifBlank { "Untitled" }
                            )
                        )
                    }
                }

                // 2. Direct mentions across all existing notes
                val targetTitle = currentNote.title.trim()
                if (targetTitle.isNotBlank() &&
                    !targetTitle.equals("Untitled", ignoreCase = true) &&
                    !targetTitle.equals("Untitled Note", ignoreCase = true) &&
                    targetTitle.length >= 2
                ) {
                    val pattern = Regex("""(?i)\b${Regex.escape(targetTitle)}\b""")
                    for (other in allNotes) {
                        if (other.id != currentNote.id && !seenSourceIds.contains(other.id)) {
                            val otherText = other.content + " " + other.blocksJson
                            if (otherText.contains(targetTitle, ignoreCase = true) &&
                                (otherText.contains("[[$targetTitle]]", ignoreCase = true) ||
                                    pattern.containsMatchIn(otherText))
                            ) {
                                seenSourceIds.add(other.id)
                                resultList.add(
                                    BacklinkItem(
                                        sourceNoteId = other.id,
                                        sourceNoteTitle = other.title.ifBlank { "Untitled" }
                                    )
                                )
                            }
                        }
                    }
                }

                resultList.toList()
            }.flowOn(Dispatchers.Default)
        } else {
            flowOf(emptyList<BacklinkItem>())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<BacklinkItem>())

    init {
        viewModelScope.launch {
            val loaded = noteDao.getNoteById(noteId)
            _note.value = loaded
            loaded?.let {
                _blocks.value = NoteJson.decodeBlocks(it.blocksJson)
                parseAndSaveLinks(it.id)
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

    fun addChecklistBlock() {
        appendBlock(
            NoteBlock.ChecklistBlock(
                id = UUID.randomUUID().toString(),
                title = "To-Do Checklist",
                items = listOf(ChecklistItem(id = UUID.randomUUID().toString(), text = "", isChecked = false))
            )
        )
    }

    fun importPdfFromUri(uri: Uri, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                PdfHelper.importPdfFromUri(appContext, uri)
            }
            if (info != null) {
                appendBlock(
                    NoteBlock.PdfBlock(
                        id = UUID.randomUUID().toString(),
                        filePath = info.filePath,
                        fileName = info.fileName,
                        pageCount = info.pageCount,
                        currentPage = 0,
                        annotationsJson = "[]"
                    )
                )
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }

    fun setNoteLocked(locked: Boolean) {
        _note.value?.let { current ->
            _note.value = current.copy(isLocked = locked, updatedAt = System.currentTimeMillis())
            saveNow()
        }
    }

    fun openOrCreateNoteByTitle(targetTitle: String, onReady: (String) -> Unit) {
        val clean = targetTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val all = noteDao.getAllNotesOnce()
            val match = all.find { it.title.trim().equals(clean, ignoreCase = true) }
            if (match != null) {
                onReady(match.id)
            } else {
                val now = System.currentTimeMillis()
                val createdId = UUID.randomUUID().toString()
                noteDao.insertNote(
                    NoteEntity(
                        id = createdId,
                        title = clean,
                        content = "",
                        folderId = _note.value?.folderId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                onReady(createdId)
            }
        }
    }

    fun updateChecklistTitle(blockId: String, newTitle: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.ChecklistBlock) {
            updateBlock(block.copy(title = newTitle))
        }
    }

    fun toggleChecklistItem(blockId: String, itemId: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.ChecklistBlock) {
            val updatedItems = block.items.map { item ->
                if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
            }
            updateBlock(block.copy(items = updatedItems))
        }
    }

    fun updateChecklistItemText(blockId: String, itemId: String, newText: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.ChecklistBlock) {
            val updatedItems = block.items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }
            updateBlock(block.copy(items = updatedItems))
        }
    }

    fun addChecklistItem(blockId: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.ChecklistBlock) {
            val newItem = ChecklistItem(id = UUID.randomUUID().toString(), text = "", isChecked = false)
            updateBlock(block.copy(items = block.items + newItem))
        }
    }

    fun removeChecklistItem(blockId: String, itemId: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.ChecklistBlock) {
            val updatedItems = block.items.filter { it.id != itemId }
            updateBlock(block.copy(items = updatedItems))
        }
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
            val strokesJson = NoteJson.encodeStrokes(strokes)
            updateBlock(block.copy(strokesJson = strokesJson))
        }
    }

    fun updateInkTemplate(blockId: String, template: String) {
        val block = _blocks.value.find { it.id == blockId }
        if (block is NoteBlock.InkBlock) {
            updateBlock(block.copy(paperTemplate = template))
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
            parseAndSaveLinks(currentNote.id)
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
            val blocksJsonStr = NoteJson.encodeBlocks(_blocks.value)
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
                parseAndSaveLinks(note.id)
            }
        }
    }

    /**
     * Gathers all text across note content and all text-containing blocks (TextBlocks, Image OCR text, ChecklistBlocks).
     */
    private fun getCombinedNoteText(): String {
        val contentText = _note.value?.content.orEmpty()
        val blockTexts = _blocks.value.mapNotNull { block ->
            when (block) {
                is NoteBlock.TextBlock -> block.text
                is NoteBlock.ImageBlock -> block.extractedOcrText
                is NoteBlock.ChecklistBlock -> {
                    val itemsText = block.items.joinToString("\n") { it.text }
                    listOf(block.title, itemsText).filter { it.isNotBlank() }.joinToString("\n")
                }
                is NoteBlock.PdfBlock -> {
                    val notes = PdfHelper.parseAnnotations(block.annotationsJson)
                        .mapNotNull { it.pageNote.takeIf { n -> n.isNotBlank() } }
                    (listOf(block.fileName) + notes).joinToString("\n")
                }
                else -> null
            }
        }
        return (listOf(contentText) + blockTexts).joinToString("\n")
    }

    /**
     * Extracts wiki-links [[Title]] AND mentions of existing notes (e.g. "Test 1", "Test 2").
     * Resolves target note IDs and saves them to LinkDao.
     */
    private suspend fun parseAndSaveLinks(sourceNoteId: String) {
        val fullText = getCombinedNoteText()
        val allNotes = noteDao.getAllNotesOnce()

        val links = withContext(Dispatchers.Default) {
            val otherNotes = allNotes.filter { it.id != sourceNoteId }
            val foundTargetTitles = mutableSetOf<String>()

            // 1. Explicit [[Wiki-Links]]
            val wikiRegex = """\[\[(.*?)]]""".toRegex()
            wikiRegex.findAll(fullText).forEach { match ->
                val t = match.groupValues[1].trim()
                if (t.isNotBlank()) {
                    foundTargetTitles.add(t)
                }
            }

            // 2. Mentions of other existing note titles (prefilter before regex compile)
            for (other in otherNotes) {
                val title = other.title.trim()
                if (title.isBlank() ||
                    title.equals("Untitled", ignoreCase = true) ||
                    title.equals("Untitled Note", ignoreCase = true) ||
                    title.length < 2
                ) {
                    continue
                }

                if (fullText.contains(title, ignoreCase = true)) {
                    val pattern = Regex("""(?i)\b${Regex.escape(title)}\b""")
                    if (pattern.containsMatchIn(fullText)) {
                        foundTargetTitles.add(title)
                    }
                }
            }

            // Map titles to LinkEntity with resolved targetNoteId
            foundTargetTitles.map { targetTitle ->
                val resolvedNote = otherNotes.find { it.title.trim().equals(targetTitle, ignoreCase = true) }
                LinkEntity(
                    sourceNoteId = sourceNoteId,
                    targetNoteTitle = resolvedNote?.title ?: targetTitle,
                    targetNoteId = resolvedNote?.id,
                    isResolved = resolvedNote != null
                )
            }
        }

        linkDao.deleteLinksForNote(sourceNoteId)
        if (links.isNotEmpty()) {
            linkDao.insertLinks(links)
        }
    }
}
