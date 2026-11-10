package com.notegraph.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.NoteGraphColors
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.isActive

/**
 * Drawing modes for the ink canvas.
 */
enum class InkMode {
    PEN,
    HIGHLIGHTER,
    ERASER,
    VANISHING_PEN, // Rainbow pointing pen that smoothly fades away
    LASSO          // Dashed loop selection to move, scale, or delete strokes
}

val InkPresetColors = listOf(
    Color.White,
    Color(0xFFFF5252),   // vibrant red
    Color(0xFF4FC3F7),   // light blue
    Color(0xFF81C784),   // green
    Color(0xFFFFD54F)    // yellow
)

private val InkPresetColorHex = listOf(
    "#FFFFFF",
    "#FF5252",
    "#4FC3F7",
    "#81C784",
    "#FFD54F"
)

/**
 * Advanced ink drawing canvas supporting:
 * - Pen, Highlighter, and Eraser
 * - Rainbow Vanishing Pen with dynamic RGB cycling and 1.5s fade out
 * - Lasso Selection Tool with dashed boundary loop, move, scale, and delete actions
 * - Shape Snap (auto-straighten lines, snap circles, rectangles, triangles)
 * - Prominent Undo/Redo controls
 */
@Composable
fun InkCanvas(
    strokes: List<InkStroke>,
    heightDp: Float,
    paperTemplate: String = "DEFAULT",
    onTemplateChanged: ((String) -> Unit)? = null,
    onStrokesChanged: (List<InkStroke>) -> Unit
) {
    var showFullscreenCanvas by remember { mutableStateOf(false) }

    InkCanvasContent(
        strokes = strokes,
        heightDp = heightDp,
        isFullscreen = false,
        paperTemplate = paperTemplate,
        onTemplateChanged = onTemplateChanged,
        onToggleFullscreen = { showFullscreenCanvas = true },
        onStrokesChanged = onStrokesChanged
    )

    if (showFullscreenCanvas) {
        val isLuxuryDialog = ThemeManager.isLuxuryUi
        val luxDialog = ThemeManager.luxuryTokens
        Dialog(
            onDismissRequest = { showFullscreenCanvas = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isLuxuryDialog) luxDialog.canvasBackground else MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = if (isLuxuryDialog) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isLuxuryDialog) 14.dp else 0.dp,
                                vertical = if (isLuxuryDialog) 8.dp else 0.dp
                            )
                            .padding(bottom = if (isLuxuryDialog) 4.dp else 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLuxuryDialog) {
                            Text(
                                text = "FULLSCREEN CANVAS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = luxDialog.textSecondary
                            )
                            Surface(
                                onClick = { showFullscreenCanvas = false },
                                shape = RoundedCornerShape(100.dp),
                                color = luxDialog.elevatedSurface,
                                border = BorderStroke(1.dp, luxDialog.borderHairline),
                                shadowElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Done",
                                        tint = luxDialog.textPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "DONE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp,
                                        color = luxDialog.textPrimary
                                    )
                                }
                            }
                        } else {
                            Text("Fullscreen Sketch Studio", style = MaterialTheme.typography.titleMedium)
                            FilledTonalButton(onClick = { showFullscreenCanvas = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Done", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Done")
                            }
                        }
                    }
                    InkCanvasContent(
                        strokes = strokes,
                        heightDp = heightDp,
                        isFullscreen = true,
                        paperTemplate = paperTemplate,
                        onTemplateChanged = onTemplateChanged,
                        onToggleFullscreen = { showFullscreenCanvas = false },
                        onStrokesChanged = onStrokesChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private val PAPER_TEMPLATE_OPTIONS = listOf(
    "DEFAULT" to "Theme",
    "DOTS" to "Dots",
    "GRID" to "Grid",
    "LINED" to "Lined",
    "CORNELL" to "Cornell",
    "BLANK" to "Blank"
)

@Composable
private fun InkCanvasContent(
    strokes: List<InkStroke>,
    heightDp: Float,
    isFullscreen: Boolean,
    paperTemplate: String,
    onTemplateChanged: ((String) -> Unit)?,
    onToggleFullscreen: () -> Unit,
    onStrokesChanged: (List<InkStroke>) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(InkMode.PEN) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var strokeWidth by remember { mutableFloatStateOf(4f) }
    var shapeSnapEnabled by remember { mutableStateOf(false) }
    var stylusOnlyMode by remember { mutableStateOf(false) }

    // Committed strokes & undo/redo snapshot stacks
    var committedStrokes by remember { mutableStateOf(strokes) }
    LaunchedEffect(strokes) {
        if (committedStrokes != strokes) {
            committedStrokes = strokes
        }
    }
    val latestOnStrokesChanged by rememberUpdatedState(onStrokesChanged)
    var undoHistory by remember { mutableStateOf<List<List<InkStroke>>>(emptyList()) }
    var redoHistory by remember { mutableStateOf<List<List<InkStroke>>>(emptyList()) }

    // In-progress stroke points (mutable list + revision counter avoids O(n^2) list copying at 240Hz)
    val currentPoints = remember { ArrayList<StrokePoint>(128) }
    var activeStrokeRevision by remember { mutableIntStateOf(0) }

    // Optimized Rainbow Vanishing Laser controller + on-demand frame ticker (0% CPU when idle)
    val laserController = remember { RainbowLaserController(minSpacing = 6f) }
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

    // Lasso selection & interactive drag-to-move state
    var selectedStrokeIndices by remember { mutableStateOf(setOf<Int>()) }
    var isLassoMoveActive by remember { mutableStateOf(false) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var activeDragPointerId by remember { mutableStateOf<PointerId?>(null) }
    var dragStartSnapshot by remember { mutableStateOf<List<InkStroke>?>(null) }
    var lastDragPos by remember { mutableStateOf<Offset?>(null) }

    fun commitStrokeEdit(newStrokes: List<InkStroke>) {
        undoHistory = (undoHistory + listOf(committedStrokes)).takeLast(40)
        redoHistory = emptyList()
        committedStrokes = newStrokes
        latestOnStrokesChanged(newStrokes)
    }

    fun switchMode(newMode: InkMode) {
        mode = newMode
        currentPoints.clear()
        activeStrokeRevision++
        if (isLaserDrawing) {
            laserController.endStroke(System.currentTimeMillis())
            isLaserDrawing = false
            laserInputVersion++
        }
        isDraggingSelection = false
        activeDragPointerId = null
        dragStartSnapshot = null
        lastDragPos = null
        if (newMode != InkMode.LASSO) {
            selectedStrokeIndices = emptySet()
            isLassoMoveActive = false
        }
    }

    val themeMode = ThemeManager.currentTheme
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val selectionAccent = when {
        isLuxury -> lux.accent
        themeMode == AppThemeMode.KINDLE_PARCHMENT -> Color(0xFF0284C7)
        else -> Color(0xFF00E5FF)
    }
    val activeInkColors = remember(themeMode, isLuxury) {
        if (themeMode == AppThemeMode.KINDLE_PARCHMENT) {
            listOf(
                if (isLuxury) Color(0xFF1E1B18) else NoteGraphColors.KindleParchmentCharcoalText,
                Color(0xFFD32F2F),
                Color(0xFF0288D1),
                Color(0xFF388E3C),
                NoteGraphColors.KindleParchmentAccent
            )
        } else {
            InkPresetColors
        }
    }
    val activeInkColorHex = remember(themeMode) {
        if (themeMode == AppThemeMode.KINDLE_PARCHMENT) {
            listOf("#2C2C2C", "#D32F2F", "#0288D1", "#388E3C", "#B45309")
        } else {
            InkPresetColorHex
        }
    }
    val canvasBackground = if (isLuxury) {
        lux.canvasBackground
    } else {
        when (themeMode) {
            AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF07030F)
            AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentBackground
            AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioBackground
        }
    }

    fun performUndo() {
        if (undoHistory.isNotEmpty()) {
            val prev = undoHistory.last()
            undoHistory = undoHistory.dropLast(1)
            redoHistory = (redoHistory + listOf(committedStrokes)).takeLast(40)
            committedStrokes = prev
            selectedStrokeIndices = emptySet()
            isLassoMoveActive = false
            latestOnStrokesChanged(prev)
        } else if (committedStrokes.isNotEmpty()) {
            val prev = committedStrokes.dropLast(1)
            redoHistory = (redoHistory + listOf(committedStrokes)).takeLast(40)
            committedStrokes = prev
            selectedStrokeIndices = emptySet()
            isLassoMoveActive = false
            latestOnStrokesChanged(prev)
        }
    }

    fun performRedo() {
        if (redoHistory.isNotEmpty()) {
            val next = redoHistory.last()
            redoHistory = redoHistory.dropLast(1)
            undoHistory = (undoHistory + listOf(committedStrokes)).takeLast(40)
            committedStrokes = next
            selectedStrokeIndices = emptySet()
            isLassoMoveActive = false
            latestOnStrokesChanged(next)
        }
    }

    Column(
        modifier = if (isLuxury) {
            modifier.fillMaxWidth()
        } else {
            modifier
                .fillMaxWidth()
                .border(0.75.dp, ThemeManager.cardBorderColor, RoundedCornerShape(12.dp))
        }
    ) {
        if (isLuxury) {
            // Razor-thin floating pill toolbar with 1px borders and zero drop shadows
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(canvasBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = lux.elevatedSurface,
                    border = BorderStroke(1.dp, lux.borderHairline),
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LuxuryInkPillTab("PEN", mode == InkMode.PEN, lux.accent, lux.textSecondary) { switchMode(InkMode.PEN) }
                        LuxuryInkPillTab("HI", mode == InkMode.HIGHLIGHTER, lux.accent, lux.textSecondary) { switchMode(InkMode.HIGHLIGHTER) }
                        LuxuryInkPillTab("ERASE", mode == InkMode.ERASER, lux.accent, lux.textSecondary) { switchMode(InkMode.ERASER) }
                        LuxuryInkPillTab("LASER", mode == InkMode.VANISHING_PEN, lux.accent, lux.textSecondary) { switchMode(InkMode.VANISHING_PEN) }
                        LuxuryInkPillTab("LASSO", mode == InkMode.LASSO, lux.accent, lux.textSecondary) { switchMode(InkMode.LASSO) }

                        Box(Modifier.width(1.dp).height(14.dp).background(lux.dividerRule))

                        activeInkColors.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { selectedColorIndex = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (idx == selectedColorIndex) 15.dp else 11.dp)
                                        .background(color, CircleShape)
                                        .border(
                                            width = if (idx == selectedColorIndex) 1.5.dp else 0.75.dp,
                                            color = if (idx == selectedColorIndex) lux.accent else lux.borderHairline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        Box(Modifier.width(1.dp).height(14.dp).background(lux.dividerRule))

                        LuxuryInkPillTab("-", false, lux.accent, lux.textSecondary) {
                            strokeWidth = (strokeWidth - 2f).coerceIn(1f, 24f)
                        }
                        Text(
                            text = "${strokeWidth.toInt()}PX",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = lux.textPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable {
                                    strokeWidth = when {
                                        strokeWidth < 3f -> 4f
                                        strokeWidth < 7f -> 8f
                                        strokeWidth < 12f -> 14f
                                        else -> 2f
                                    }
                                }
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                        LuxuryInkPillTab("+", false, lux.accent, lux.textSecondary) {
                            strokeWidth = (strokeWidth + 2f).coerceIn(1f, 24f)
                        }

                        Box(Modifier.width(1.dp).height(14.dp).background(lux.dividerRule))

                        LuxuryInkPillTab("SNAP", shapeSnapEnabled, lux.accent, lux.textMuted) {
                            shapeSnapEnabled = !shapeSnapEnabled
                        }
                        LuxuryInkPillTab(if (stylusOnlyMode) "S-PEN" else "TOUCH", stylusOnlyMode, lux.accent, lux.textMuted) {
                            stylusOnlyMode = !stylusOnlyMode
                        }
                        if (onTemplateChanged != null) {
                            val curIdx = PAPER_TEMPLATE_OPTIONS.indexOfFirst { it.first == paperTemplate }.coerceAtLeast(0)
                            val curLabel = PAPER_TEMPLATE_OPTIONS[curIdx].second.uppercase()
                            LuxuryInkPillTab(curLabel, paperTemplate != "DEFAULT", lux.accent, lux.textMuted) {
                                val next = PAPER_TEMPLATE_OPTIONS[(curIdx + 1) % PAPER_TEMPLATE_OPTIONS.size].first
                                onTemplateChanged(next)
                            }
                        }

                        Box(Modifier.width(1.dp).height(14.dp).background(lux.dividerRule))

                        val canUndo = undoHistory.isNotEmpty() || committedStrokes.isNotEmpty()
                        LuxuryInkPillTab(
                            label = "↶",
                            selected = false,
                            accent = lux.accent,
                            muted = if (canUndo) lux.textPrimary else lux.textMuted.copy(alpha = 0.4f),
                            onClick = { if (canUndo) performUndo() }
                        )
                        LuxuryInkPillTab(
                            label = "↷",
                            selected = false,
                            accent = lux.accent,
                            muted = if (redoHistory.isNotEmpty()) lux.textPrimary else lux.textMuted.copy(alpha = 0.4f),
                            onClick = { if (redoHistory.isNotEmpty()) performRedo() }
                        )
                        if (!isFullscreen) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Expand Canvas",
                                tint = lux.textSecondary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onToggleFullscreen)
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = lux.dividerRule, thickness = 1.dp)

            if (selectedStrokeIndices.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(canvasBackground)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = lux.elevatedSurface,
                        border = BorderStroke(1.dp, lux.accent),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${selectedStrokeIndices.size} SEL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lux.accent
                            )
                            LuxuryInkPillTab(
                                label = if (isLassoMoveActive) "MOVE ON" else "MOVE",
                                selected = isLassoMoveActive,
                                accent = lux.accent,
                                muted = lux.textPrimary,
                                onClick = { isLassoMoveActive = !isLassoMoveActive }
                            )
                            LuxuryInkPillTab("+20%", false, lux.accent, lux.textSecondary) {
                                commitStrokeEdit(scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 1.2f))
                            }
                            LuxuryInkPillTab("-20%", false, lux.accent, lux.textSecondary) {
                                commitStrokeEdit(scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 0.8f))
                            }
                            LuxuryInkPillTab("DEL", false, Color(0xFFEF4444), Color(0xFFEF4444)) {
                                val remaining = committedStrokes.filterIndexed { idx, _ -> idx !in selectedStrokeIndices }
                                selectedStrokeIndices = emptySet()
                                isLassoMoveActive = false
                                commitStrokeEdit(remaining)
                            }
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Deselect",
                                tint = lux.textMuted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        selectedStrokeIndices = emptySet()
                                        isLassoMoveActive = false
                                    }
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ─── Classic Toolbar Row 1: Mode chips, S-Pen Palm Rejection, Paper Template & Expand ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FilterChip(
                    selected = mode == InkMode.PEN,
                    onClick = { switchMode(InkMode.PEN) },
                    label = { Text("Pen") }
                )
                FilterChip(
                    selected = mode == InkMode.HIGHLIGHTER,
                    onClick = { switchMode(InkMode.HIGHLIGHTER) },
                    label = { Text("Highlight") }
                )
                FilterChip(
                    selected = mode == InkMode.ERASER,
                    onClick = { switchMode(InkMode.ERASER) },
                    label = { Text("Eraser") }
                )
                FilterChip(
                    selected = mode == InkMode.VANISHING_PEN,
                    onClick = { switchMode(InkMode.VANISHING_PEN) },
                    label = { Text("Laser") }
                )
                FilterChip(
                    selected = mode == InkMode.LASSO,
                    onClick = { switchMode(InkMode.LASSO) },
                    label = { Text("Lasso") }
                )
                FilterChip(
                    selected = shapeSnapEnabled,
                    onClick = { shapeSnapEnabled = !shapeSnapEnabled },
                    label = { Text(if (shapeSnapEnabled) "Snap ON" else "Snap") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                FilterChip(
                    selected = stylusOnlyMode,
                    onClick = { stylusOnlyMode = !stylusOnlyMode },
                    label = { Text(if (stylusOnlyMode) "Stylus Only" else "Touch", fontSize = 11.sp) }
                )
                if (onTemplateChanged != null) {
                    val curIdx = PAPER_TEMPLATE_OPTIONS.indexOfFirst { it.first == paperTemplate }.coerceAtLeast(0)
                    val curLabel = PAPER_TEMPLATE_OPTIONS[curIdx].second
                    AssistChip(
                        onClick = {
                            val next = PAPER_TEMPLATE_OPTIONS[(curIdx + 1) % PAPER_TEMPLATE_OPTIONS.size].first
                            onTemplateChanged(next)
                        },
                        label = { Text("Paper: $curLabel", fontSize = 11.sp) }
                    )
                }
                if (!isFullscreen) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "Expand Sketch Studio",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ─── Classic Toolbar Row 2: Color dots, Width Slider, and Undo / Redo Buttons ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeInkColors.forEachIndexed { idx, color ->
                        IconButton(
                            onClick = { selectedColorIndex = idx },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (idx == selectedColorIndex) 20.dp else 14.dp)
                                    .background(color, CircleShape)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .then(
                                        if (idx == selectedColorIndex)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                ) {
                    Text("W", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 1f..24f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { performUndo() },
                        enabled = undoHistory.isNotEmpty() || committedStrokes.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("↶ Undo", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = { performRedo() },
                        enabled = redoHistory.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("↷ Redo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ─── Classic Lasso Selection Action Bar ───
            if (selectedStrokeIndices.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${selectedStrokeIndices.size} selected",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            if (isLassoMoveActive) {
                                Button(
                                    onClick = { isLassoMoveActive = false },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Move ON", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { isLassoMoveActive = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Move", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    commitStrokeEdit(scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 1.2f))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Scale +", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    commitStrokeEdit(scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 0.8f))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Scale -", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    val remaining = committedStrokes.filterIndexed { idx, _ -> idx !in selectedStrokeIndices }
                                    selectedStrokeIndices = emptySet()
                                    isLassoMoveActive = false
                                    commitStrokeEdit(remaining)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Delete", style = MaterialTheme.typography.labelSmall)
                            }

                            IconButton(
                                onClick = {
                                    selectedStrokeIndices = emptySet()
                                    isLassoMoveActive = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Deselect", modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = if (isLassoMoveActive) {
                                "Drag anywhere on the canvas to move the sketch freely. Tap Move ON to draw a new lasso."
                            } else {
                                "Drag inside the dashed selection box to move freely, or tap Move."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // ─── Drawing Canvas ───
        val canvasHeightModifier = if (isFullscreen) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().height(heightDp.dp)
        }

        val cachedCommittedPaths = remember(committedStrokes) {
            committedStrokes.map { stroke ->
                if (stroke.points.size >= 2) {
                    Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                } else {
                    null
                }
            }
        }
        val activeStrokePath = remember { Path() }
        val lassoDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }
        val density = LocalDensity.current
        val ruleStepPx = with(density) { 28.dp.toPx() }
        val gridStepPx = with(density) { 24.dp.toPx() }
        val cueXPx = with(density) { 48.dp.toPx() }
        val cachedRulePath = remember { Path() }
        val cachedDotOffsets = remember { ArrayList<Offset>(256) }
        var cachedRulerKey by remember { mutableStateOf("") }

        Canvas(
            modifier = canvasHeightModifier
                .clipToBounds()
                .background(
                    color = canvasBackground,
                    shape = if (isLuxury) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                .pointerInput(mode, selectedColorIndex, strokeWidth, shapeSnapEnabled, stylusOnlyMode, themeMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            if (isDraggingSelection) {
                                val tracked = event.changes.firstOrNull { it.id == activeDragPointerId }
                                if (tracked != null && tracked.pressed) {
                                    val isStylusOrEraser =
                                        tracked.type == PointerType.Stylus || tracked.type == PointerType.Eraser
                                    if (!stylusOnlyMode || isStylusOrEraser) {
                                        val prev = lastDragPos
                                        if (prev != null) {
                                            val dx = tracked.position.x - prev.x
                                            val dy = tracked.position.y - prev.y
                                            if (dx != 0f || dy != 0f) {
                                                committedStrokes = translateSelectedStrokes(
                                                    strokes = committedStrokes,
                                                    selectedIndices = selectedStrokeIndices,
                                                    dx = dx,
                                                    dy = dy,
                                                    canvasWidth = size.width.toFloat(),
                                                    canvasHeight = size.height.toFloat()
                                                )
                                                lastDragPos = tracked.position
                                            }
                                        } else {
                                            lastDragPos = tracked.position
                                        }
                                    }
                                    tracked.consume()
                                } else {
                                    val snapshot = dragStartSnapshot
                                    if (snapshot != null && snapshot != committedStrokes) {
                                        undoHistory = (undoHistory + listOf(snapshot)).takeLast(40)
                                        redoHistory = emptyList()
                                        latestOnStrokesChanged(committedStrokes)
                                    }
                                    isDraggingSelection = false
                                    activeDragPointerId = null
                                    dragStartSnapshot = null
                                    lastDragPos = null
                                }
                                continue
                            }

                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.size == 1) {
                                val first = activeChanges.first()
                                val isStylusOrEraser = first.type == PointerType.Stylus || first.type == PointerType.Eraser
                                if (stylusOnlyMode && !isStylusOrEraser) {
                                    // Palm rejection: ignore bare touch when S-Pen Only is active
                                    continue
                                }

                                if (mode == InkMode.VANISHING_PEN) {
                                    val now = System.currentTimeMillis()
                                    if (!isLaserDrawing) {
                                        isLaserDrawing = true
                                        laserController.beginStroke(first.position.x, first.position.y, now)
                                        runLaserLoop = true
                                        laserInputVersion++
                                    } else if (laserController.addSample(first.position.x, first.position.y, now)) {
                                        laserInputVersion++
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                // Enter interactive drag-to-move when pressing inside selection bounds or with Move mode active
                                if (currentPoints.isEmpty() &&
                                    mode == InkMode.LASSO &&
                                    selectedStrokeIndices.isNotEmpty()
                                ) {
                                    val bounds = computeStrokeSetBounds(committedStrokes, selectedStrokeIndices)
                                    val inSelectionBox = bounds?.contains(
                                        first.position.x,
                                        first.position.y,
                                        padding = 24f
                                    ) == true
                                    if (isLassoMoveActive || inSelectionBox) {
                                        isDraggingSelection = true
                                        activeDragPointerId = first.id
                                        dragStartSnapshot = committedStrokes
                                        lastDragPos = first.position
                                        first.consume()
                                        continue
                                    }
                                }

                                val lastPt = currentPoints.lastOrNull()
                                val px = first.position.x
                                val py = first.position.y
                                if (lastPt == null || (px - lastPt.x) * (px - lastPt.x) + (py - lastPt.y) * (py - lastPt.y) >= 2.25f) {
                                    currentPoints.add(StrokePoint(px, py))
                                    activeStrokeRevision++
                                }
                                event.changes.forEach { it.consume() }
                            } else if (activeChanges.size > 1) {
                                if (isLaserDrawing) {
                                    laserController.endStroke(System.currentTimeMillis())
                                    isLaserDrawing = false
                                    laserInputVersion++
                                }
                                // Ignore multi-touch palm/scroll gestures cleanly
                                continue
                            } else if (isLaserDrawing) {
                                laserController.endStroke(System.currentTimeMillis())
                                isLaserDrawing = false
                                laserInputVersion++
                            } else if (currentPoints.isNotEmpty()) {
                                // Pointer released after drawing
                                when (mode) {
                                    InkMode.ERASER -> {
                                        val eraserPts = currentPoints.toList()
                                        val threshold = 24f * 24f
                                        val remaining = committedStrokes.filter { stroke ->
                                            stroke.points.none { sp ->
                                                eraserPts.any { ep ->
                                                    val dx = sp.x - ep.x
                                                    val dy = sp.y - ep.y
                                                    dx * dx + dy * dy < threshold
                                                }
                                            }
                                        }
                                        selectedStrokeIndices = emptySet()
                                        isLassoMoveActive = false
                                        if (remaining != committedStrokes) {
                                            commitStrokeEdit(remaining)
                                        }
                                    }

                                    InkMode.VANISHING_PEN -> Unit

                                    InkMode.LASSO -> {
                                        if (currentPoints.size >= 3) {
                                            val loop = currentPoints.toList()
                                            val matched = mutableSetOf<Int>()
                                            committedStrokes.forEachIndexed { idx, s ->
                                                if (isStrokeInsideLasso(s, loop)) {
                                                    matched.add(idx)
                                                }
                                            }
                                            selectedStrokeIndices = matched
                                            if (matched.isEmpty()) {
                                                isLassoMoveActive = false
                                            }
                                        } else {
                                            // Simple tap outside clears selection
                                            selectedStrokeIndices = emptySet()
                                            isLassoMoveActive = false
                                        }
                                    }

                                    InkMode.PEN, InkMode.HIGHLIGHTER -> {
                                        if (currentPoints.size >= 2) {
                                            val snapped = if (shapeSnapEnabled) {
                                                snapShape(currentPoints.toList())
                                            } else {
                                                currentPoints.toList()
                                            }

                                            val newStroke = InkStroke(
                                                points = snapped,
                                                colorHex = activeInkColorHex[selectedColorIndex],
                                                strokeWidth = strokeWidth,
                                                isHighlighter = mode == InkMode.HIGHLIGHTER,
                                                isEraser = false
                                            )
                                            commitStrokeEdit(committedStrokes + newStroke)
                                        }
                                    }
                                }

                                currentPoints.clear()
                                activeStrokeRevision++
                            }
                        }
                    }
                }
        ) {
            // Read tick & input revisions to invalidate Canvas only during active stroke / fading laser
            val _t = laserFrameTick
            val _inp = laserInputVersion
            val _activeRev = activeStrokeRevision

            // 0. Draw cached paper template or theme-default surface ruling
            val resolvedTemplate = if (paperTemplate == "DEFAULT") {
                when (themeMode) {
                    AppThemeMode.KINDLE_PARCHMENT -> "CORNELL"
                    AppThemeMode.MATTE_STUDIO -> "DOTS"
                    AppThemeMode.DEEP_PURPLE_AMOLED -> "BLANK"
                }
            } else {
                paperTemplate
            }
            val rulerColor = if (isLuxury) {
                when (themeMode) {
                    AppThemeMode.KINDLE_PARCHMENT -> lux.dividerRule
                    AppThemeMode.MATTE_STUDIO -> lux.borderHairline
                    AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF1F1F1F)
                }
            } else {
                when (themeMode) {
                    AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentSepiaDivider
                    AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioGridBorder
                    AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF282038)
                }
            }
            val cueColor = if (isLuxury) {
                lux.accent.copy(alpha = 0.42f)
            } else {
                when (themeMode) {
                    AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentAccent.copy(alpha = 0.45f)
                    AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioAccent.copy(alpha = 0.45f)
                    AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledAccent.copy(alpha = 0.45f)
                }
            }

            val rulerKey = "$resolvedTemplate:${size.width.toInt()}x${size.height.toInt()}"
            if (cachedRulerKey != rulerKey) {
                cachedRulerKey = rulerKey
                cachedRulePath.rewind()
                cachedDotOffsets.clear()
                when (resolvedTemplate) {
                    "CORNELL", "LINED" -> {
                        var y = ruleStepPx
                        while (y < size.height) {
                            cachedRulePath.moveTo(0f, y)
                            cachedRulePath.lineTo(size.width, y)
                            y += ruleStepPx
                        }
                    }
                    "GRID" -> {
                        var gx = gridStepPx
                        while (gx < size.width) {
                            cachedRulePath.moveTo(gx, 0f)
                            cachedRulePath.lineTo(gx, size.height)
                            gx += gridStepPx
                        }
                        var gy = gridStepPx
                        while (gy < size.height) {
                            cachedRulePath.moveTo(0f, gy)
                            cachedRulePath.lineTo(size.width, gy)
                            gy += gridStepPx
                        }
                    }
                    "DOTS" -> {
                        var gx = gridStepPx
                        while (gx < size.width) {
                            var gy = gridStepPx
                            while (gy < size.height) {
                                cachedDotOffsets.add(Offset(gx, gy))
                                gy += gridStepPx
                            }
                            gx += gridStepPx
                        }
                    }
                }
            }

            when (resolvedTemplate) {
                "CORNELL" -> {
                    drawPath(path = cachedRulePath, color = rulerColor, style = Stroke(width = 1.5f))
                    drawLine(color = cueColor, start = Offset(cueXPx, 0f), end = Offset(cueXPx, size.height), strokeWidth = 2f)
                }
                "LINED" -> {
                    drawPath(path = cachedRulePath, color = rulerColor, style = Stroke(width = 1.4f))
                }
                "GRID" -> {
                    drawPath(path = cachedRulePath, color = rulerColor.copy(alpha = 0.55f), style = Stroke(width = 1f))
                }
                "DOTS" -> {
                    if (cachedDotOffsets.isNotEmpty()) {
                        drawPoints(
                            points = cachedDotOffsets,
                            pointMode = PointMode.Points,
                            color = rulerColor,
                            strokeWidth = 3.4f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                else -> Unit // BLANK: clean solid canvas
            }

            // 1. Draw committed strokes using pre-built Paths & LRU-cached colors
            val fallbackColor = if (themeMode == AppThemeMode.KINDLE_PARCHMENT) {
                NoteGraphColors.KindleParchmentCharcoalText
            } else {
                Color.White
            }
            for (idx in committedStrokes.indices) {
                val path = cachedCommittedPaths.getOrNull(idx) ?: continue
                val stroke = committedStrokes[idx]
                val isSelected = idx in selectedStrokeIndices
                val alpha = if (stroke.isHighlighter) 0.4f else 1f
                val strokeColor = if (isSelected) {
                    selectionAccent.copy(alpha = if (stroke.isHighlighter) 0.7f else 1f)
                } else {
                    val rawHex = stroke.colorHex
                    val adaptedHex = when {
                        themeMode == AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#FFFFFF", ignoreCase = true) -> "#2C2C2C"
                        themeMode != AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#2C2C2C", ignoreCase = true) -> "#FFFFFF"
                        else -> rawHex
                    }
                    NoteJson.parseColorCached(adaptedHex, fallbackColor).copy(alpha = alpha)
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = if (isSelected) stroke.strokeWidth + 2f else stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 1b. Draw unified dashed bounding box with corner handles for selected strokes
            val selectedBounds = if (selectedStrokeIndices.isNotEmpty()) {
                computeStrokeSetBounds(committedStrokes, selectedStrokeIndices)
            } else {
                null
            }
            if (selectedBounds != null) {
                val pad = 12f
                val left = selectedBounds.minX - pad
                val top = selectedBounds.minY - pad
                val boxWidth = (selectedBounds.width + pad * 2f).coerceAtLeast(16f)
                val boxHeight = (selectedBounds.height + pad * 2f).coerceAtLeast(16f)

                drawRect(
                    color = selectionAccent.copy(alpha = if (isDraggingSelection) 0.14f else 0.07f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )
                drawRect(
                    color = selectionAccent,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(
                        width = if (isDraggingSelection) 2.5f else 1.8f,
                        pathEffect = lassoDashEffect
                    )
                )

                val handleRadius = 4.5f
                drawCircle(color = selectionAccent, radius = handleRadius, center = Offset(left, top))
                drawCircle(color = selectionAccent, radius = handleRadius, center = Offset(left + boxWidth, top))
                drawCircle(color = selectionAccent, radius = handleRadius, center = Offset(left, top + boxHeight))
                drawCircle(color = selectionAccent, radius = handleRadius, center = Offset(left + boxWidth, top + boxHeight))
            }

            // 2. Draw active in-progress stroke (non-laser)
            if (_activeRev >= 0 && currentPoints.size >= 2 && mode != InkMode.VANISHING_PEN) {
                activeStrokePath.rewind()
                activeStrokePath.moveTo(currentPoints[0].x, currentPoints[0].y)
                for (i in 1 until currentPoints.size) {
                    activeStrokePath.lineTo(currentPoints[i].x, currentPoints[i].y)
                }

                if (mode == InkMode.LASSO) {
                    drawPath(
                        path = activeStrokePath,
                        color = selectionAccent,
                        style = Stroke(
                            width = 2f,
                            pathEffect = lassoDashEffect,
                            cap = StrokeCap.Round
                        )
                    )
                } else {
                    val alpha = if (mode == InkMode.HIGHLIGHTER) 0.4f else 1f
                    val color = NoteJson.parseColorCached(
                        activeInkColorHex[selectedColorIndex],
                        activeInkColors[selectedColorIndex]
                    ).copy(alpha = alpha)
                    drawPath(
                        path = activeStrokePath,
                        color = color,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 3. Draw batched Rainbow Vanishing Laser trails (active + fading)
            if (laserController.hasAnyContent) {
                val nowMs = if (_t > 0L) maxOf(_t, System.currentTimeMillis()) else System.currentTimeMillis()
                laserController.drawInto(
                    scope = this,
                    nowMs = nowMs,
                    baseStrokeWidthPx = strokeWidth + 2f
                )
            }
        }
        if (isLuxury && !isFullscreen) {
            HorizontalDivider(color = lux.dividerRule, thickness = 1.dp)
        }
    }
}

@Composable
private fun LuxuryInkPillTab(
    label: String,
    selected: Boolean,
    accent: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, accent, shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else muted
        )
    }
}
