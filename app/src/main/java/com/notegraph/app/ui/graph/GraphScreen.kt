package com.notegraph.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphNode(
    val noteId: String,
    val title: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit = {},
    viewModel: GraphViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val links by viewModel.links.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(100f, 200f)) }

    // Initialize node positions in a circular layout
    val nodes = remember(notes) {
        val count = notes.size.coerceAtLeast(1)
        notes.mapIndexed { index, note ->
            val angle = (2.0 * Math.PI * index / count).toFloat()
            val dist = 240f + (index % 3) * 70f
            GraphNode(
                noteId = note.id,
                title = note.title.ifBlank { "Untitled" },
                x = 450f + dist * cos(angle),
                y = 450f + dist * sin(angle)
            )
        }.toMutableList()
    }

    // Map edges between notes
    val edges = remember(links, nodes) {
        val nodeById = nodes.associateBy { it.noteId }
        val nodeByTitle = nodes.associateBy { it.title }
        links.mapNotNull { link ->
            val src = nodeById[link.sourceNoteId]
            val dst = nodeById[link.targetNoteId] ?: nodeByTitle[link.targetNoteTitle]
            if (src != null && dst != null && src.noteId != dst.noteId) {
                src to dst
            } else {
                null
            }
        }
    }

    // Force simulation step counter to trigger canvas recomposition
    var simTick by remember { mutableIntStateOf(0) }

    // Run force-directed simulation
    LaunchedEffect(nodes, edges) {
        if (nodes.size < 2) return@LaunchedEffect
        repeat(90) {
            // Repulsion between all node pairs
            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    val a = nodes[i]
                    val b = nodes[j]
                    val dx = a.x - b.x
                    val dy = a.y - b.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(10f)
                    val force = 6500f / (dist * dist)
                    val fx = force * (dx / dist)
                    val fy = force * (dy / dist)
                    a.vx += fx
                    a.vy += fy
                    b.vx -= fx
                    b.vy -= fy
                }
            }

            // Attraction along wiki-link edges
            for ((src, dst) in edges) {
                val dx = dst.x - src.x
                val dy = dst.y - src.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val force = (dist - 160f) * 0.05f
                val fx = force * (dx / dist)
                val fy = force * (dy / dist)
                src.vx += fx
                src.vy += fy
                dst.vx -= fx
                dst.vy -= fy
            }

            // Gravity towards center (450, 450)
            for (node in nodes) {
                node.vx += (450f - node.x) * 0.006f
                node.vy += (450f - node.y) * 0.006f
            }

            // Apply velocity with damping
            for (node in nodes) {
                node.vx *= 0.82f
                node.vy *= 0.82f
                node.x += node.vx
                node.y += node.vy
            }

            simTick++
            delay(16L)
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Graph (${notes.size} notes)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(nodes, scale, offset) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            var hasMoved = false

                            do {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.isEmpty()) {
                                    // Finger lifted without significant drag -> TAP!
                                    if (!hasMoved) {
                                        var closestNode: GraphNode? = null
                                        var minDistanceSq = Float.MAX_VALUE

                                        for (node in nodes) {
                                            // Exact screen coordinates of the node
                                            val nodeScreenX = node.x * scale + offset.x
                                            val nodeScreenY = node.y * scale + offset.y
                                            val dx = downPos.x - nodeScreenX
                                            val dy = downPos.y - nodeScreenY
                                            val distSq = dx * dx + dy * dy
                                            if (distSq < minDistanceSq) {
                                                minDistanceSq = distSq
                                                closestNode = node
                                            }
                                        }

                                        // Comfortable touch radius on screen (at least 90px)
                                        val hitRadius = maxOf(48f * scale, 90f)
                                        if (closestNode != null && minDistanceSq <= hitRadius * hitRadius) {
                                            onNoteClick(closestNode.noteId)
                                        }
                                    }
                                    break
                                }

                                if (event.changes.size > 1) {
                                    // Pinch zoom & two-finger pan
                                    hasMoved = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(0.25f, 4f)
                                    offset += pan
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // Single finger pan
                                    val curPos = activePointers.first().position
                                    val diff = curPos - downPos
                                    if (diff.getDistance() > 10f) {
                                        hasMoved = true
                                        val pan = curPos - activePointers.first().previousPosition
                                        offset += pan
                                        activePointers.first().consume()
                                    }
                                }
                            } while (true)
                        }
                    }
            ) {
                // Read simTick to ensure canvas updates during physics iterations
                val _tick = simTick

                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, Offset.Zero)
                }) {
                    // Draw edge connections
                    for ((src, dst) in edges) {
                        drawLine(
                            color = Color(0xFF757575),
                            start = Offset(src.x, src.y),
                            end = Offset(dst.x, dst.y),
                            strokeWidth = 2f
                        )
                    }

                    // Draw nodes
                    for (node in nodes) {
                        // Outer halo
                        drawCircle(
                            color = Color(0x3342A5F5),
                            radius = 32f,
                            center = Offset(node.x, node.y)
                        )
                        // Inner circle
                        drawCircle(
                            color = Color(0xFF42A5F5),
                            radius = 22f,
                            center = Offset(node.x, node.y)
                        )
                        // Node title label
                        val label = if (node.title.length > 14) node.title.take(13) + "…" else node.title
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(node.x - 36f, node.y + 26f),
                            style = TextStyle(color = Color.White, fontSize = 12.sp)
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notes created yet.\nCreate notes with [[wiki-links]] to populate the graph.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // Quick-access note selector bar at the bottom
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Tap node above or select note below to open:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(notes) { note ->
                                InputChip(
                                    selected = false,
                                    onClick = { onNoteClick(note.id) },
                                    label = { Text(note.title.ifBlank { "Untitled" }) },
                                    leadingIcon = {
                                        Text("📄", fontSize = 12.sp)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
