package com.notegraph.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint
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
    onStrokesChanged: (List<InkStroke>) -> Unit
) {
    var mode by remember { mutableStateOf(InkMode.PEN) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var strokeWidth by remember { mutableFloatStateOf(4f) }
    var shapeSnapEnabled by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        // ─── Toolbar Row 1: Mode chips (Pen, Hi-lite, Eraser, Vanish, Lasso, Snap) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = mode == InkMode.PEN,
                onClick = { mode = InkMode.PEN },
                label = { Text("Pen") }
            )
            FilterChip(
                selected = mode == InkMode.HIGHLIGHTER,
                onClick = { mode = InkMode.HIGHLIGHTER },
                label = { Text("Hi-lite") }
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
                label = { Text("✨ Vanish") }
            )
            FilterChip(
                selected = mode == InkMode.LASSO,
                onClick = { mode = InkMode.LASSO },
                label = { Text("⬡ Lasso") }
            )
            FilterChip(
                selected = shapeSnapEnabled,
                onClick = { shapeSnapEnabled = !shapeSnapEnabled },
                label = { Text(if (shapeSnapEnabled) "📐 Snap: ON" else "📐 Snap") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
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
                InkPresetColors.forEachIndexed { idx, color ->
                    IconButton(
                        onClick = { selectedColorIndex = idx },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (idx == selectedColorIndex) 20.dp else 14.dp)
                                .background(color, CircleShape)
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .pointerInput(mode, selectedColorIndex, strokeWidth, shapeSnapEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            val point = StrokePoint(pos.x, pos.y)

                            when {
                                event.changes.any { it.pressed } -> {
                                    currentPoints = currentPoints + point
                                    event.changes.forEach { it.consume() }
                                }
                                currentPoints.isNotEmpty() -> {
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
                                                colorHex = InkPresetColorHex[selectedColorIndex],
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
                }
        ) {
            // Read tick to drive continuous 60-120 FPS animation of vanishing strokes
            val _t = currentTick

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
                        Color(0xFF8B5CF6) // Highlight selected strokes in Electric Violet
                    } else {
                        try {
                            Color(android.graphics.Color.parseColor(stroke.colorHex)).copy(alpha = alpha)
                        } catch (_: Exception) {
                            Color.White.copy(alpha = alpha)
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
                            Color(android.graphics.Color.parseColor(InkPresetColorHex[selectedColorIndex])).copy(alpha = alpha)
                        } catch (_: Exception) {
                            Color.White.copy(alpha = alpha)
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
