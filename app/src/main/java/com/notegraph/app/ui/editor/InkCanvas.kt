package com.notegraph.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint

/**
 * Drawing mode for the ink canvas.
 */
enum class InkMode { PEN, HIGHLIGHTER, ERASER }

/**
 * Preset colors available in the ink canvas toolbar.
 */
val InkPresetColors = listOf(
    Color.White,
    Color.Red,
    Color(0xFF4FC3F7),   // light blue
    Color(0xFF81C784),   // green
    Color(0xFFFFD54F)    // yellow
)

private val InkPresetColorHex = listOf(
    "#FFFFFF",
    "#FF0000",
    "#4FC3F7",
    "#81C784",
    "#FFD54F"
)

/**
 * A composable ink drawing canvas supporting pen, highlighter and eraser
 * with undo/redo, 5 preset colours and adjustable stroke thickness.
 *
 * @param strokes   current list of strokes persisted in the parent.
 * @param heightDp  canvas height in dp.
 * @param onStrokesChanged callback whenever the committed stroke list changes.
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

    // Undo / redo stacks – keep committed strokes + redo buffer
    var committedStrokes by remember(strokes) { mutableStateOf(strokes) }
    var redoStack by remember { mutableStateOf(listOf<InkStroke>()) }

    // The stroke currently being drawn
    var currentPoints by remember { mutableStateOf(listOf<StrokePoint>()) }

    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        // --------------- toolbar ---------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mode buttons
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

            Spacer(Modifier.width(4.dp))

            // Colour dots
            InkPresetColors.forEachIndexed { idx, color ->
                IconButton(
                    onClick = { selectedColorIndex = idx },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (idx == selectedColorIndex) 22.dp else 16.dp)
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

        // Stroke width slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Width", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = strokeWidth,
                onValueChange = { strokeWidth = it },
                valueRange = 1f..24f,
                modifier = Modifier.weight(1f)
            )
            // Undo
            IconButton(onClick = {
                if (committedStrokes.isNotEmpty()) {
                    redoStack = redoStack + committedStrokes.last()
                    committedStrokes = committedStrokes.dropLast(1)
                    onStrokesChanged(committedStrokes)
                }
            }) {
                Icon(Icons.Default.Edit, contentDescription = "Undo")
            }
            // Redo
            IconButton(onClick = {
                if (redoStack.isNotEmpty()) {
                    committedStrokes = committedStrokes + redoStack.last()
                    redoStack = redoStack.dropLast(1)
                    onStrokesChanged(committedStrokes)
                }
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Redo")
            }
        }

        // --------------- canvas ---------------
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .pointerInput(mode, selectedColorIndex, strokeWidth) {
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
                                    // Pointer lifted – commit stroke
                                    if (mode == InkMode.ERASER) {
                                        // Simple eraser: remove strokes that have any point
                                        // within 20px of any eraser point
                                        val eraserPts = currentPoints
                                        val threshold = 20f * 20f
                                        committedStrokes = committedStrokes.filter { stroke ->
                                            stroke.points.none { sp ->
                                                eraserPts.any { ep ->
                                                    val dx = sp.x - ep.x
                                                    val dy = sp.y - ep.y
                                                    dx * dx + dy * dy < threshold
                                                }
                                            }
                                        }
                                    } else {
                                        val newStroke = InkStroke(
                                            points = currentPoints,
                                            colorHex = InkPresetColorHex[selectedColorIndex],
                                            strokeWidth = strokeWidth,
                                            isHighlighter = mode == InkMode.HIGHLIGHTER,
                                            isEraser = false
                                        )
                                        committedStrokes = committedStrokes + newStroke
                                    }
                                    redoStack = emptyList()
                                    currentPoints = emptyList()
                                    onStrokesChanged(committedStrokes)
                                }
                            }
                        }
                    }
                }
        ) {
            // Draw committed strokes
            fun drawStrokeList(list: List<InkStroke>) {
                for (stroke in list) {
                    if (stroke.points.size < 2) continue
                    val path = Path().apply {
                        moveTo(stroke.points[0].x, stroke.points[0].y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                    val alpha = if (stroke.isHighlighter) 0.4f else 1f
                    val color = try {
                        Color(android.graphics.Color.parseColor(stroke.colorHex)).copy(alpha = alpha)
                    } catch (_: Exception) {
                        Color.White.copy(alpha = alpha)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = stroke.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            drawStrokeList(committedStrokes)

            // Draw current in-progress stroke
            if (currentPoints.size >= 2 && mode != InkMode.ERASER) {
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
}
