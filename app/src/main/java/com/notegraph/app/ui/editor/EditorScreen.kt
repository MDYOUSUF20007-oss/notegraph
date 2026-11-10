package com.notegraph.app.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onNavigateReader: (String?) -> Unit = {},
    onNoteClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()
    val outgoingLinks by viewModel.outgoingLinks.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val blocks by viewModel.blocks.collectAsState()

    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var contentField by remember(note?.id) {
        mutableStateOf(TextFieldValue(note?.content ?: ""))
    }
    var showExportSheet by remember { mutableStateOf(false) }
    var showLinkPickerDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pendingAudioSeekMs by remember { mutableStateOf<Long?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Keep editor state initialized once note loads asynchronously from Room
    LaunchedEffect(note?.id) {
        val loaded = note
        if (loaded != null) {
            if (title.isEmpty() && loaded.title.isNotEmpty()) title = loaded.title
            if (contentField.text.isEmpty() && loaded.content.isNotEmpty()) {
                contentField = TextFieldValue(loaded.content, TextRange(loaded.content.length))
            }
        }
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importPdfFromUri(uri) { ok ->
            Toast.makeText(
                context,
                if (ok) "PDF imported — draw or highlight right on top!" else "Could not open PDF",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Intercept back button to persist immediately
    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    // Persist immediately on composition disposal
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNow()
        }
    }

    // Collect save notification events
    LaunchedEffect(Unit) {
        viewModel.saveEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    // Storage Access Framework launchers for each export format
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        note?.let { n ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ExportManager.exportPdf(context, n, os)
                }
                Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val docxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        note?.let { n ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ExportManager.exportDocx(n, os)
                }
                Toast.makeText(context, "DOCX saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "DOCX export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pptxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        note?.let { n ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ExportManager.exportPptx(n, os)
                }
                Toast.makeText(context, "PPTX saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "PPTX export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        note?.let { n ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ExportManager.exportPng(context, n, os)
                }
                Toast.makeText(context, "Lossless PNG saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "PNG export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val mdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        note?.let { n ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ExportManager.exportMarkdown(n, os)
                }
                Toast.makeText(context, "Markdown saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Markdown export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Modal Bottom Sheet for Export / Save As
    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Export / Save As",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(16.dp))

                val baseName = (note?.title ?: "note").ifBlank { "note" }
                    .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")

                // 1. PDF
                OutlinedCard(
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pdfLauncher.launch("$baseName.pdf")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save as PDF (.pdf)") },
                        supportingContent = { Text("Multi-page document with text, ink canvas & images") },
                        leadingContent = { Text("📄", style = MaterialTheme.typography.titleLarge) }
                    )
                }

                // 2. DOCX
                OutlinedCard(
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        docxLauncher.launch("$baseName.docx")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save as Word Document (.docx)") },
                        supportingContent = { Text("OpenXML format for Microsoft Word and Google Docs") },
                        leadingContent = { Text("📝", style = MaterialTheme.typography.titleLarge) }
                    )
                }

                // 3. PPTX
                OutlinedCard(
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pptxLauncher.launch("$baseName.pptx")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save as PowerPoint (.pptx)") },
                        supportingContent = { Text("Slide presentation for PowerPoint and Google Slides") },
                        leadingContent = { Text("📊", style = MaterialTheme.typography.titleLarge) }
                    )
                }

                // 4. PNG
                OutlinedCard(
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pngLauncher.launch("$baseName.png")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save as Lossless Image (.png)") },
                        supportingContent = { Text("High-resolution crisp screenshot") },
                        leadingContent = { Text("🖼", style = MaterialTheme.typography.titleLarge) }
                    )
                }

                // 5. Markdown
                OutlinedCard(
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        mdLauncher.launch("$baseName.md")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Save as Markdown (.md)") },
                        supportingContent = { Text("Plain text with [[wiki-links]] and #tags") },
                        leadingContent = { Text("📋", style = MaterialTheme.typography.titleLarge) }
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = title,
                        onValueChange = {
                            title = it
                            viewModel.updateTitle(it)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        placeholder = { Text("Note Title") }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNow()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Lock / Unlock Note
                    val isLocked = note?.isLocked == true
                    IconButton(onClick = {
                        if (isLocked) {
                            viewModel.setNoteLocked(false)
                            Toast.makeText(context, "Note unlocked", Toast.LENGTH_SHORT).show()
                        } else if (NoteSecurityManager.hasPin(context)) {
                            viewModel.setNoteLocked(true)
                            Toast.makeText(context, "Note locked with Vault PIN", Toast.LENGTH_SHORT).show()
                        } else {
                            showPinSetupDialog = true
                        }
                    }) {
                        Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "Unlock Note" else "Lock Note"
                        )
                    }
                    // Book Reading Mode
                    IconButton(onClick = {
                        viewModel.saveNow()
                        onNavigateReader(note?.id)
                    }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Read in Book Mode")
                    }
                    // Visible Save Button
                    IconButton(onClick = { viewModel.saveNow(showEvent = true) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save Note")
                    }
                    // Export / Save As Button
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export / Save As")
                    }
                }
            )
        },
        bottomBar = {
            // Multimedia, PDF & Checklist Toolbar
            BottomAppBar(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("📄 PDF")
                    }
                    TextButton(onClick = { viewModel.addChecklistBlock() }) {
                        Text("☑ To-Do")
                    }
                    TextButton(onClick = { viewModel.addInkBlock() }) {
                        Text("✏️ Drawing")
                    }
                    TextButton(onClick = { viewModel.addAudioBlock() }) {
                        Text("🎙 Voice")
                    }
                    TextButton(onClick = { viewModel.addImageBlock() }) {
                        Text("🖼 Image/OCR")
                    }
                    TextButton(onClick = { showLinkPickerDialog = true }) {
                        Text("🔗 [[Link]]")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Main text editor
            OutlinedTextField(
                value = contentField,
                onValueChange = { updated ->
                    contentField = updated
                    viewModel.updateContent(updated.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Start typing notes or type [[ to link notes…") }
            )

            // Live Obsidian [[Wiki-Link]] Autocomplete Bar while typing `[[query`
            val activeWikiMatch = remember(contentField) {
                val cursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                val beforeCursor = contentField.text.substring(0, cursor)
                Regex("""\[\[([^\[\]\n]*)$""").find(beforeCursor)
            }
            if (activeWikiMatch != null) {
                val rawQuery = activeWikiMatch.groupValues[1].trim()
                val candidates = remember(allNotes, note?.id, rawQuery) {
                    allNotes
                        .filter { it.id != note?.id && it.title.isNotBlank() }
                        .filter { rawQuery.isBlank() || it.title.contains(rawQuery, ignoreCase = true) }
                        .take(10)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = if (candidates.isEmpty()) "No matching notes (finish with ]] to create)" else "Tap to link note:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (candidates.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(candidates, key = { it.id }) { candidate ->
                                    SuggestionChip(
                                        onClick = {
                                            val startIdx = activeWikiMatch.range.first
                                            val cursor = contentField.selection.start.coerceIn(startIdx, contentField.text.length)
                                            val inserted = "[[${candidate.title}]] "
                                            val newText = contentField.text.replaceRange(startIdx, cursor, inserted)
                                            val newCursor = startIdx + inserted.length
                                            contentField = TextFieldValue(newText, TextRange(newCursor))
                                            viewModel.updateContent(newText)
                                        },
                                        label = { Text("[[${candidate.title}]]") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick add actions directly underneath the note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                    label = { Text("📄 Add PDF & Markup") }
                )
                AssistChip(
                    onClick = { viewModel.addChecklistBlock() },
                    label = { Text("☑ Add To-Do") }
                )
                AssistChip(
                    onClick = { viewModel.addInkBlock() },
                    label = { Text("✏️ Add Sketch") }
                )
                AssistChip(
                    onClick = { showLinkPickerDialog = true },
                    label = { Text("🔗 Link Note") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multimedia, PDF & Checklist blocks
            blocks.forEach { block ->
                key(block.id) {
                    val blockLabel = when (block) {
                        is NoteBlock.TextBlock -> "Text Note"
                        is NoteBlock.InkBlock -> "Ink Canvas"
                        is NoteBlock.AudioBlock -> "Voice Recording"
                        is NoteBlock.ImageBlock -> "Image & OCR"
                        is NoteBlock.ChecklistBlock -> "To-Do Checklist"
                        is NoteBlock.PdfBlock -> "PDF Document & Markup"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, ThemeManager.cardBorderColor),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = blockLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                IconButton(
                                    onClick = { viewModel.removeBlock(block.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove block",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            when (block) {
                                is NoteBlock.TextBlock -> {
                                    var blockText by remember(block.id) { mutableStateOf(block.text) }
                                    OutlinedTextField(
                                        value = blockText,
                                        onValueChange = { newText ->
                                            blockText = newText
                                            viewModel.updateBlock(block.copy(text = newText))
                                            viewModel.stampTextBlockWithAudioTime(block.id)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        placeholder = { Text("Text block…") }
                                    )
                                    block.audioTimestampMs?.let { ts ->
                                        AssistChip(
                                            onClick = { pendingAudioSeekMs = ts },
                                            label = { Text("▶ Jump to Audio @ ${ts / 1000}s") },
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                        )
                                    }
                                }

                                is NoteBlock.InkBlock -> {
                                    val strokes: List<InkStroke> = remember(block.strokesJson) {
                                        try {
                                            json.decodeFromString<List<InkStroke>>(block.strokesJson)
                                        } catch (e: Exception) {
                                            emptyList()
                                        }
                                    }
                                    InkCanvas(
                                        strokes = strokes,
                                        heightDp = block.heightDp,
                                        paperTemplate = block.paperTemplate,
                                        onTemplateChanged = { tmpl ->
                                            viewModel.updateInkTemplate(block.id, tmpl)
                                        },
                                        onStrokesChanged = { newStrokes ->
                                            viewModel.updateInkStrokes(block.id, newStrokes)
                                        }
                                    )
                                }

                                is NoteBlock.AudioBlock -> {
                                    AudioRecorderBlock(
                                        audioFilePath = block.filePath,
                                        durationMs = block.durationMs,
                                        seekToRequestMs = pendingAudioSeekMs,
                                        onSeekHandled = { pendingAudioSeekMs = null },
                                        onRecordingStarted = { _, startMs ->
                                            viewModel.onAudioRecordingStarted(startMs)
                                        },
                                        onRecordingStopped = { duration ->
                                            viewModel.onAudioRecordingStopped()
                                            viewModel.updateBlock(block.copy(durationMs = duration))
                                        }
                                    )
                                }

                                is NoteBlock.ImageBlock -> {
                                    ImageOcrBlock(
                                        uriString = block.uriString,
                                        extractedText = block.extractedOcrText,
                                        onImagePicked = { uri: Uri ->
                                            viewModel.updateBlock(
                                                block.copy(uriString = uri.toString())
                                            )
                                        },
                                        onOcrResult = { text ->
                                            viewModel.updateBlock(
                                                block.copy(extractedOcrText = text)
                                            )
                                            val current = contentField.text
                                            val appended = if (current.isBlank()) text
                                                else "$current\n\n--- OCR ---\n$text"
                                            contentField = TextFieldValue(appended, TextRange(appended.length))
                                            viewModel.updateContent(appended)
                                        }
                                    )
                                }

                                is NoteBlock.ChecklistBlock -> {
                                    ChecklistBlockUi(
                                        block = block,
                                        onTitleChanged = { newTitle ->
                                            viewModel.updateChecklistTitle(block.id, newTitle)
                                        },
                                        onToggleItem = { itemId ->
                                            viewModel.toggleChecklistItem(block.id, itemId)
                                        },
                                        onItemTextChanged = { itemId, newText ->
                                            viewModel.updateChecklistItemText(block.id, itemId, newText)
                                        },
                                        onAddItem = {
                                            viewModel.addChecklistItem(block.id)
                                        },
                                        onRemoveItem = { itemId ->
                                            viewModel.removeChecklistItem(block.id, itemId)
                                        }
                                    )
                                }

                                is NoteBlock.PdfBlock -> {
                                    PdfAnnotationBlock(
                                        block = block,
                                        onBlockUpdated = { updatedPdf ->
                                            viewModel.updateBlock(updatedPdf)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outgoing Wiki-Links section (Obsidian parity)
            if (outgoingLinks.isNotEmpty()) {
                Text("Linked Notes (${outgoingLinks.size})", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(outgoingLinks, key = { it.id }) { link ->
                        InputChip(
                            selected = link.isResolved,
                            onClick = {
                                viewModel.saveNow()
                                if (link.targetNoteId != null) {
                                    onNoteClick(link.targetNoteId)
                                } else {
                                    viewModel.openOrCreateNoteByTitle(link.targetNoteTitle) { targetId ->
                                        onNoteClick(targetId)
                                    }
                                }
                            },
                            label = {
                                Text(
                                    if (link.isResolved) "[[${link.targetNoteTitle}]]"
                                    else "[[${link.targetNoteTitle}]] (+ Create)"
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Backlinks section
            Text("Backlinks (${backlinks.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (backlinks.isEmpty()) {
                Text(
                    "No backlinks found. Mention this note's title in other notes to connect them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    "Referenced by:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(backlinks) { item ->
                        InputChip(
                            selected = false,
                            onClick = {
                                viewModel.saveNow()
                                onNoteClick(item.sourceNoteId)
                            },
                            label = { Text(item.sourceNoteTitle) },
                            leadingIcon = {
                                Text("🔗", fontSize = 12.sp)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Searchable [[Wiki-Link]] Picker Dialog
    if (showLinkPickerDialog) {
        var linkQuery by remember { mutableStateOf("") }
        val filtered = remember(allNotes, note?.id, linkQuery) {
            val q = linkQuery.trim()
            allNotes
                .filter { it.id != note?.id }
                .filter { q.isBlank() || it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showLinkPickerDialog = false },
            title = { Text("Insert [[Wiki-Link]]") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = linkQuery,
                        onValueChange = { linkQuery = it },
                        placeholder = { Text("Search note by title or create new…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(filtered, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.title.ifBlank { "Untitled" }) },
                                supportingContent = if (target.content.isNotBlank()) {
                                    { Text(target.content.take(60), maxLines = 1) }
                                } else null,
                                modifier = Modifier.clickable {
                                    val insert = "[[${target.title.ifBlank { "Untitled" }}]] "
                                    val cursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                                    val newText = contentField.text.replaceRange(cursor, cursor, insert)
                                    contentField = TextFieldValue(newText, TextRange(cursor + insert.length))
                                    viewModel.updateContent(newText)
                                    showLinkPickerDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (linkQuery.isNotBlank()) {
                    Button(onClick = {
                        val insert = "[[${linkQuery.trim()}]] "
                        val cursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                        val newText = contentField.text.replaceRange(cursor, cursor, insert)
                        contentField = TextFieldValue(newText, TextRange(cursor + insert.length))
                        viewModel.updateContent(newText)
                        showLinkPickerDialog = false
                    }) {
                        Text("Link \"${linkQuery.trim()}\"")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkPickerDialog = false }) { Text("Cancel") }
            }
        )
    }

    // First-time Vault PIN setup dialog when locking a note
    if (showPinSetupDialog) {
        VaultPinDialog(
            isSettingNewPin = true,
            title = "Set Vault PIN",
            subtitle = "Create a PIN to protect locked notes across Home, Reader, and Search.",
            onDismiss = { showPinSetupDialog = false },
            onVerify = { true },
            onSuccess = { newPin ->
                NoteSecurityManager.setPin(context, newPin)
                viewModel.setNoteLocked(true)
                showPinSetupDialog = false
                Toast.makeText(context, "PIN saved & note locked", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
