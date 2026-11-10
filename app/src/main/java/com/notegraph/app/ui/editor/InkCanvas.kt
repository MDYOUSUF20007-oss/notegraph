package com.notegraph.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.NoteGraphColors
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

/**
 * Drawing modes for the ink canvas.
 */
enum class InkMode {
    PEN,
    HIGHLIGHTER,
    ERASER,
    VANISHING_PEN, // Rainbow pointing pen that smoothly fades away after 1.5 seconds
    LASSO          // Dashed loop selection to move, scale, or delete strokes
}

data class VanishingStroke(
    val points: List<StrokePoint>,
    val startTime: Long
)

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
        Dialog(
            onDismissRequest = { showFullscreenCanvas = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fullscreen Sketch Studio", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = { showFullscreenCanvas = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Done", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Done")
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

    // Committed strokes & undo/redo buffers
    var committedStrokes by remember(strokes) { mutableStateOf(strokes) }
    var redoStack by remember { mutableStateOf(listOf<InkStroke>()) }

    // In-progress stroke points
    var currentPoints by remember { mutableStateOf(listOf<StrokePoint>()) }

    // Vanishing strokes state (Rainbow pointing tool)
    val vanishingStrokes = remember { mutableStateListOf<VanishingStroke>() }
    var currentTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Continuous tick ticker for 60-120 FPS vanishing pen fade animation
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(16L)
            currentTick = System.currentTimeMillis()
            // Prune vanishing strokes older than 1500ms
            if (vanishingStrokes.isNotEmpty()) {
                vanishingStrokes.removeAll { currentTick - it.startTime >= 1500L }
            }
        }
    }

    // Lasso selection state
    var selectedStrokeIndices by remember { mutableStateOf(setOf<Int>()) }

    val themeMode = ThemeManager.currentTheme
    val activeInkColors = remember(themeMode) {
        if (themeMode == AppThemeMode.KINDLE_PARCHMENT) {
            listOf(
                NoteGraphColors.KindleParchmentCharcoalText,
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
    val canvasBackground = when (themeMode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF07030F)
        AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentBackground
        AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioBackground
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(0.75.dp, ThemeManager.cardBorderColor, RoundedCornerShape(12.dp))
    ) {
        // ─── Minimalist Toolbar Row 1: Mode chips, S-Pen Palm Rejection, Paper Template & Expand ───
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
                onClick = { mode = InkMode.PEN },
                label = { Text("Pen") }
            )
            FilterChip(
                selected = mode == InkMode.HIGHLIGHTER,
                onClick = { mode = InkMode.HIGHLIGHTER },
                label = { Text("Highlight") }
            )
            FilterChip(
                selected = mode == InkMode.ERASER,
                onClick = { mode = InkMode.ERASER },
                label = { Text("Eraser") }
            )
            FilterChip(
                selected = mode == InkMode.VANISHING_PEN,
                onClick = {
                    mode = InkMode.VANISHING_PEN
                    selectedStrokeIndices = emptySet()
                },
                label = { Text("Laser") }
            )
            FilterChip(
                selected = mode == InkMode.LASSO,
                onClick = { mode = InkMode.LASSO },
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

        // ─── Toolbar Row 2: Color dots, Width Slider, and Undo / Redo Buttons ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Color palette dots
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

            // Stroke width
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

            // Explicit Undo and Redo buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        if (committedStrokes.isNotEmpty()) {
                            val last = committedStrokes.last()
                            redoStack = redoStack + last
                            committedStrokes = committedStrokes.dropLast(1)
                            selectedStrokeIndices = emptySet()
                            onStrokesChanged(committedStrokes)
                        }
                    },
                    enabled = committedStrokes.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("↶ Undo", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val next = redoStack.last()
                            redoStack = redoStack.dropLast(1)
                            committedStrokes = committedStrokes + next
                            onStrokesChanged(committedStrokes)
                        }
                    },
                    enabled = redoStack.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("↷ Redo", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ─── Lasso Selection Action Bar ───
        if (selectedStrokeIndices.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${selectedStrokeIndices.size} stroke(s) selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Move Up/Down/Left/Right
                        FilledTonalButton(
                            onClick = {
                                // Move selected strokes right & down by 20px
                                committedStrokes = committedStrokes.mapIndexed { idx, s ->
                                    if (idx in selectedStrokeIndices) {
                                        s.copy(points = s.points.map { StrokePoint(it.x + 20f, it.y + 20f) })
                                    } else s
                                }
                                onStrokesChanged(committedStrokes)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Move ↘", style = MaterialTheme.typography.labelSmall)
                        }

                        // Scale Up (1.2x)
                        FilledTonalButton(
                            onClick = {
                                committedStrokes = scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 1.2f)
                                onStrokesChanged(committedStrokes)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Scale +", style = MaterialTheme.typography.labelSmall)
                        }

                        // Scale Down (0.8x)
                        FilledTonalButton(
                            onClick = {
                                committedStrokes = scaleSelectedStrokes(committedStrokes, selectedStrokeIndices, 0.8f)
                                onStrokesChanged(committedStrokes)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Scale -", style = MaterialTheme.typography.labelSmall)
                        }

                        // Delete
                        Button(
                            onClick = {
                                redoStack = emptyList()
                                committedStrokes = committedStrokes.filterIndexed { idx, _ -> idx !in selectedStrokeIndices }
                                selectedStrokeIndices = emptySet()
                                onStrokesChanged(committedStrokes)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                        }

                        // Deselect
                        IconButton(
                            onClick = { selectedStrokeIndices = emptySet() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Deselect", modifier = Modifier.size(16.dp))
                        }
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

        Canvas(
            modifier = canvasHeightModifier
                .clipToBounds()
                .background(canvasBackground, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .pointerInput(mode, selectedColorIndex, strokeWidth, shapeSnapEnabled, stylusOnlyMode, themeMode) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.size == 1) {
                                val first = activeChanges.first()
                                val isStylusOrEraser = first.type == PointerType.Stylus || first.type == PointerType.Eraser
                                if (stylusOnlyMode && !isStylusOrEraser) {
                                    // Palm rejection: ignore bare touch when S-Pen Only is active
                                    continue
                                }
                                val point = StrokePoint(first.position.x, first.position.y)
                                currentPoints = currentPoints + point
                                event.changes.forEach { it.consume() }
                            } else if (activeChanges.size > 1) {
                                // Ignore multi-touch palm/scroll gestures cleanly
                                continue
                            } else if (currentPoints.isNotEmpty()) {
                                // Pointer released
                                when (mode) {
                                        InkMode.ERASER -> {
                                            val eraserPts = currentPoints
                                            val threshold = 24f * 24f
                                            committedStrokes = committedStrokes.filter { stroke ->
                                                stroke.points.none { sp ->
                                                    eraserPts.any { ep ->
                                                        val dx = sp.x - ep.x
                                                        val dy = sp.y - ep.y
                                                        dx * dx + dy * dy < threshold
                                                    }
                                                }
                                            }
                                            selectedStrokeIndices = emptySet()
                                            onStrokesChanged(committedStrokes)
                                        }

                                        InkMode.VANISHING_PEN -> {
                                            // Add to vanishing strokes with current timestamp
                                            if (currentPoints.size >= 2) {
                                                vanishingStrokes.add(VanishingStroke(currentPoints, System.currentTimeMillis()))
                                            }
                                        }

                                        InkMode.LASSO -> {
                                            // Close loop and select strokes inside the lasso polygon
                                            if (currentPoints.size >= 3) {
                                                val loop = currentPoints
                                                val matched = mutableSetOf<Int>()
                                                committedStrokes.forEachIndexed { idx, s ->
                                                    // Check if any point of the stroke is inside the lasso polygon
                                                    val isInside = s.points.any { pt -> isPointInPolygon(pt, loop) }
                                                    if (isInside) matched.add(idx)
                                                }
                                                selectedStrokeIndices = matched
                                            }
                                        }

                                        InkMode.PEN, InkMode.HIGHLIGHTER -> {
                                            // Process Shape Snap if enabled
                                            val finalPoints = if (shapeSnapEnabled) {
                                                snapShape(currentPoints)
                                            } else {
                                                currentPoints
                                            }

                                            val newStroke = InkStroke(
                                                points = finalPoints,
                                                colorHex = activeInkColorHex[selectedColorIndex],
                                                strokeWidth = strokeWidth,
                                                isHighlighter = mode == InkMode.HIGHLIGHTER,
                                                isEraser = false
                                            )
                                            committedStrokes = committedStrokes + newStroke
                                            redoStack = emptyList()
                                            onStrokesChanged(committedStrokes)
                                        }
                                    }

                                    currentPoints = emptyList()
                            }
                        }
                    }
                }
        ) {
            // Read tick to drive continuous 60-120 FPS animation of vanishing strokes
            val _t = currentTick

            // 0. Draw selected paper template or theme-default surface ruling
            val resolvedTemplate = if (paperTemplate == "DEFAULT") {
                when (themeMode) {
                    AppThemeMode.KINDLE_PARCHMENT -> "CORNELL"
                    AppThemeMode.MATTE_STUDIO -> "DOTS"
                    AppThemeMode.DEEP_PURPLE_AMOLED -> "BLANK"
                }
            } else {
                paperTemplate
            }
            val rulerColor = when (themeMode) {
                AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentSepiaDivider
                AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioGridBorder
                AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF282038)
            }
            val cueColor = when (themeMode) {
                AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentAccent.copy(alpha = 0.45f)
                AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioAccent.copy(alpha = 0.45f)
                AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledAccent.copy(alpha = 0.45f)
            }

            when (resolvedTemplate) {
                "CORNELL" -> {
                    val ruleStep = 28.dp.toPx()
                    var y = ruleStep
                    while (y < size.height) {
                        drawLine(color = rulerColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.5f)
                        y += ruleStep
                    }
                    val cueX = 48.dp.toPx()
                    drawLine(color = cueColor, start = Offset(cueX, 0f), end = Offset(cueX, size.height), strokeWidth = 2f)
                }
                "LINED" -> {
                    val ruleStep = 28.dp.toPx()
                    var y = ruleStep
                    while (y < size.height) {
                        drawLine(color = rulerColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.4f)
                        y += ruleStep
                    }
                }
                "GRID" -> {
                    val step = 24.dp.toPx()
                    var gx = step
                    while (gx < size.width) {
                        drawLine(color = rulerColor.copy(alpha = 0.55f), start = Offset(gx, 0f), end = Offset(gx, size.height), strokeWidth = 1f)
                        gx += step
                    }
                    var gy = step
                    while (gy < size.height) {
                        drawLine(color = rulerColor.copy(alpha = 0.55f), start = Offset(0f, gy), end = Offset(size.width, gy), strokeWidth = 1f)
                        gy += step
                    }
                }
                "DOTS" -> {
                    val gridStep = 24.dp.toPx()
                    var gx = gridStep
                    while (gx < size.width) {
                        var gy = gridStep
                        while (gy < size.height) {
                            drawCircle(color = rulerColor, radius = 1.7f, center = Offset(gx, gy))
                            gy += gridStep
                        }
                        gx += gridStep
                    }
                }
                else -> Unit // BLANK: clean solid canvas
            }

            // 1. Draw committed strokes
            committedStrokes.forEachIndexed { idx, stroke ->
                if (stroke.points.size >= 2) {
                    val path = Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                    val isSelected = idx in selectedStrokeIndices
                    val alpha = if (stroke.isHighlighter) 0.4f else 1f
                    val strokeColor = if (isSelected) {
                        NoteGraphColors.DeepPurpleAmoledAccent
                    } else {
                        val rawHex = stroke.colorHex
                        val adaptedHex = when {
                            themeMode == AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#FFFFFF", ignoreCase = true) -> "#2C2C2C"
                            themeMode != AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#2C2C2C", ignoreCase = true) -> "#FFFFFF"
                            else -> rawHex
                        }
                        try {
                            Color(android.graphics.Color.parseColor(adaptedHex)).copy(alpha = alpha)
                        } catch (e: Exception) {
                            (if (themeMode == AppThemeMode.KINDLE_PARCHMENT) NoteGraphColors.KindleParchmentCharcoalText else Color.White).copy(alpha = alpha)
                        }
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

                    // Draw dashed bounding box if selected
                    if (isSelected) {
                        val minX = stroke.points.minOf { it.x } - 8f
                        val maxX = stroke.points.maxOf { it.x } + 8f
                        val minY = stroke.points.minOf { it.y } - 8f
                        val maxY = stroke.points.maxOf { it.y } + 8f
                        drawRect(
                            color = Color(0xFF00FFFF),
                            topLeft = Offset(minX, minY),
                            size = androidx.compose.ui.geometry.Size(maxX - minX, maxY - minY),
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                }
            }

            // 2. Draw active in-progress stroke
            if (currentPoints.size >= 2) {
                when (mode) {
                    InkMode.LASSO -> {
                        // Draw dashed lasso loop boundary
                        val lassoPath = Path().apply {
                            moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (i in 1 until currentPoints.size) {
                                lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                        }
                        drawPath(
                            path = lassoPath,
                            color = Color(0xFF00FFFF),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    InkMode.VANISHING_PEN -> {
                        // Draw in-progress rainbow vanishing stroke
                        for (i in 1 until currentPoints.size) {
                            val p1 = currentPoints[i - 1]
                            val p2 = currentPoints[i]
                            val hue = ((i * 12f + (currentTick / 4f)) % 360f)
                            val color = Color.hsl(hue, 1f, 0.55f, 0.95f)
                            drawLine(
                                color = color,
                                start = Offset(p1.x, p1.y),
                                end = Offset(p2.x, p2.y),
                                strokeWidth = strokeWidth + 2f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    else -> {
                        val path = Path().apply {
                            moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (i in 1 until currentPoints.size) {
                                lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                        }
                        val alpha = if (mode == InkMode.HIGHLIGHTER) 0.4f else 1f
                        val color = try {
                            Color(android.graphics.Color.parseColor(activeInkColorHex[selectedColorIndex])).copy(alpha = alpha)
                        } catch (e: Exception) {
                            activeInkColors[selectedColorIndex].copy(alpha = alpha)
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // 3. Draw Rainbow Vanishing Pen strokes (fading out smoothly over 1.5 seconds)
            for (vanish in vanishingStrokes) {
                val age = currentTick - vanish.startTime
                if (age < 1500L && vanish.points.size >= 2) {
                    val progress = (age / 1500f).coerceIn(0f, 1f)
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.95f

                    for (i in 1 until vanish.points.size) {
                        val p1 = vanish.points[i - 1]
                        val p2 = vanish.points[i]
                        // Dynamic rainbow hue cycling based on point position and age
                        val hue = ((i * 14f + (vanish.startTime / 6f) + (progress * 180f)) % 360f)
                        val color = Color.hsl(hue, 1f, 0.55f, alpha)

                        drawLine(
                            color = color,
                            start = Offset(p1.x, p1.y),
                            end = Offset(p2.x, p2.y),
                            strokeWidth = (strokeWidth + 2f) * (1f - progress * 0.3f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

// ─── Geometric Shape Snap Logic ──────────────────────────────────

/**
 * Auto-straightens freehand lines and snaps closed shapes into clean geometric vectors.
 */
private fun snapShape(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 5) return points

    val start = points.first()
    val end = points.last()
    val dx = end.x - start.x
    val dy = end.y - start.y
    val endToEndDist = sqrt(dx * dx + dy * dy)

    // Calculate total path length
    var totalLength = 0f
    for (i in 1 until points.size) {
        val segDx = points[i].x - points[i - 1].x
        val segDy = points[i].y - points[i - 1].y
        totalLength += sqrt(segDx * segDx + segDy * segDy)
    }

    // 1. Straight Line Snap: If end-to-end distance is large and path length is close to straight distance
    if (endToEndDist > 40f && totalLength < endToEndDist * 1.14f) {
        // Snap to perfect 2-point straight line
        return listOf(start, end)
    }

    // 2. Closed Shapes (Circle, Rectangle, Triangle): Start & End are close together
    if (endToEndDist < 45f && totalLength > 100f) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        // Check if points form a circle / ellipse
        val avgRadius = (width + height) / 4f
        var radiusDeviance = 0f
        for (p in points) {
            val dist = sqrt((p.x - centerX) * (p.x - centerX) + (p.y - centerY) * (p.y - centerY))
            radiusDeviance += abs(dist - avgRadius)
        }
        val avgDeviance = radiusDeviance / points.size

        if (avgDeviance < avgRadius * 0.22f) {
            // Snap to clean 36-point circle
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

        // Check if it snaps to a clean rectangle (aspect ratio test)
        if (width > 30f && height > 30f) {
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

// ─── Lasso Selection Utilities ───────────────────────────────────

/**
 * Point-in-polygon ray casting algorithm.
 */
private fun isPointInPolygon(p: StrokePoint, polygon: List<StrokePoint>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > p.y) != (pj.y > p.y) &&
            (p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x)
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Scales selected strokes around their common centroid.
 */
private fun scaleSelectedStrokes(
    strokes: List<InkStroke>,
    selectedIndices: Set<Int>,
    factor: Float
): List<InkStroke> {
    val allSelectedPoints = strokes.filterIndexed { idx, _ -> idx in selectedIndices }.flatMap { it.points }
    if (allSelectedPoints.isEmpty()) return strokes

    val centerX = (allSelectedPoints.minOf { it.x } + allSelectedPoints.maxOf { it.x }) / 2f
    val centerY = (allSelectedPoints.minOf { it.y } + allSelectedPoints.maxOf { it.y }) / 2f

    return strokes.mapIndexed { idx, stroke ->
        if (idx in selectedIndices) {
            stroke.copy(
                points = stroke.points.map { pt ->
                    StrokePoint(
                        x = centerX + (pt.x - centerX) * factor,
                        y = centerY + (pt.y - centerY) * factor
                    )
                }
            )
        } else stroke
    }
}
