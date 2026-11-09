package com.notegraph.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.25f, 3.5f)
        offset += panChange
    }

    // Initialize node positions in a circular layout
    val nodes = remember(notes) {
        val count = notes.size.coerceAtLeast(1)
        notes.mapIndexed { index, note ->
            val angle = (2.0 * Math.PI * index / count).toFloat()
            val dist = 240f + (index % 3) * 70f
            GraphNode(
                noteId = note.id,
                title = note.title.ifBlank { "Untitled" },
                x = 500f + dist * cos(angle),
                y = 500f + dist * sin(angle)
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

            // Gravity towards center (500, 500)
            for (node in nodes) {
                node.vx += (500f - node.x) * 0.006f
                node.vy += (500f - node.y) * 0.006f
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
                title = { Text("Knowledge Graph (${notes.size} nodes)") },
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
                    .transformable(state = transformState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(nodes, scale, offset) {
                        detectTapGestures { tapOffset ->
                            val worldX = (tapOffset.x - offset.x) / scale
                            val worldY = (tapOffset.y - offset.y) / scale
                            for (node in nodes) {
                                val dx = node.x - worldX
                                val dy = node.y - worldY
                                if (dx * dx + dy * dy <= 28f * 28f) {
                                    onNoteClick(node.noteId)
                                    break
                                }
                            }
                        }
                    }
            ) {
                // Read simTick to ensure canvas updates during physics iterations
                val _tick = simTick

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
                    // Node outer ring / halo
                    drawCircle(
                        color = Color(0x3342A5F5),
                        radius = 26f,
                        center = Offset(node.x, node.y)
                    )
                    // Node center circle
                    drawCircle(
                        color = Color(0xFF42A5F5),
                        radius = 18f,
                        center = Offset(node.x, node.y)
                    )
                    // Node label
                    val label = if (node.title.length > 14) node.title.take(13) + "…" else node.title
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(node.x - 36f, node.y + 22f),
                        style = TextStyle(color = Color.White, fontSize = 11.sp)
                    )
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
            }
        }
    }
}
