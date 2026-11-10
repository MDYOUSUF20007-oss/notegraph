package com.notegraph.app.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.notegraph.app.ui.theme.MinimalistBackground
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
    val activePreset = ThemeManager.currentGradient

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

    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNow()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

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
                Toast.makeText(context, "PNG saved successfully", Toast.LENGTH_SHORT).show()
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

    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Export Note",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))

                val baseName = (note?.title ?: "note").ifBlank { "note" }
                    .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")

                ExportOptionRow(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "PDF Document (.pdf)",
                    subtitle = "Multi-page document with text, ink & attachments",
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pdfLauncher.launch("$baseName.pdf")
                    }
                )
                ExportOptionRow(
                    icon = Icons.Rounded.Description,
                    title = "Word Document (.docx)",
                    subtitle = "Editable OpenXML format for Word & Docs",
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        docxLauncher.launch("$baseName.docx")
                    }
                )
                ExportOptionRow(
                    icon = Icons.Rounded.Slideshow,
                    title = "Presentation (.pptx)",
                    subtitle = "Slide deck for PowerPoint & Keynote",
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pptxLauncher.launch("$baseName.pptx")
                    }
                )
                ExportOptionRow(
                    icon = Icons.Rounded.Image,
                    title = "Lossless Image (.png)",
                    subtitle = "High-resolution flat snapshot",
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        pngLauncher.launch("$baseName.png")
                    }
                )
                ExportOptionRow(
                    icon = Icons.Rounded.Code,
                    title = "Markdown (.md)",
                    subtitle = "Plain text with [[wiki-links]] and #tags",
                    onClick = {
                        showExportSheet = false
                        viewModel.saveNow()
                        mdLauncher.launch("$baseName.md")
                    }
                )
            }
        }
    }

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.saveNow()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
                                imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = if (isLocked) "Unlock Note" else "Lock Note",
                                tint = if (isLocked) activePreset.accentStart else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            viewModel.saveNow()
                            onNavigateReader(note?.id)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = "Book Reader Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.saveNow(showEvent = true) }) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Save Note",
                                tint = activePreset.accentStart
                            )
                        }
                        IconButton(onClick = { showExportSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                // Single minimalist glass toolbar for multimedia, PDF & checklist blocks
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorToolButton(
                                icon = Icons.Rounded.PictureAsPdf,
                                label = "PDF",
                                onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }
                            )
                            EditorToolButton(
                                icon = Icons.Rounded.CheckBox,
                                label = "Tasks",
                                onClick = { viewModel.addChecklistBlock() }
                            )
                            EditorToolButton(
                                icon = Icons.Rounded.Brush,
                                label = "Sketch",
                                onClick = { viewModel.addInkBlock() }
                            )
                            EditorToolButton(
                                icon = Icons.Rounded.Mic,
                                label = "Voice",
                                onClick = { viewModel.addAudioBlock() }
                            )
                            EditorToolButton(
                                icon = Icons.Rounded.Image,
                                label = "OCR",
                                onClick = { viewModel.addImageBlock() }
                            )
                            EditorToolButton(
                                icon = Icons.Rounded.Link,
                                label = "Link",
                                onClick = { showLinkPickerDialog = true }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Borderless Editorial Title
                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        viewModel.updateTitle(it)
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(activePreset.accentStart),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "Untitled",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(Modifier.height(10.dp))

                // Minimal accent hairline rule
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.activeAccentBrush)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(0.5.dp)
                            .background(ThemeManager.cardBorderColor)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Borderless Distraction-Free Note Canvas
                BasicTextField(
                    value = contentField,
                    onValueChange = { updated ->
                        contentField = updated
                        viewModel.updateContent(updated.text)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 25.sp
                    ),
                    cursorBrush = SolidColor(activePreset.accentStart),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    decorationBox = { innerTextField ->
                        if (contentField.text.isEmpty()) {
                            Text(
                                text = "Start writing, or type [[ to connect notes…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
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
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.75.dp, activePreset.accentStart.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = if (candidates.isEmpty()) "No matching notes (close with ]] to create)" else "Link note:",
                                style = MaterialTheme.typography.labelSmall,
                                color = activePreset.accentStart,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (candidates.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
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

                Spacer(modifier = Modifier.height(14.dp))

                // Multimedia, PDF & Checklist blocks
                blocks.forEach { block ->
                    key(block.id) {
                        val blockLabel = when (block) {
                            is NoteBlock.TextBlock -> "Note Segment"
                            is NoteBlock.InkBlock -> "Sketch"
                            is NoteBlock.AudioBlock -> "Voice"
                            is NoteBlock.ImageBlock -> "Image & OCR"
                            is NoteBlock.ChecklistBlock -> "Checklist"
                            is NoteBlock.PdfBlock -> "PDF Markup"
                        }
                        val blockShape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(blockShape)
                                .background(ThemeManager.activeCardBrush(highlighted = false))
                                .border(0.75.dp, ThemeManager.cardBorderColor, blockShape)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 6.dp, top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = blockLabel.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeBlock(block.id) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove block",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                    }
                                }

                                when (block) {
                                    is NoteBlock.TextBlock -> {
                                        var blockText by remember(block.id) { mutableStateOf(block.text) }
                                        BasicTextField(
                                            value = blockText,
                                            onValueChange = { newText ->
                                                blockText = newText
                                                viewModel.updateBlock(block.copy(text = newText))
                                                viewModel.stampTextBlockWithAudioTime(block.id)
                                            },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(activePreset.accentStart),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            decorationBox = { inner ->
                                                if (blockText.isEmpty()) {
                                                    Text(
                                                        "Write additional notes…",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                                                    )
                                                }
                                                inner()
                                            }
                                        )
                                        block.audioTimestampMs?.let { ts ->
                                            AssistChip(
                                                onClick = { pendingAudioSeekMs = ts },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.PlayArrow,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                label = { Text("Audio @ ${ts / 1000}s") },
                                                modifier = Modifier.padding(start = 14.dp, bottom = 10.dp)
                                            )
                                        }
                                    }

                                    is NoteBlock.InkBlock -> {
                                        val strokes: List<InkStroke> = remember(block.strokesJson) {
                                            try {
                                                json.decodeFromString<List<InkStroke>>(block.strokesJson)
                                            } catch (_: Exception) {
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

                // Outgoing Wiki-Links section
                if (outgoingLinks.isNotEmpty()) {
                    Text(
                        text = "Linked Notes (${outgoingLinks.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
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
                Spacer(modifier = Modifier.height(6.dp))
                if (backlinks.isEmpty()) {
                    Text(
                        text = "No backlinks yet. Reference this note with [[Title]] elsewhere to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                } else {
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
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(92.dp))
            }
        }
    }

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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

@Composable
private fun EditorToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val activePreset = ThemeManager.currentGradient
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activePreset.accentStart,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val activePreset = ThemeManager.currentGradient
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(activePreset.accentStart.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activePreset.accentStart,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
