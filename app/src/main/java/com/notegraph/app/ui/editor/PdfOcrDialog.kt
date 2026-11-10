package com.notegraph.app.ui.editor

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class PdfOcrScope(val label: String) {
    CURRENT_PAGE("This Page"),
    CUSTOM_RANGE("Page Range"),
    ALL_PAGES("All Pages")
}

private enum class PdfOcrViewMode(val label: String) {
    BLOCKS("Select Blocks"),
    FREE_EDIT("Edit & Trim")
}

private data class PdfOcrChunk(
    val id: String,
    val pageIndex: Int,
    val blockOrder: Int,
    val text: String
)

/**
 * Dedicated full-screen window for running offline ML Kit OCR on an imported PDF,
 * choosing which pages and how much extracted text to keep (selective blocks, custom edit, or extract all),
 * and copying or inserting the extracted text into the note.
 */
@Composable
fun PdfOcrWindowDialog(
    filePath: String,
    fileName: String,
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onInsertIntoNote: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val safeTotal = pageCount.coerceAtLeast(1)
    val initialPage = (currentPage.coerceIn(0, safeTotal - 1)) + 1

    var ocrScope by remember { mutableStateOf(PdfOcrScope.CURRENT_PAGE) }
    var rangeStart by remember { mutableIntStateOf(initialPage) }
    var rangeEnd by remember { mutableIntStateOf(minOf(initialPage + 2, safeTotal)) }

    var viewMode by remember { mutableStateOf(PdfOcrViewMode.BLOCKS) }
    var chunks by remember { mutableStateOf<List<PdfOcrChunk>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customEditedText by remember { mutableStateOf("") }
    var hasManualEdit by remember { mutableStateOf(false) }

    var isScanning by remember { mutableStateOf(false) }
    var scanProgressPage by remember { mutableIntStateOf(0) }
    var scanProgressTotal by remember { mutableIntStateOf(0) }
    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun rebuildSelectedText(currentChunks: List<PdfOcrChunk>, selected: Set<String>): String {
        val grouped = currentChunks.filter { it.id in selected }.groupBy { it.pageIndex }
        if (grouped.isEmpty()) return ""
        val singlePage = grouped.size <= 1
        return grouped.entries.sortedBy { it.key }.joinToString("\n\n") { (pageIdx, segs) ->
            val body = segs.joinToString("\n\n") { it.text }
            if (singlePage) body else "--- Page ${pageIdx + 1} ---\n$body"
        }
    }

    fun startOcrScan(pagesZeroIndexed: IntProgression) {
        scanJob?.cancel()
        val targetPages = pagesZeroIndexed.toList().filter { it in 0 until safeTotal }
        if (targetPages.isEmpty()) return

        hasManualEdit = false
        isScanning = true
        scanProgressPage = 0
        scanProgressTotal = targetPages.size
        scanStatusMessage = null
        chunks = emptyList()
        selectedIds = emptySet()
        customEditedText = ""

        scanJob = scope.launch {
            val recognizer = PdfHelper.createOcrRecognizer()
            val accumulated = mutableListOf<PdfOcrChunk>()
            val accIds = mutableSetOf<String>()
            try {
                targetPages.forEachIndexed { idx, pIdx ->
                    scanProgressPage = idx + 1
                    val detected = PdfHelper.ocrPdfPageBlocks(
                        filePath = filePath,
                        pageIndex = pIdx,
                        targetWidthPx = 1500,
                        sharedRecognizer = recognizer
                    )
                    detected.forEachIndexed { bIdx, text ->
                        val chunkId = "p${pIdx}_b${bIdx}"
                        accumulated.add(
                            PdfOcrChunk(
                                id = chunkId,
                                pageIndex = pIdx,
                                blockOrder = bIdx,
                                text = text
                            )
                        )
                        accIds.add(chunkId)
                    }
                    val snapshotChunks = accumulated.toList()
                    val snapshotIds = accIds.toSet()
                    chunks = snapshotChunks
                    selectedIds = snapshotIds
                    if (!hasManualEdit) {
                        customEditedText = rebuildSelectedText(snapshotChunks, snapshotIds)
                    }
                }
                scanStatusMessage = if (accumulated.isEmpty()) {
                    "No selectable text was detected in the scanned page(s)."
                } else null
            } catch (_: CancellationException) {
                scanStatusMessage = "Scan stopped (${accumulated.size} blocks captured)"
            } catch (e: Exception) {
                scanStatusMessage = e.localizedMessage ?: "OCR failed"
            } finally {
                isScanning = false
                try {
                    recognizer.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Automatically scan the current page when window opens for instant results
    LaunchedEffect(filePath, currentPage) {
        startOcrScan((initialPage - 1)..(initialPage - 1))
    }

    DisposableEffect(Unit) {
        onDispose {
            scanJob?.cancel()
        }
    }

    val activeOutputText = remember(chunks, selectedIds, viewMode, customEditedText, hasManualEdit) {
        if (hasManualEdit || viewMode == PdfOcrViewMode.FREE_EDIT) {
            customEditedText
        } else {
            rebuildSelectedText(chunks, selectedIds)
        }
    }

    val activePreset = ThemeManager.currentGradient

    Dialog(
        onDismissRequest = {
            scanJob?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MinimalistBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                        ),
                        navigationIcon = {
                            IconButton(onClick = {
                                scanJob?.cancel()
                                onDismiss()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close PDF OCR")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = "PDF Text Extractor (OCR)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${fileName.ifBlank { "Document.pdf" }} • $safeTotal pages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            if (!isScanning && safeTotal > 1) {
                                TextButton(
                                    onClick = {
                                        ocrScope = PdfOcrScope.ALL_PAGES
                                        startOcrScan(0 until safeTotal)
                                    }
                                ) {
                                    Text(
                                        text = "Extract All",
                                        color = activePreset.accentStart,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (viewMode == PdfOcrViewMode.FREE_EDIT && hasManualEdit) {
                                        "Custom edited • ${activeOutputText.length} chars"
                                    } else {
                                        "${selectedIds.size} of ${chunks.size} blocks • ${activeOutputText.length} chars"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (chunks.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            hasManualEdit = false
                                            val allIds = chunks.map { it.id }.toSet()
                                            selectedIds = allIds
                                            customEditedText = rebuildSelectedText(chunks, allIds)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Select All", fontSize = 11.sp, color = activePreset.accentStart)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (activeOutputText.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(activeOutputText))
                                            Toast.makeText(
                                                context,
                                                "Copied ${activeOutputText.length} characters",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = activeOutputText.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy")
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (activeOutputText.isNotBlank()) ThemeManager.activeAccentBrush
                                            else androidx.compose.ui.graphics.SolidColor(
                                                MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                        .clickable(enabled = activeOutputText.isNotBlank()) {
                                            scanJob?.cancel()
                                            onInsertIntoNote(activeOutputText)
                                        }
                                        .padding(vertical = 11.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NoteAdd,
                                            contentDescription = null,
                                            tint = if (activeOutputText.isNotBlank()) activePreset.onAccent
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Insert into Note",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeOutputText.isNotBlank()) activePreset.onAccent
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ─── Page Scope Picker Card ───
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PdfOcrScope.values().forEach { scopeOption ->
                                    val selected = ocrScope == scopeOption
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            ocrScope = scopeOption
                                            if (scopeOption == PdfOcrScope.CURRENT_PAGE) {
                                                startOcrScan((initialPage - 1)..(initialPage - 1))
                                            } else if (scopeOption == PdfOcrScope.ALL_PAGES) {
                                                startOcrScan(0 until safeTotal)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = when (scopeOption) {
                                                    PdfOcrScope.CURRENT_PAGE -> "Page $initialPage"
                                                    PdfOcrScope.CUSTOM_RANGE -> "Page Range"
                                                    PdfOcrScope.ALL_PAGES -> "All ($safeTotal pp)"
                                                },
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }
                            }

                            if (ocrScope == PdfOcrScope.CUSTOM_RANGE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    PageStepper(
                                        label = "From",
                                        value = rangeStart,
                                        min = 1,
                                        max = safeTotal,
                                        onChange = { next ->
                                            rangeStart = next
                                            if (rangeEnd < next) rangeEnd = next
                                        }
                                    )
                                    PageStepper(
                                        label = "To",
                                        value = rangeEnd,
                                        min = rangeStart,
                                        max = safeTotal,
                                        onChange = { next -> rangeEnd = next }
                                    )
                                    Button(
                                        onClick = { startOcrScan((rangeStart - 1)..(rangeEnd - 1)) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Scan", fontSize = 12.sp)
                                    }
                                }
                            }

                            if (isScanning) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Recognizing text on page $scanProgressPage of $scanProgressTotal…",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = activePreset.accentStart
                                        )
                                        TextButton(
                                            onClick = { scanJob?.cancel() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Stop", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    val frac = if (scanProgressTotal > 0) {
                                        (scanProgressPage.toFloat() / scanProgressTotal.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = activePreset.accentStart,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }

                    // ─── Selection Mode Switcher: Pick Blocks vs Edit & Trim ───
                    if (chunks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = viewMode == PdfOcrViewMode.BLOCKS,
                                    onClick = { viewMode = PdfOcrViewMode.BLOCKS },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("Pick Blocks", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = viewMode == PdfOcrViewMode.FREE_EDIT,
                                    onClick = {
                                        if (!hasManualEdit) {
                                            customEditedText = rebuildSelectedText(chunks, selectedIds)
                                        }
                                        viewMode = PdfOcrViewMode.FREE_EDIT
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                    },
                                    label = { Text("Trim / Edit", fontSize = 12.sp) }
                                )
                            }

                            if (viewMode == PdfOcrViewMode.BLOCKS) {
                                val allSelected = selectedIds.size == chunks.size
                                TextButton(
                                    onClick = {
                                        hasManualEdit = false
                                        val nextIds = if (allSelected) emptySet() else chunks.map { it.id }.toSet()
                                        selectedIds = nextIds
                                        customEditedText = rebuildSelectedText(chunks, nextIds)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        text = if (allSelected) "Clear All" else "Check All",
                                        fontSize = 12.sp,
                                        color = activePreset.accentStart
                                    )
                                }
                            }
                        }
                    }

                    scanStatusMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // ─── Main Content Area ───
                    if (viewMode == PdfOcrViewMode.FREE_EDIT) {
                        OutlinedTextField(
                            value = customEditedText,
                            onValueChange = {
                                customEditedText = it
                                hasManualEdit = true
                            },
                            label = { Text("Edit or trim how much text to extract") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else if (chunks.isEmpty() && !isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = null,
                                    tint = activePreset.accentStart,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Choose a page scope above and tap Scan",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chunks, key = { it.id }) { chunk ->
                                val isChecked = chunk.id in selectedIds
                                val cardShape = RoundedCornerShape(14.dp)
                                Surface(
                                    shape = cardShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(
                                        width = if (isChecked) 1.2.dp else 0.75.dp,
                                        color = if (isChecked) activePreset.accentStart.copy(alpha = 0.65f)
                                        else ThemeManager.cardBorderColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(ThemeManager.activeCardBrush(highlighted = isChecked))
                                        .clickable {
                                            hasManualEdit = false
                                            val nextIds = if (isChecked) selectedIds - chunk.id else selectedIds + chunk.id
                                            selectedIds = nextIds
                                            customEditedText = rebuildSelectedText(chunks, nextIds)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isChecked) activePreset.accentStart
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isChecked) activePreset.accentStart else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isChecked) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = activePreset.onAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "PAGE ${chunk.pageIndex + 1} • BLOCK ${chunk.blockOrder + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.6.sp,
                                                color = if (isChecked) activePreset.accentStart
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            SelectionContainer {
                                                Text(
                                                    text = chunk.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = value > min,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label page", modifier = Modifier.size(16.dp))
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = value < max,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase $label page", modifier = Modifier.size(16.dp))
            }
        }
    }
}
