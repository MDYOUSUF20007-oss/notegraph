package com.notegraph.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
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

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val viewWidth = constraints.maxWidth.toFloat()
            val viewHeight = constraints.maxHeight.toFloat()
            val defaultCenterX = if (viewWidth > 0f) viewWidth / 2f else 500f
            val defaultCenterY = if (viewHeight > 0f) viewHeight / 2f else 600f

            // Initialize node positions in a circular layout centered on screen
            val nodes = remember(notes) {
                val count = notes.size.coerceAtLeast(1)
                notes.mapIndexed { index, note ->
                    val angle = (2.0 * Math.PI * index / count).toFloat()
                    val dist = 220f + (index % 3) * 60f
                    GraphNode(
                        noteId = note.id,
                        title = note.title.ifBlank { "Untitled" },
                        x = defaultCenterX + dist * cos(angle),
                        y = defaultCenterY + dist * sin(angle)
                    )
                }.toMutableList()
            }

            // Map edges between notes supporting multi-node networks, [[wiki-links]], and direct title mentions
            val edges = remember(links, nodes, notes) {
                val nodeById = nodes.associateBy { it.noteId }
                val nodeByTitle = nodes.associateBy { it.title.trim().lowercase() }
                val edgeSet = mutableSetOf<Pair<String, String>>()
                val result = mutableListOf<Pair<GraphNode, GraphNode>>()

                fun addEdge(a: GraphNode?, b: GraphNode?) {
                    if (a == null || b == null || a.noteId == b.noteId) return
                    val key = if (a.noteId < b.noteId) a.noteId to b.noteId else b.noteId to a.noteId
                    if (edgeSet.add(key)) {
                        result.add(a to b)
                    }
                }

                // 1. Edges from Room LinkEntity records
                for (link in links) {
                    val src = nodeById[link.sourceNoteId]
                    val dst = (if (link.targetNoteId != null) nodeById[link.targetNoteId] else null)
                        ?: nodeByTitle[link.targetNoteTitle.trim().lowercase()]
                    addEdge(src, dst)
                }

                // 2. Direct title / [[wiki-link]] mentions across all note contents and blocks
                for (note in notes) {
                    val src = nodeById[note.id] ?: continue
                    val fullText = note.content + " " + note.blocksJson

                    for (other in notes) {
                        if (other.id == note.id) continue
                        val otherTitle = other.title.trim()
                        if (otherTitle.isBlank() ||
                            otherTitle.equals("Untitled", ignoreCase = true) ||
                            otherTitle.equals("Untitled Note", ignoreCase = true) ||
                            otherTitle.length < 2
                        ) continue

                        val isMentioned = fullText.contains("[[$otherTitle]]", ignoreCase = true) ||
                                Regex("""(?i)\b${Regex.escape(otherTitle)}\b""").containsMatchIn(fullText)

                        if (isMentioned) {
                            val dst = nodeById[other.id]
                            addEdge(src, dst)
                        }
                    }
                }

                result
            }

            // Force simulation step counter to trigger canvas recomposition
            var simTick by remember { mutableIntStateOf(0) }

            // Run force-directed simulation
            LaunchedEffect(nodes, edges, defaultCenterX, defaultCenterY) {
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

                    // Attraction along connecting edges
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

                    // Gravity towards screen center
                    for (node in nodes) {
                        node.vx += (defaultCenterX - node.x) * 0.005f
                        node.vy += (defaultCenterY - node.y) * 0.005f
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

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(nodes) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            val downTime = System.currentTimeMillis()
                            var isTransforming = false

                            do {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.isEmpty()) {
                                    // Finger lifted: check if it was a quick tap
                                    val duration = System.currentTimeMillis() - downTime
                                    if (!isTransforming && duration < 350) {
                                        var closestNode: GraphNode? = null
                                        var minDistanceSq = Float.MAX_VALUE

                                        for (node in nodes) {
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

                                        // Generous hit radius for easy finger tap
                                        val hitRadius = maxOf(44f * scale, 95f)
                                        if (closestNode != null && minDistanceSq <= hitRadius * hitRadius) {
                                            onNoteClick(closestNode.noteId)
                                        }
                                    }
                                    break
                                }

                                if (activePointers.size > 1) {
                                    // Multi-touch pinch zoom & pan
                                    isTransforming = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()

                                    // Compute centroid of active touch pointers
                                    var sumX = 0f
                                    var sumY = 0f
                                    for (p in activePointers) {
                                        sumX += p.position.x
                                        sumY += p.position.y
                                    }
                                    val centroid = Offset(sumX / activePointers.size, sumY / activePointers.size)

                                    val newScale = (scale * zoom).coerceIn(0.2f, 5.0f)
                                    val scaleFactor = newScale / scale
                                    offset = (offset - centroid) * scaleFactor + centroid + pan
                                    scale = newScale
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // Single-finger movement
                                    val pointer = activePointers.first()
                                    val moveDist = (pointer.position - downPos).getDistance()
                                    if (moveDist > 12f) {
                                        isTransforming = true
                                    }
                                    if (isTransforming) {
                                        val pan = pointer.position - pointer.previousPosition
                                        offset += pan
                                        pointer.consume()
                                    }
                                }
                            } while (true)
                        }
                    }
            ) {
                // Read simTick to ensure canvas redraws during physics iterations
                val _tick = simTick

                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, Offset.Zero)
                }) {
                    // Draw edge connections
                    for ((src, dst) in edges) {
                        drawLine(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.75f),
                            start = Offset(src.x, src.y),
                            end = Offset(dst.x, dst.y),
                            strokeWidth = 3f
                        )
                    }

                    // Connected node IDs for visual highlighting
                    val connectedNodeIds = edges.flatMap { listOf(it.first.noteId, it.second.noteId) }.toSet()

                    // Draw nodes
                    for (node in nodes) {
                        val isConnected = connectedNodeIds.contains(node.noteId)
                        val nodeColor = if (isConnected) Color(0xFF8B5CF6) else Color(0xFF42A5F5)
                        val haloColor = if (isConnected) Color(0x448B5CF6) else Color(0x3342A5F5)

                        // Outer halo
                        drawCircle(
                            color = haloColor,
                            radius = 32f,
                            center = Offset(node.x, node.y)
                        )
                        // Inner circle
                        drawCircle(
                            color = nodeColor,
                            radius = 22f,
                            center = Offset(node.x, node.y)
                        )
                        // Glowing center core
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = 7f,
                            center = Offset(node.x, node.y)
                        )

                        // Node title label
                        val label = if (node.title.length > 15) node.title.take(14) + "…" else node.title
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(node.x - 36f, node.y + 26f),
                            style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            // Floating Zoom & Reset Controls
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                FilledTonalIconButton(
                    onClick = { scale = (scale * 1.3f).coerceIn(0.2f, 5.0f) },
                    shape = CircleShape
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalIconButton(
                    onClick = { scale = (scale / 1.3f).coerceIn(0.2f, 5.0f) },
                    shape = CircleShape
                ) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalIconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    shape = CircleShape
                ) {
                    Text("⟲", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No notes created yet.\nCreate notes and mention them to populate the graph.",
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap node or chip to open note:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${edges.size} connections",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(notes) { note ->
                                val connectionCount = edges.count { it.first.noteId == note.id || it.second.noteId == note.id }
                                InputChip(
                                    selected = false,
                                    onClick = { onNoteClick(note.id) },
                                    label = {
                                        Text(
                                            if (connectionCount > 0)
                                                "${note.title.ifBlank { "Untitled" }} ($connectionCount)"
                                            else
                                                note.title.ifBlank { "Untitled" }
                                        )
                                    },
                                    leadingIcon = {
                                        Text(if (connectionCount > 0) "🔗" else "📄", fontSize = 12.sp)
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
