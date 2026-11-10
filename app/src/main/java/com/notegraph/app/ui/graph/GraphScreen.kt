package com.notegraph.app.ui.graph

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.components.ObsidianCard
import com.notegraph.app.ui.components.ObsidianIconTile
import com.notegraph.app.ui.components.ObsidianMetallicBadge
import com.notegraph.app.ui.components.ObsidianStatTile
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphNode(
    val noteId: String,
    val title: String,
    val folderColor: Color?,
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
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val links by viewModel.links.collectAsState()
    val folders by viewModel.folders.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var searchQuery by remember { mutableStateOf("") }
    var hideOrphans by remember { mutableStateOf(false) }
    var inspectedNodeId by remember { mutableStateOf<String?>(null) }
    var pendingUnlockNote by remember { mutableStateOf<NoteEntity?>(null) }
    val notesById = remember(notes) { notes.associateBy { it.id } }
    val foldersById = remember(folders) { folders.associateBy { it.id } }
    val activePreset = ThemeManager.currentGradient
    val obsidianAccent = ObsidianTokens.resolveAccent(activePreset.accentStart)
    val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)
    val inspectorDateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    fun handleNodeOpen(noteId: String) {
        val target = notesById[noteId]
        if (target != null && target.isLocked && NoteSecurityManager.hasPin(context)) {
            pendingUnlockNote = target
        } else {
            onNoteClick(noteId)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val folderColorsById = remember(folders) {
        folders.associate { f ->
            val col = try {
                Color(android.graphics.Color.parseColor(f.colorHex))
            } catch (_: Exception) {
                null
            }
            f.id to col
        }
    }

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            text = "Graph · ${notes.size}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
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

                val nodes = remember(notes, folderColorsById) {
                    val count = notes.size.coerceAtLeast(1)
                    notes.mapIndexed { index, note ->
                        val angle = (2.0 * Math.PI * index / count).toFloat()
                        val dist = 220f + (index % 3) * 60f
                        GraphNode(
                            noteId = note.id,
                            title = note.title.ifBlank { "Untitled" },
                            folderColor = note.folderId?.let { folderColorsById[it] },
                            x = defaultCenterX + dist * cos(angle),
                            y = defaultCenterY + dist * sin(angle)
                        )
                    }.toMutableList()
                }

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

                    for (link in links) {
                        val src = nodeById[link.sourceNoteId]
                        val dst = (if (link.targetNoteId != null) nodeById[link.targetNoteId] else null)
                            ?: nodeByTitle[link.targetNoteTitle.trim().lowercase()]
                        addEdge(src, dst)
                    }

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

                var simTick by remember { mutableIntStateOf(0) }

                LaunchedEffect(nodes, edges, defaultCenterX, defaultCenterY) {
                    if (nodes.size < 2) return@LaunchedEffect
                    repeat(90) {
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

                        for (node in nodes) {
                            node.vx += (defaultCenterX - node.x) * 0.005f
                            node.vy += (defaultCenterY - node.y) * 0.005f
                        }

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

                val connectedNodeIds = remember(edges) {
                    edges.flatMap { listOf(it.first.noteId, it.second.noteId) }.toSet()
                }
                val qTrim = searchQuery.trim().lowercase()
                val visibleNodes = remember(nodes, connectedNodeIds, hideOrphans) {
                    if (hideOrphans) nodes.filter { it.noteId in connectedNodeIds } else nodes
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(visibleNodes) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                val downTime = System.currentTimeMillis()
                                var isTransforming = false

                                var touchedNode: GraphNode? = null
                                var minDistSq = Float.MAX_VALUE
                                for (node in visibleNodes) {
                                    val sx = node.x * scale + offset.x
                                    val sy = node.y * scale + offset.y
                                    val dx = downPos.x - sx
                                    val dy = downPos.y - sy
                                    val dSq = dx * dx + dy * dy
                                    if (dSq < minDistSq) {
                                        minDistSq = dSq
                                        touchedNode = node
                                    }
                                }
                                val nodeGrabRadius = maxOf(42f * scale, 65f)
                                val grabbedNode = if (touchedNode != null && minDistSq <= nodeGrabRadius * nodeGrabRadius) touchedNode else null

                                do {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.filter { it.pressed }

                                    if (activePointers.isEmpty()) {
                                        val duration = System.currentTimeMillis() - downTime
                                        if (!isTransforming && duration < 350) {
                                            if (grabbedNode != null) {
                                                if (inspectedNodeId == grabbedNode.noteId) {
                                                    handleNodeOpen(grabbedNode.noteId)
                                                } else {
                                                    inspectedNodeId = grabbedNode.noteId
                                                }
                                            } else {
                                                inspectedNodeId = null
                                            }
                                        }
                                        break
                                    }

                                    if (activePointers.size > 1) {
                                        isTransforming = true
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()

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
                                        val pointer = activePointers.first()
                                        val diff = pointer.position - downPos
                                        if (diff.x * diff.x + diff.y * diff.y > 144f) {
                                            isTransforming = true
                                        }
                                        if (isTransforming) {
                                            val delta = pointer.position - pointer.previousPosition
                                            if (grabbedNode != null) {
                                                grabbedNode.x += delta.x / scale
                                                grabbedNode.y += delta.y / scale
                                                grabbedNode.vx = 0f
                                                grabbedNode.vy = 0f
                                                simTick++
                                            } else {
                                                offset += delta
                                            }
                                            pointer.consume()
                                        }
                                    }
                                } while (true)
                            }
                        }
                ) {
                    val _tick = simTick

                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, Offset.Zero)
                    }) {
                        for ((src, dst) in edges) {
                            if (hideOrphans && (src.noteId !in connectedNodeIds || dst.noteId !in connectedNodeIds)) continue
                            val edgeMatchesSearch = qTrim.isNotEmpty() &&
                                (src.title.lowercase().contains(qTrim) || dst.title.lowercase().contains(qTrim))
                            val edgeTouchesInspected = inspectedNodeId != null &&
                                (src.noteId == inspectedNodeId || dst.noteId == inspectedNodeId)
                            drawLine(
                                color = when {
                                    edgeTouchesInspected -> activePreset.accentEnd.copy(alpha = 0.95f)
                                    edgeMatchesSearch -> activePreset.accentEnd.copy(alpha = 0.9f)
                                    else -> activePreset.accentStart.copy(alpha = if (qTrim.isEmpty()) 0.55f else 0.18f)
                                },
                                start = Offset(src.x, src.y),
                                end = Offset(dst.x, dst.y),
                                strokeWidth = if (edgeTouchesInspected || edgeMatchesSearch) 3.5f else 2f
                            )
                        }

                        for (node in visibleNodes) {
                            val isConnected = connectedNodeIds.contains(node.noteId)
                            val isSearchMatch = qTrim.isNotEmpty() && node.title.lowercase().contains(qTrim)
                            val isInspected = inspectedNodeId == node.noteId
                            val dimAlpha = if (qTrim.isNotEmpty() && !isSearchMatch) 0.25f else 1f
                            val nodeColor = (node.folderColor ?: if (isConnected) activePreset.accentStart else activePreset.accentEnd)
                                .copy(alpha = dimAlpha)
                            val haloColor = nodeColor.copy(alpha = 0.2f * dimAlpha)

                            if (isInspected) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = 38f,
                                    center = Offset(node.x, node.y),
                                    style = Stroke(width = 3f)
                                )
                            } else if (isSearchMatch) {
                                drawCircle(
                                    color = activePreset.accentEnd,
                                    radius = 36f,
                                    center = Offset(node.x, node.y),
                                    style = Stroke(width = 2.5f)
                                )
                            }

                            drawCircle(
                                color = haloColor,
                                radius = if (isConnected) 32f else 26f,
                                center = Offset(node.x, node.y)
                            )
                            drawCircle(
                                color = nodeColor,
                                radius = if (isConnected) 20f else 16f,
                                center = Offset(node.x, node.y)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f * dimAlpha),
                                radius = 5.5f,
                                center = Offset(node.x, node.y)
                            )

                            val label = if (node.title.length > 15) node.title.take(14) + "…" else node.title
                            drawText(
                                textMeasurer = textMeasurer,
                                text = label,
                                topLeft = Offset(node.x - 36f, node.y + 24f),
                                style = TextStyle(
                                    color = labelColor.copy(alpha = dimAlpha),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSearchMatch) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Minimalist Floating Filter Pill HUD
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 12.dp, start = 14.dp, end = 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = activePreset.accentStart,
                                modifier = Modifier.size(16.dp)
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(activePreset.accentStart),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Filter nodes…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                    inner()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    FilterChip(
                        selected = hideOrphans,
                        onClick = { hideOrphans = !hideOrphans },
                        label = { Text(if (hideOrphans) "Linked" else "All", fontSize = 12.sp) }
                    )
                }

                // Floating Clean Vector Zoom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    SmallCircleControlButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Zoom In",
                        onClick = { scale = (scale * 1.3f).coerceIn(0.2f, 5.0f) }
                    )
                    SmallCircleControlButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Zoom Out",
                        onClick = { scale = (scale / 1.3f).coerceIn(0.2f, 5.0f) }
                    )
                    SmallCircleControlButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Reset View",
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ObsidianCard(
                            accentColor = obsidianAccent,
                            headerContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ObsidianIconTile(
                                        icon = Icons.Rounded.Share,
                                        accentColor = obsidianAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Knowledge Graph",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianTokens.TextPrimary
                                            )
                                            ObsidianMetallicBadge(
                                                text = "EMPTY",
                                                accentColor = obsidianAccent
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "@vault • [[Wiki-Links]] Graph",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                    ObsidianStatTile(
                                        value = "0 nodes",
                                        label = "GRAPH",
                                        accentColor = obsidianAccent
                                    )
                                }
                            },
                            footerContent = {
                                Text(
                                    text = "Create notes and link them with [[Title]] to visualize nodes and backlinks. Stored locally in Room Database.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ObsidianTokens.FooterText
                                )
                            }
                        )
                    }
                } else {
                    val inspectedNote = (inspectedNodeId?.let { notesById[it] })
                        ?: visibleNodes.firstOrNull { connectedNodeIds.contains(it.noteId) }?.let { notesById[it.noteId] }
                        ?: visibleNodes.firstOrNull()?.let { notesById[it.noteId] }
                        ?: notes.first()
                    val selectedId = inspectedNote.id
                    val nodeConnections = edges.count { it.first.noteId == selectedId || it.second.noteId == selectedId }
                    val nodeFolder = inspectedNote.folderId?.let { foldersById[it] }
                    val firstTag = Regex("#([a-zA-Z0-9_\\-/]+)").find("${inspectedNote.content} ${inspectedNote.blocksJson}")?.value ?: "#node"
                    val inspectorIcon = when {
                        inspectedNote.isLocked -> Icons.Rounded.Lock
                        nodeConnections > 0 -> Icons.Rounded.Share
                        else -> Icons.Rounded.Description
                    }

                    ObsidianCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp),
                        accentColor = obsidianAccent,
                        onClick = { handleNodeOpen(selectedId) },
                        verticalPadding = 0.dp,
                        headerContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ObsidianIconTile(
                                    icon = inspectorIcon,
                                    accentColor = obsidianAccent
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = inspectedNote.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianTokens.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        ObsidianMetallicBadge(
                                            text = when {
                                                inspectedNote.isLocked -> "LOCKED"
                                                nodeConnections > 0 -> "HUB"
                                                else -> "NODE"
                                            },
                                            accentColor = obsidianAccent
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "@${nodeFolder?.name?.lowercase()?.replace(' ', '_') ?: "vault"} • $firstTag",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = ObsidianTokens.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ObsidianStatTile(
                                    value = "$nodeConnections link${if (nodeConnections == 1) "" else "s"}",
                                    label = "EDGES",
                                    accentColor = obsidianAccent
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(notes, key = { it.id }) { note ->
                                    val connectionCount = edges.count { it.first.noteId == note.id || it.second.noteId == note.id }
                                    val isNodeSelected = note.id == selectedId
                                    val chipShape = RoundedCornerShape(10.dp)
                                    Row(
                                        modifier = Modifier
                                            .clip(chipShape)
                                            .background(
                                                if (isNodeSelected) obsidianAccent.copy(alpha = 0.20f)
                                                else ObsidianTokens.StatTileSurface
                                            )
                                            .border(
                                                width = if (isNodeSelected) 1.dp else 0.75.dp,
                                                color = if (isNodeSelected) obsidianAccent else ObsidianTokens.DividerRule,
                                                shape = chipShape
                                            )
                                            .clickable {
                                                if (isNodeSelected) {
                                                    handleNodeOpen(note.id)
                                                } else {
                                                    inspectedNodeId = note.id
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                note.isLocked -> Icons.Rounded.Lock
                                                connectionCount > 0 -> Icons.Rounded.Link
                                                else -> Icons.Rounded.Description
                                            },
                                            contentDescription = null,
                                            tint = if (isNodeSelected) accentInk else ObsidianTokens.TextMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (connectionCount > 0) {
                                                "${note.title.ifBlank { "Untitled" }} ($connectionCount)"
                                            } else {
                                                note.title.ifBlank { "Untitled" }
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isNodeSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isNodeSelected) ObsidianTokens.TextPrimary else ObsidianTokens.TextSecondary
                                        )
                                    }
                                }
                            }
                        },
                        footerContent = {
                            Text(
                                text = "Modified ${inspectorDateFormat.format(Date(inspectedNote.updatedAt))} • ${visibleNodes.size} nodes / ${edges.size} canvas edges indexed locally in Room DB • Tap card or selected chip to open",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                color = ObsidianTokens.FooterText
                            )
                        }
                    )
                }
            }
        }
    }

    pendingUnlockNote?.let { locked ->
        VaultPinDialog(
            isSettingNewPin = false,
            title = "Unlock Note",
            subtitle = "\"${locked.title.ifBlank { "Untitled" }}\" is protected.",
            onDismiss = { pendingUnlockNote = null },
            onVerify = { attempt -> NoteSecurityManager.verifyPin(context, attempt) },
            onSuccess = {
                pendingUnlockNote = null
                onNoteClick(locked.id)
            }
        )
    }
}

@Composable
private fun SmallCircleControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
