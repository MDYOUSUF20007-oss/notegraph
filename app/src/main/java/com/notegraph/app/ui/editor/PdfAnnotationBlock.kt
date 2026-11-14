package com.notegraph.app.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import com.notegraph.app.data.local.entity.StrokePoint
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class PdfToolMode {
    PEN,
    HIGHLIGHTER,
    ERASER,
    VANISHING,
    PAN
}

private val PdfInkColors = listOf(
    Color(0xFF111827), // Deep ink black (default for white PDF paper)
    Color(0xFFEF4444), // Red markup
    Color(0xFF2563EB), // Royal blue
    Color(0xFF16A34A), // Emerald green
    Color(0xFFFACC15), // Highlighter yellow
    Color(0xFF9333EA)  // Violet accent
)

private val PdfInkColorHex = listOf(
    "#111827",
    "#EF4444",
    "#2563EB",
    "#16A34A",
    "#FACC15",
    "#9333EA"
)

private val DarkPdfColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Samsung Notes inspired PDF Viewer + Multi-Page Markup Block.
 * Supports picking PDFs from device storage, rendering pages natively offline via PdfRenderer,
 * zooming/panning, S-Pen palm rejection, highlighter/shape-snap ink overlays per page,
 * page margin notes, dark-paper inversion, and a fullscreen PDF Markup Studio dialog.
 */
@Composable
fun PdfAnnotationBlock(
    block: NoteBlock.PdfBlock,
    onBlockUpdated: (NoteBlock.PdfBlock) -> Unit,
    onExtractTextToNote: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showFullscreenStudio by remember { mutableStateOf(false) }
    var showOcrWindow by remember { mutableStateOf(false) }
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val activePreset = ThemeManager.currentGradient

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        isImporting = true
        importError = null
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                PdfHelper.importPdfFromUri(context, uri)
            }
            isImporting = false
            if (info != null) {
                onBlockUpdated(
                    block.copy(
                        filePath = info.filePath,
                        fileName = info.fileName,
                        pageCount = info.pageCount,
                        currentPage = 0,
                        annotationsJson = "[]"
                    )
                )
            } else {
                importError = "Could not open PDF file."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isLuxury) 0.dp else 8.dp, vertical = if (isLuxury) 6.dp else 8.dp)
    ) {
        if (block.filePath.isBlank() || block.pageCount <= 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "PDF Document & Markup",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = if (isLuxury) FontFamily.Serif else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Open a PDF file to write, highlight, or draw directly above every page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    enabled = !isImporting,
                    shape = RoundedCornerShape(if (isLuxury) 8.dp else 12.dp),
                    colors = if (isLuxury) {
                        ButtonDefaults.buttonColors(containerColor = lux.accent, contentColor = lux.onAccent)
                    } else ButtonDefaults.buttonColors(),
                    elevation = if (isLuxury) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation()
                ) {
                    Text(
                        text = if (isImporting) "Importing PDF…" else "Select PDF from Device",
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                    )
                }
                importError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            // Document Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLuxury) 16.dp else 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = block.fileName.ifBlank { "Document.pdf" },
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = if (isLuxury) FontFamily.Serif else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val annotatedPages = remember(block.annotationsJson) {
                        PdfHelper.parseAnnotations(block.annotationsJson).count {
                            it.strokes.isNotEmpty() || it.pageNote.isNotBlank()
                        }
                    }
                    Text(
                        text = if (isLuxury) {
                            "${block.pageCount}P // $annotatedPages ANNOTATED"
                        } else {
                            "${block.pageCount} pages • $annotatedPages annotated"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                        fontSize = if (isLuxury) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                        color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLuxury) {
                        PdfLuxuryPill(
                            label = "OCR TEXT",
                            selected = true,
                            onClick = { showOcrWindow = true }
                        )
                        PdfLuxuryPill(
                            label = "FOCUS STUDIO",
                            selected = false,
                            onClick = { showFullscreenStudio = true }
                        )
                        PdfLuxuryPill(
                            label = "REPLACE",
                            selected = false,
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { showOcrWindow = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = activePreset.accentStart.copy(alpha = 0.16f),
                                contentColor = activePreset.accentStart
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DocumentScanner,
                                contentDescription = "OCR Extract PDF Text",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("OCR Text", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = { showFullscreenStudio = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen PDF Markup",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Focus Studio", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Replace", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            PdfMarkupSurface(
                block = block,
                isFullscreen = false,
                onBlockUpdated = onBlockUpdated
            )
        }
    }

    if (showFullscreenStudio && block.filePath.isNotBlank()) {
        Dialog(
            onDismissRequest = { showFullscreenStudio = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isLuxury) lux.canvasBackground else MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen Top Header
                    Surface(
                        tonalElevation = if (isLuxury) 0.dp else 4.dp,
                        shadowElevation = 0.dp,
                        color = if (isLuxury) lux.elevatedSurface else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.fileName.ifBlank { "PDF Studio" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = if (isLuxury) FontFamily.Serif else FontFamily.Default,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isLuxury) "2-FINGER PINCH ZOOM // PEN + HIGHLIGHT" else "Pinch with 2 fingers to zoom & pan • Annotate right on PDF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                        fontSize = if (isLuxury) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                                        color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.outline
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isLuxury) {
                                        PdfLuxuryPill(
                                            label = "OCR",
                                            selected = false,
                                            onClick = { showOcrWindow = true }
                                        )
                                        PdfLuxuryPill(
                                            label = "DONE",
                                            selected = true,
                                            onClick = { showFullscreenStudio = false }
                                        )
                                    } else {
                                        FilledTonalButton(
                                            onClick = { showOcrWindow = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Rounded.DocumentScanner, contentDescription = "OCR PDF Text", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("OCR")
                                        }
                                        FilledTonalButton(onClick = { showFullscreenStudio = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Studio", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Done")
                                        }
                                    }
                                }
                            }
                            if (isLuxury) {
                                HorizontalDivider(thickness = 1.dp, color = lux.dividerRule)
                            }
                        }
                    }

                    PdfMarkupSurface(
                        block = block,
                        isFullscreen = true,
                        onBlockUpdated = onBlockUpdated,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isLuxury) 0.dp else 8.dp)
                    )
                }
            }
        }
    }

    if (showOcrWindow && block.filePath.isNotBlank()) {
        PdfOcrWindowDialog(
            filePath = block.filePath,
            fileName = block.fileName,
            pageCount = block.pageCount,
            currentPage = block.currentPage,
            onDismiss = { showOcrWindow = false },
            onInsertIntoNote = { extractedText ->
                showOcrWindow = false
                if (onExtractTextToNote != null) {
                    onExtractTextToNote(extractedText)
                } else {
                    val existing = PdfHelper.parseAnnotations(block.annotationsJson)
                    val curIdx = block.currentPage.coerceIn(0, (block.pageCount - 1).coerceAtLeast(0))
                    val curAnn = existing.find { it.pageIndex == curIdx } ?: PdfPageAnnotation(curIdx)
                    val mergedNote = if (curAnn.pageNote.isBlank()) extractedText
                    else "${curAnn.pageNote}\n$extractedText"
                    val updated = existing.filter { it.pageIndex != curIdx } +
                        curAnn.copy(pageNote = mergedNote)
                    onBlockUpdated(block.copy(annotationsJson = PdfHelper.encodeAnnotations(updated)))
                }
            }
        )
    }
}

@Composable
private fun PdfLuxuryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val lux = ThemeManager.luxuryTokens
    val pillShape = RoundedCornerShape(100.dp)
    Surface(
        shape = pillShape,
        color = if (selected) lux.accent.copy(alpha = 0.14f) else lux.elevatedSurface,
        border = BorderStroke(1.dp, if (selected) lux.accent else lux.borderHairline),
        shadowElevation = 0.dp,
        modifier = Modifier.clip(pillShape).clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) lux.accent else lux.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun PdfMarkupSurface(
    block: NoteBlock.PdfBlock,
    isFullscreen: Boolean,
    onBlockUpdated: (NoteBlock.PdfBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val safePageCount = block.pageCount.coerceAtLeast(1)
    val currentPage = block.currentPage.coerceIn(0, safePageCount - 1)

    val allAnnotations = remember(block.annotationsJson) {
        PdfHelper.parseAnnotations(block.annotationsJson)
    }
    val currentPageAnn = remember(allAnnotations, currentPage) {
        allAnnotations.find { it.pageIndex == currentPage } ?: PdfPageAnnotation(pageIndex = currentPage)
    }

    var pageStrokes by remember(block.id, currentPage) {
        mutableStateOf(currentPageAnn.strokes)
    }
    var redoStack by remember(block.id, currentPage) { mutableStateOf(listOf<InkStroke>()) }
    var pageNoteText by remember(block.id, currentPage) {
        mutableStateOf(currentPageAnn.pageNote)
    }

    LaunchedEffect(block.annotationsJson, currentPage) {
        if (currentPageAnn.strokes != pageStrokes) {
            pageStrokes = currentPageAnn.strokes
        }
        if (currentPageAnn.pageNote != pageNoteText) {
            pageNoteText = currentPageAnn.pageNote
        }
    }

    var toolMode by remember { mutableStateOf(PdfToolMode.PEN) }
    var selectedColorIdx by remember { mutableIntStateOf(if (toolMode == PdfToolMode.HIGHLIGHTER) 4 else 1) }
    var strokeWidth by remember { mutableFloatStateOf(5f) }
    var shapeSnapEnabled by remember { mutableStateOf(false) }
    var stylusOnlyMode by remember { mutableStateOf(false) }

    // Zoom & pan state per page
    var zoomScale by remember(currentPage) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    val latestBlock by rememberUpdatedState(block)
    val latestAllAnnotations by rememberUpdatedState(allAnnotations)
    val latestPageNoteText by rememberUpdatedState(pageNoteText)
    val latestOnBlockUpdated by rememberUpdatedState(onBlockUpdated)

    // Live in-progress stroke (stored in normalized [0..1] coords)
    val activeNormPoints = remember { ArrayList<StrokePoint>(128) }
    var activeStrokeRevision by remember { mutableIntStateOf(0) }

    // Optimized Rainbow Vanishing Laser controller + on-demand frame ticker (0% CPU when idle)
    val laserController = remember(block.id, currentPage) {
        RainbowLaserController(minSpacing = 0.007f)
    }
    var isLaserDrawing by remember { mutableStateOf(false) }
    var runLaserLoop by remember { mutableStateOf(false) }
    var laserFrameTick by remember { mutableLongStateOf(0L) }
    var laserInputVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(runLaserLoop) {
        if (!runLaserLoop) return@LaunchedEffect
        while (isActive) {
            withFrameMillis { _ ->
                val now = System.currentTimeMillis()
                laserFrameTick = now
                val stillAlive = laserController.pruneExpired(now)
                if (!stillAlive && !isLaserDrawing) {
                    runLaserLoop = false
                }
            }
        }
    }

    // Rasterize current PDF page bitmap with bounded LRU cache on IO dispatcher
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    LaunchedEffect(block.filePath, currentPage, isFullscreen) {
        isRendering = true
        val widthPx = if (isFullscreen) 1440 else 1080
        val bmp = withContext(Dispatchers.IO) {
            PdfHelper.renderPageBitmapCached(block.filePath, currentPage, widthPx)
        }
        pageBitmap = bmp
        isRendering = false
    }

    fun persistCurrentPage(newStrokes: List<InkStroke>, newNote: String = latestPageNoteText) {
        val updatedList = latestAllAnnotations
            .filter { it.pageIndex != currentPage } +
            PdfPageAnnotation(pageIndex = currentPage, strokes = newStrokes, pageNote = newNote)
        latestOnBlockUpdated(
            latestBlock.copy(
                currentPage = currentPage,
                annotationsJson = PdfHelper.encodeAnnotations(updatedList)
            )
        )
    }

    val aspectRatio = remember(pageBitmap) {
        val b = pageBitmap
        if (b != null && b.width > 0 && b.height > 0) {
            b.width.toFloat() / b.height.toFloat()
        } else {
            0.707f // Standard A4 ratio fallback
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ─── Page Switcher + Zoom + Dark Inversion Row ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (isLuxury) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLuxury) {
                val prevCircleShape = CircleShape
                Surface(
                    shape = prevCircleShape,
                    color = lux.elevatedSurface,
                    border = BorderStroke(1.dp, lux.borderHairline),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(prevCircleShape)
                        .then(
                            if (currentPage > 0) Modifier.clickable {
                                onBlockUpdated(block.copy(currentPage = currentPage - 1))
                            } else Modifier
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous PDF Page",
                            tint = if (currentPage > 0) lux.textPrimary else lux.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = lux.elevatedSurface,
                    border = BorderStroke(1.dp, lux.borderHairline),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = "P.${currentPage + 1} / $safePageCount",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lux.textPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                val nextCircleShape = CircleShape
                Surface(
                    shape = nextCircleShape,
                    color = lux.elevatedSurface,
                    border = BorderStroke(1.dp, lux.borderHairline),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(nextCircleShape)
                        .then(
                            if (currentPage < safePageCount - 1) Modifier.clickable {
                                onBlockUpdated(block.copy(currentPage = currentPage + 1))
                            } else Modifier
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next PDF Page",
                            tint = if (currentPage < safePageCount - 1) lux.textPrimary else lux.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                PdfLuxuryPill(
                    label = if (block.invertDark) "NIGHT" else "LIGHT",
                    selected = block.invertDark,
                    onClick = { onBlockUpdated(block.copy(invertDark = !block.invertDark)) }
                )
                PdfLuxuryPill(
                    label = if (stylusOnlyMode) "STYLUS" else "TOUCH",
                    selected = stylusOnlyMode,
                    onClick = { stylusOnlyMode = !stylusOnlyMode }
                )
                if (zoomScale > 1.02f) {
                    PdfLuxuryPill(
                        label = "${(zoomScale * 100).toInt()}% RESET",
                        selected = true,
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        }
                    )
                }
            } else {
                FilledTonalIconButton(
                    onClick = {
                        if (currentPage > 0) {
                            onBlockUpdated(block.copy(currentPage = currentPage - 1))
                        }
                    },
                    enabled = currentPage > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous PDF Page")
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Page ${currentPage + 1} / $safePageCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        if (currentPage < safePageCount - 1) {
                            onBlockUpdated(block.copy(currentPage = currentPage + 1))
                        }
                    },
                    enabled = currentPage < safePageCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next PDF Page")
                }

                FilterChip(
                    selected = block.invertDark,
                    onClick = { onBlockUpdated(block.copy(invertDark = !block.invertDark)) },
                    label = { Text(if (block.invertDark) "Night PDF" else "Light PDF", fontSize = 11.sp) }
                )

                FilterChip(
                    selected = stylusOnlyMode,
                    onClick = { stylusOnlyMode = !stylusOnlyMode },
                    label = { Text(if (stylusOnlyMode) "Stylus Only" else "Touch Ink", fontSize = 11.sp) }
                )

                if (zoomScale > 1.02f) {
                    OutlinedButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("${(zoomScale * 100).toInt()}% Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ─── Annotation Tool Row ───
        if (isLuxury) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = lux.elevatedSurface,
                border = BorderStroke(1.dp, lux.borderHairline),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val toolItems = listOf(
                        PdfToolMode.PEN to "PEN",
                        PdfToolMode.HIGHLIGHTER to "HL",
                        PdfToolMode.ERASER to "ERASE",
                        PdfToolMode.VANISHING to "LASER",
                        PdfToolMode.PAN to "PAN"
                    )
                    toolItems.forEach { (mode, label) ->
                        val active = toolMode == mode
                        val toolPillShape = RoundedCornerShape(100.dp)
                        Surface(
                            shape = toolPillShape,
                            color = if (active) lux.accent.copy(alpha = 0.14f) else Color.Transparent,
                            border = if (active) BorderStroke(1.dp, lux.accent) else null,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .clip(toolPillShape)
                                .clickable {
                                    toolMode = mode
                                    if (mode == PdfToolMode.PEN && selectedColorIdx == 4) selectedColorIdx = 1
                                    if (mode == PdfToolMode.HIGHLIGHTER) selectedColorIdx = 4
                                }
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) lux.accent else lux.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    val snapPillShape = RoundedCornerShape(100.dp)
                    Surface(
                        shape = snapPillShape,
                        color = if (shapeSnapEnabled) lux.accent.copy(alpha = 0.14f) else Color.Transparent,
                        border = if (shapeSnapEnabled) BorderStroke(1.dp, lux.accent) else null,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .clip(snapPillShape)
                            .clickable { shapeSnapEnabled = !shapeSnapEnabled }
                    ) {
                        Text(
                            text = if (shapeSnapEnabled) "SNAP:ON" else "SNAP",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (shapeSnapEnabled) FontWeight.Bold else FontWeight.Medium,
                            color = if (shapeSnapEnabled) lux.accent else lux.textSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = toolMode == PdfToolMode.PEN,
                    onClick = {
                        toolMode = PdfToolMode.PEN
                        if (selectedColorIdx == 4) selectedColorIdx = 1
                    },
                    label = { Text("Pen") }
                )
                FilterChip(
                    selected = toolMode == PdfToolMode.HIGHLIGHTER,
                    onClick = {
                        toolMode = PdfToolMode.HIGHLIGHTER
                        selectedColorIdx = 4
                    },
                    label = { Text("Highlight") }
                )
                FilterChip(
                    selected = toolMode == PdfToolMode.ERASER,
                    onClick = { toolMode = PdfToolMode.ERASER },
                    label = { Text("Eraser") }
                )
                FilterChip(
                    selected = toolMode == PdfToolMode.VANISHING,
                    onClick = { toolMode = PdfToolMode.VANISHING },
                    label = { Text("Laser") }
                )
                FilterChip(
                    selected = toolMode == PdfToolMode.PAN,
                    onClick = { toolMode = PdfToolMode.PAN },
                    label = { Text("Pan") }
                )
                FilterChip(
                    selected = shapeSnapEnabled,
                    onClick = { shapeSnapEnabled = !shapeSnapEnabled },
                    label = { Text(if (shapeSnapEnabled) "Snap ON" else "Snap") }
                )
            }
        }

        // ─── Color Dots + Stroke Width + Undo / Redo / Clear ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLuxury) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PdfInkColors.forEachIndexed { idx, col ->
                    val selectedCol = idx == selectedColorIdx
                    Box(
                        modifier = Modifier
                            .size(if (selectedCol) 20.dp else 15.dp)
                            .clip(CircleShape)
                            .background(col, CircleShape)
                            .border(
                                width = if (selectedCol) (if (isLuxury) 1.5.dp else 2.dp) else 1.dp,
                                color = when {
                                    selectedCol && isLuxury -> lux.accent
                                    selectedCol -> MaterialTheme.colorScheme.primary
                                    isLuxury -> lux.borderHairline
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                            .clickable { selectedColorIdx = idx }
                    )
                }
            }

            if (isLuxury) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = lux.elevatedSurface,
                    border = BorderStroke(1.dp, lux.borderHairline),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "−",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = lux.textPrimary,
                            modifier = Modifier.clickable { strokeWidth = (strokeWidth - 2f).coerceIn(2f, 22f) }
                        )
                        Text(
                            text = "${strokeWidth.toInt()}PX",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = lux.accent
                        )
                        Text(
                            text = "+",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = lux.textPrimary,
                            modifier = Modifier.clickable { strokeWidth = (strokeWidth + 2f).coerceIn(2f, 22f) }
                        )
                    }
                }
            } else {
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 2f..22f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isLuxury) {
                    PdfLuxuryPill(
                        label = "↶",
                        selected = pageStrokes.isNotEmpty(),
                        onClick = {
                            if (pageStrokes.isNotEmpty()) {
                                redoStack = redoStack + pageStrokes.last()
                                pageStrokes = pageStrokes.dropLast(1)
                                persistCurrentPage(pageStrokes)
                            }
                        }
                    )
                    PdfLuxuryPill(
                        label = "↷",
                        selected = redoStack.isNotEmpty(),
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                pageStrokes = pageStrokes + redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                persistCurrentPage(pageStrokes)
                            }
                        }
                    )
                    if (pageStrokes.isNotEmpty()) {
                        PdfLuxuryPill(
                            label = "CLR",
                            selected = false,
                            onClick = {
                                redoStack = emptyList()
                                pageStrokes = emptyList()
                                persistCurrentPage(emptyList())
                            }
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (pageStrokes.isNotEmpty()) {
                                redoStack = redoStack + pageStrokes.last()
                                pageStrokes = pageStrokes.dropLast(1)
                                persistCurrentPage(pageStrokes)
                            }
                        },
                        enabled = pageStrokes.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("↶", fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                pageStrokes = pageStrokes + redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                persistCurrentPage(pageStrokes)
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("↷", fontSize = 14.sp)
                    }
                    if (pageStrokes.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                redoStack = emptyList()
                                pageStrokes = emptyList()
                                persistCurrentPage(emptyList())
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ─── PDF Page Viewport + Ink Overlay Canvas ───
        val pageContainerModifier = if (isFullscreen) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio.coerceIn(0.45f, 1.8f))
        }

        val pageBgColor = when {
            isLuxury && block.invertDark -> Color(0xFF000000)
            isLuxury -> Color(0xFFF4F1EA)
            block.invertDark -> Color(0xFF0B0B0D)
            else -> Color(0xFFF3F4F6)
        }
        val pageShape = RoundedCornerShape(if (isLuxury) 0.dp else 8.dp)

        BoxWithConstraints(
            modifier = pageContainerModifier
                .clip(pageShape)
                .then(
                    if (isLuxury) Modifier else Modifier.border(1.dp, ThemeManager.cardBorderColor, pageShape)
                )
                .background(pageBgColor),
            contentAlignment = Alignment.Center
        ) {
            val safeAspect = aspectRatio.coerceIn(0.45f, 1.8f)
            val fittedModifier = if (isFullscreen && maxHeight.value.isFinite() && maxWidth.value.isFinite()) {
                val heightFromWidth = maxWidth / safeAspect
                if (heightFromWidth <= maxHeight) {
                    Modifier.width(maxWidth).height(heightFromWidth)
                } else {
                    Modifier.height(maxHeight).width(maxHeight * safeAspect)
                }
            } else {
                Modifier.fillMaxSize()
            }

            Box(
                modifier = fittedModifier
                    .clipToBounds()
                    .pointerInput(
                        block.id,
                        currentPage,
                        toolMode,
                        selectedColorIdx,
                        strokeWidth,
                        shapeSnapEnabled,
                        stylusOnlyMode
                    ) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val active = changes.filter { it.pressed }

                                if (active.size >= 2) {
                                    // Two-finger pinch zoom & pan always works
                                    if (activeNormPoints.isNotEmpty()) {
                                        activeNormPoints.clear()
                                        activeStrokeRevision++
                                    }
                                    if (isLaserDrawing) {
                                        laserController.endStroke(System.currentTimeMillis())
                                        isLaserDrawing = false
                                        laserInputVersion++
                                    }
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    var cx = 0f
                                    var cy = 0f
                                    for (p in active) {
                                        cx += p.position.x
                                        cy += p.position.y
                                    }
                                    val centroid = Offset(cx / active.size, cy / active.size)
                                    val newZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                                    val factor = newZoom / zoomScale
                                    val rawPan = (panOffset - centroid) * factor + centroid + pan
                                    val maxPanX = 0f
                                    val minPanX = -(size.width * (newZoom - 1f))
                                    val maxPanY = 0f
                                    val minPanY = -(size.height * (newZoom - 1f))
                                    zoomScale = newZoom
                                    panOffset = if (newZoom <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            rawPan.x.coerceIn(minPanX, maxPanX),
                                            rawPan.y.coerceIn(minPanY, maxPanY)
                                        )
                                    }
                                    changes.forEach { it.consume() }
                                    continue
                                }

                                val single = active.firstOrNull()
                                if (single != null) {
                                    val isStylus = single.type == PointerType.Stylus
                                    val isHardwareEraser = single.type == PointerType.Eraser
                                    val effectivePanOnly =
                                        (toolMode == PdfToolMode.PAN && !isStylus && !isHardwareEraser) ||
                                            (stylusOnlyMode && !isStylus && !isHardwareEraser)

                                    if (effectivePanOnly) {
                                        if (activeNormPoints.isNotEmpty()) {
                                            activeNormPoints.clear()
                                            activeStrokeRevision++
                                        }
                                        if (isLaserDrawing) {
                                            laserController.endStroke(System.currentTimeMillis())
                                            isLaserDrawing = false
                                            laserInputVersion++
                                        }
                                        if (zoomScale > 1.01f || isFullscreen) {
                                            val delta = single.position - single.previousPosition
                                            val maxPanX = 0f
                                            val minPanX = -(size.width * (zoomScale - 1f))
                                            val maxPanY = 0f
                                            val minPanY = -(size.height * (zoomScale - 1f))
                                            panOffset = Offset(
                                                (panOffset.x + delta.x).coerceIn(minPanX, maxPanX),
                                                (panOffset.y + delta.y).coerceIn(minPanY, maxPanY)
                                            )
                                            single.consume()
                                        }
                                        continue
                                    }

                                    // Drawing or erasing directly on PDF coordinate space
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val pageX = (single.position.x - panOffset.x) / zoomScale
                                    val pageY = (single.position.y - panOffset.y) / zoomScale
                                    val nx = (pageX / w).coerceIn(0f, 1f)
                                    val ny = (pageY / h).coerceIn(0f, 1f)

                                    if (toolMode == PdfToolMode.VANISHING) {
                                        val now = System.currentTimeMillis()
                                        if (!isLaserDrawing) {
                                            isLaserDrawing = true
                                            laserController.beginStroke(nx, ny, now)
                                            runLaserLoop = true
                                            laserInputVersion++
                                        } else if (laserController.addSample(nx, ny, now)) {
                                            laserInputVersion++
                                        }
                                        changes.forEach { it.consume() }
                                        continue
                                    }

                                    val lastPt = activeNormPoints.lastOrNull()
                                    if (lastPt == null || (nx - lastPt.x) * (nx - lastPt.x) + (ny - lastPt.y) * (ny - lastPt.y) >= 0.000004f) {
                                        activeNormPoints.add(StrokePoint(nx, ny))
                                        activeStrokeRevision++
                                    }
                                    changes.forEach { it.consume() }
                                } else if (isLaserDrawing) {
                                    laserController.endStroke(System.currentTimeMillis())
                                    isLaserDrawing = false
                                    laserInputVersion++
                                } else if (activeNormPoints.isNotEmpty()) {
                                    // Pointer released — commit PDF stroke or erase
                                    val releasedMode = toolMode
                                    when (releasedMode) {
                                        PdfToolMode.ERASER -> {
                                            val eraserPts = activeNormPoints.toList()
                                            val threshSq = 0.035f * 0.035f
                                            val filtered = pageStrokes.filter { stroke ->
                                                stroke.points.none { sp ->
                                                    eraserPts.any { ep ->
                                                        val dx = sp.x - ep.x
                                                        val dy = sp.y - ep.y
                                                        dx * dx + dy * dy < threshSq
                                                    }
                                                }
                                            }
                                            if (filtered.size != pageStrokes.size) {
                                                pageStrokes = filtered
                                                persistCurrentPage(filtered)
                                            }
                                        }
                                        PdfToolMode.VANISHING -> Unit
                                        PdfToolMode.PEN, PdfToolMode.HIGHLIGHTER -> {
                                            if (activeNormPoints.size >= 2) {
                                                val snapshot = activeNormPoints.toList()
                                                val finalPts = if (shapeSnapEnabled) {
                                                    snapNormalizedPoints(snapshot)
                                                } else {
                                                    snapshot
                                                }
                                                val widthToUse = if (releasedMode == PdfToolMode.HIGHLIGHTER) {
                                                    (strokeWidth * 2.6f).coerceAtLeast(14f)
                                                } else {
                                                    strokeWidth
                                                }
                                                val newStroke = InkStroke(
                                                    points = finalPts,
                                                    colorHex = PdfInkColorHex[selectedColorIdx],
                                                    strokeWidth = widthToUse,
                                                    isHighlighter = releasedMode == PdfToolMode.HIGHLIGHTER,
                                                    isEraser = false
                                                )
                                                pageStrokes = pageStrokes + newStroke
                                                redoStack = emptyList()
                                                persistCurrentPage(pageStrokes)
                                            }
                                        }
                                        PdfToolMode.PAN -> Unit
                                    }
                                    activeNormPoints.clear()
                                    activeStrokeRevision++
                                }
                            }
                        }
                    }
            ) {
                var cachedStrokesRef by remember { mutableStateOf<List<InkStroke>?>(null) }
                var cachedDimsKey by remember { mutableLongStateOf(0L) }
                val cachedCommittedPaths = remember { ArrayList<Pair<Path, InkStroke>>() }
                val activeStrokePath = remember { Path() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = panOffset.x
                            translationY = panOffset.y
                            scaleX = zoomScale
                            scaleY = zoomScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    val bmp = pageBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (block.invertDark) ColorFilter.colorMatrix(DarkPdfColorMatrix) else null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isRendering) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "PDF file missing on device (${block.fileName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Ink Overlay Canvas (scales cleanly in sync with the PDF bitmap)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val _t = laserFrameTick
                        val _inp = laserInputVersion
                        val _activeRev = activeStrokeRevision
                        val w = size.width
                        val h = size.height
                        val widthScale = (w / 420f).coerceAtLeast(0.75f)

                        val dimsKey = (w.toInt().toLong() shl 32) or (h.toInt().toLong() and 0xffffffffL)
                        if (cachedStrokesRef !== pageStrokes || cachedDimsKey != dimsKey) {
                            cachedStrokesRef = pageStrokes
                            cachedDimsKey = dimsKey
                            cachedCommittedPaths.clear()
                            for (stroke in pageStrokes) {
                                if (stroke.points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
                                        for (i in 1 until stroke.points.size) {
                                            lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                                        }
                                    }
                                    cachedCommittedPaths.add(path to stroke)
                                }
                            }
                        }

                        // Committed strokes on this PDF page (pre-built Path + LRU color parse)
                        for ((path, stroke) in cachedCommittedPaths) {
                            val alpha = if (stroke.isHighlighter) 0.42f else 1f
                            val parsed = NoteJson.parseColorCached(stroke.colorHex, Color.Red).copy(alpha = alpha)
                            drawPath(
                                path = path,
                                color = parsed,
                                style = Stroke(
                                    width = stroke.strokeWidth * widthScale,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Active in-progress stroke (non-laser)
                        if (_activeRev >= 0 && activeNormPoints.size >= 2 &&
                            (toolMode == PdfToolMode.PEN || toolMode == PdfToolMode.HIGHLIGHTER)
                        ) {
                            activeStrokePath.rewind()
                            activeStrokePath.moveTo(activeNormPoints[0].x * w, activeNormPoints[0].y * h)
                            for (i in 1 until activeNormPoints.size) {
                                activeStrokePath.lineTo(activeNormPoints[i].x * w, activeNormPoints[i].y * h)
                            }
                            val isHi = toolMode == PdfToolMode.HIGHLIGHTER
                            val widthToDraw = if (isHi) (strokeWidth * 2.6f).coerceAtLeast(14f) else strokeWidth
                            val color = PdfInkColors[selectedColorIdx].copy(alpha = if (isHi) 0.42f else 1f)
                            drawPath(
                                path = activeStrokePath,
                                color = color,
                                style = Stroke(
                                    width = widthToDraw * widthScale,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Batched Rainbow Vanishing Laser trails (normalized [0..1] scaled to PDF dims)
                        if (laserController.hasAnyContent) {
                            val nowMs = if (_t > 0L) maxOf(_t, System.currentTimeMillis()) else System.currentTimeMillis()
                            laserController.drawInto(
                                scope = this,
                                nowMs = nowMs,
                                baseStrokeWidthPx = (strokeWidth + 3f) * widthScale,
                                scaleX = w,
                                scaleY = h
                            )
                        }
                    }
                }
            }
        }

        // ─── Per-Page Margin Note (Searchable + Exportable alongside PDF page) ───
        OutlinedTextField(
            value = pageNoteText,
            onValueChange = { updatedText ->
                pageNoteText = updatedText
                persistCurrentPage(pageStrokes, updatedText)
            },
            placeholder = {
                Text(
                    text = if (isLuxury) "MARGIN NOTE P.${currentPage + 1}..." else "Margin notes for page ${currentPage + 1}…",
                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                    fontSize = if (isLuxury) 11.sp else 13.sp
                )
            },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(if (isLuxury) 6.dp else 12.dp),
            colors = if (isLuxury) {
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = lux.accent,
                    unfocusedBorderColor = lux.borderHairline,
                    focusedContainerColor = lux.recessedSurface,
                    unfocusedContainerColor = lux.recessedSurface,
                    focusedTextColor = lux.textPrimary,
                    unfocusedTextColor = lux.textPrimary
                )
            } else OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLuxury) 12.dp else 0.dp),
            textStyle = if (isLuxury) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
        )
    }
}

private fun snapNormalizedPoints(normPoints: List<StrokePoint>): List<StrokePoint> {
    if (normPoints.size < 5) return normPoints
    val scaled = normPoints.map { StrokePoint(it.x * 1000f, it.y * 1000f) }
    val snapped = snapShapeInVirtualSpace(scaled)
    return snapped.map { StrokePoint((it.x / 1000f).coerceIn(0f, 1f), (it.y / 1000f).coerceIn(0f, 1f)) }
}

private fun snapShapeInVirtualSpace(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 5) return points
    val start = points.first()
    val end = points.last()
    val dx = end.x - start.x
    val dy = end.y - start.y
    val endToEndDist = sqrt(dx * dx + dy * dy)

    var totalLength = 0f
    for (i in 1 until points.size) {
        val segDx = points[i].x - points[i - 1].x
        val segDy = points[i].y - points[i - 1].y
        totalLength += sqrt(segDx * segDx + segDy * segDy)
    }

    if (endToEndDist > 36f && totalLength < endToEndDist * 1.14f) {
        return listOf(start, end)
    }

    if (endToEndDist < 48f && totalLength > 95f) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        val avgRadius = (width + height) / 4f
        if (avgRadius > 10f) {
            var radiusDeviance = 0f
            for (p in points) {
                val dist = sqrt((p.x - centerX) * (p.x - centerX) + (p.y - centerY) * (p.y - centerY))
                radiusDeviance += abs(dist - avgRadius)
            }
            if (radiusDeviance / points.size < avgRadius * 0.22f) {
                val circlePoints = mutableListOf<StrokePoint>()
                for (step in 0..36) {
                    val angle = (2.0 * Math.PI * step / 36.0)
                    circlePoints.add(
                        StrokePoint(
                            x = (centerX + (width / 2f) * cos(angle)).toFloat(),
                            y = (centerY + (height / 2f) * sin(angle)).toFloat()
                        )
                    )
                }
                return circlePoints
            }
        }

        if (width > 28f && height > 28f) {
            return listOf(
                StrokePoint(minX, minY),
                StrokePoint(maxX, minY),
                StrokePoint(maxX, maxY),
                StrokePoint(minX, maxY),
                StrokePoint(minX, minY)
            )
        }
    }

    return points
}
