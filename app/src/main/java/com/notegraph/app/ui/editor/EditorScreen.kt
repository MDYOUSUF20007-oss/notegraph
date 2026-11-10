package com.notegraph.app.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
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
    val blocks by viewModel.blocks.collectAsState()

    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var content by remember(note?.id) { mutableStateOf(note?.content ?: "") }
    var showExportSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val json = remember { Json { ignoreUnknownKeys = true } }

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
            // Multimedia & Checklist Toolbar
            BottomAppBar(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
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
                value = content,
                onValueChange = {
                    content = it
                    viewModel.updateContent(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Start typing notes or use [[Wiki-Links]]...") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick add actions directly underneath the note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.addChecklistBlock() },
                    label = { Text("☑ Add To-Do Checklist") }
                )
                AssistChip(
                    onClick = { viewModel.addInkBlock() },
                    label = { Text("✏️ Add Sketch") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multimedia & Checklist blocks
            blocks.forEach { block ->
                key(block.id) {
                    val blockLabel = when (block) {
                        is NoteBlock.TextBlock -> "Text Note"
                        is NoteBlock.InkBlock -> "Ink Canvas"
                        is NoteBlock.AudioBlock -> "Voice Recording"
                        is NoteBlock.ImageBlock -> "Image & OCR"
                        is NoteBlock.ChecklistBlock -> "To-Do Checklist"
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
                                        Text(
                                            "🔗 Audio @ ${ts / 1000}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                                            color = MaterialTheme.colorScheme.outline
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
                                        onStrokesChanged = { newStrokes ->
                                            viewModel.updateInkStrokes(block.id, newStrokes)
                                        }
                                    )
                                }

                                is NoteBlock.AudioBlock -> {
                                    AudioRecorderBlock(
                                        audioFilePath = block.filePath,
                                        durationMs = block.durationMs,
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
                                            val current = content
                                            val appended = if (current.isBlank()) text
                                                else "$current\n\n--- OCR ---\n$text"
                                            content = appended
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
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
}
