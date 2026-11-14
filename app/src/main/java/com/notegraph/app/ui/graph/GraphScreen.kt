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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.NoteJson
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.floor
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
    onNavigateTutorial: () -> Unit = {},
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
    val isMaterialYou = ThemeManager.isMaterialYou
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val obsidianAccent = when {
        isMaterialYou -> MaterialTheme.colorScheme.primary
        isLuxury -> lux.accent
        else -> ObsidianTokens.resolveAccent(activePreset.accentStart)
    }
    val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)

    fun handleNodeOpen(noteId: String) {
        val target = notesById[noteId]
        if (target != null && target.isLocked && NoteSecurityManager.hasPin(context)) {
            pendingUnlockNote = target
        } else {
            onNoteClick(noteId)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
    val coordLabelColor = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (isLuxury) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Knowledge Graph",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = lux.textPrimary
                                )
                                Text(
                                    text = "${notes.size} NODES",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp,
                                    color = lux.textMuted
                                )
                            }
                        } else {
                            Text(
                                text = "Graph · ${notes.size}",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateTutorial) {
                            Box(
                                modifier = Modifier
                                    .size(23.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isLuxury) 1.dp else 1.5.dp,
                                        color = if (isLuxury) lux.borderHairline else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLuxury) lux.textSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

                val edges by produceState(
                    initialValue = emptyList<Pair<GraphNode, GraphNode>>(),
                    links,
                    nodes,
                    notes
                ) {
                    value = withContext(Dispatchers.Default) {
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

                                if (fullText.contains(otherTitle, ignoreCase = true)) {
                                    val isMentioned = fullText.contains("[[$otherTitle]]", ignoreCase = true) ||
                                        Regex("""(?i)\b${Regex.escape(otherTitle)}\b""").containsMatchIn(fullText)
                                    if (isMentioned) {
                                        val dst = nodeById[other.id]
                                        addEdge(src, dst)
                                    }
                                }
                            }
                        }

                        result
                    }
                }

                var simTick by remember { mutableIntStateOf(0) }

                LaunchedEffect(nodes, edges, defaultCenterX, defaultCenterY, isLuxury) {
                    if (nodes.size < 2) return@LaunchedEffect
                    val repulsionConstant = if (isLuxury) 8400f else 6500f
                    val springRestLength = if (isLuxury) 200f else 160f
                    for (step in 0 until 90) {
                        for (i in nodes.indices) {
                            for (j in i + 1 until nodes.size) {
                                val a = nodes[i]
                                val b = nodes[j]
                                val dx = a.x - b.x
                                val dy = a.y - b.y
                                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(10f)
                                val force = repulsionConstant / (dist * dist)
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
                            val force = (dist - springRestLength) * 0.05f
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

                        var maxStepSq = 0f
                        for (node in nodes) {
                            node.vx *= 0.82f
                            node.vy *= 0.82f
                            node.x += node.vx
                            node.y += node.vy
                            val stepSq = node.vx * node.vx + node.vy * node.vy
                            if (stepSq > maxStepSq) maxStepSq = stepSq
                        }

                        simTick++
                        if (step >= 12 && maxStepSq < 0.015f) {
                            break
                        }
                        delay(16L)
                    }
                }

                val connectedNodeIds = remember(edges) {
                    edges.flatMap { listOf(it.first.noteId, it.second.noteId) }.toSet()
                }
                val nodeDegreeMap = remember(edges) {
                    buildMap<String, Int> {
                        for ((a, b) in edges) {
                            put(a.noteId, (get(a.noteId) ?: 0) + 1)
                            put(b.noteId, (get(b.noteId) ?: 0) + 1)
                        }
                    }
                }
                val qTrim = searchQuery.trim().lowercase()
                val visibleNodes = remember(nodes, connectedNodeIds, hideOrphans) {
                    if (hideOrphans) nodes.filter { it.noteId in connectedNodeIds } else nodes
                }
                val labelLayouts: Map<String, TextLayoutResult> = remember(visibleNodes, labelColor, qTrim, isLuxury, isMaterialYou) {
                    visibleNodes.associate { node ->
                        val label = if (node.title.length > 15) node.title.take(14) + "…" else node.title
                        val isMatch = qTrim.isNotEmpty() && node.title.lowercase().contains(qTrim)
                        node.noteId to textMeasurer.measure(
                            text = label,
                            style = when {
                                isMaterialYou -> TextStyle(
                                    color = labelColor,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.15.sp,
                                    fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Medium
                                )
                                isLuxury -> TextStyle(
                                    color = labelColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 0.35.sp,
                                    fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Medium
                                )
                                else -> TextStyle(
                                    color = labelColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
                val coordLayouts: Map<String, TextLayoutResult> = remember(
                    visibleNodes,
                    inspectedNodeId,
                    nodeDegreeMap,
                    simTick,
                    isLuxury,
                    isMaterialYou,
                    coordLabelColor,
                    qTrim
                ) {
                    if (!isLuxury && !isMaterialYou) {
                        emptyMap()
                    } else {
                        visibleNodes
                            .filter { node ->
                                val degree = nodeDegreeMap[node.noteId] ?: 0
                                val isCoreHub = degree >= GraphBlueprintSpec.HUB_DEGREE_THRESHOLD
                                val isMatch = qTrim.isNotEmpty() && node.title.lowercase().contains(qTrim)
                                node.noteId == inspectedNodeId || isMatch || isCoreHub
                            }
                            .take(16)
                            .associate { node ->
                                val degree = nodeDegreeMap[node.noteId] ?: 0
                                val tag = if (isMaterialYou) {
                                    if (degree >= GraphBlueprintSpec.HUB_DEGREE_THRESHOLD) "HUB • $degree" else "$degree LINKS"
                                } else {
                                    GraphBlueprintSpec.formatCoordinateTag(node.x, node.y)
                                }
                                node.noteId to textMeasurer.measure(
                                    text = tag,
                                    style = TextStyle(
                                        color = coordLabelColor,
                                        fontFamily = if (isMaterialYou) FontFamily.SansSerif else FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                )
                            }
                    }
                }

                val m3ColorScheme = MaterialTheme.colorScheme
                val m3OutlineVariant = m3ColorScheme.outlineVariant
                val m3Primary = m3ColorScheme.primary
                val m3Tertiary = m3ColorScheme.tertiary
                val m3SurfaceVariant = m3ColorScheme.surfaceVariant

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
                    val safeScale = scale.coerceAtLeast(0.05f)
                    val viewLeft = (-offset.x) / safeScale - 140f
                    val viewTop = (-offset.y) / safeScale - 140f
                    val viewRight = (size.width - offset.x) / safeScale + 140f
                    val viewBottom = (size.height - offset.y) / safeScale + 140f

                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, Offset.Zero)
                    }) {
                        if (isLuxury || isMaterialYou) {
                            // Subtle architectural blueprint drafting grid (razor-thin 1px)
                            val gridStep = 120f
                            val onePxStroke = 1f / safeScale
                            val gridColor = if (isMaterialYou) {
                                m3OutlineVariant.copy(alpha = 0.08f)
                            } else {
                                lux.textMuted.copy(alpha = 0.06f)
                            }
                            val startX = floor(viewLeft / gridStep) * gridStep
                            val startY = floor(viewTop / gridStep) * gridStep
                            var gx = startX
                            while (gx <= viewRight) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(gx, viewTop),
                                    end = Offset(gx, viewBottom),
                                    strokeWidth = onePxStroke
                                )
                                gx += gridStep
                            }
                            var gy = startY
                            while (gy <= viewBottom) {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(viewLeft, gy),
                                    end = Offset(viewRight, gy),
                                    strokeWidth = onePxStroke
                                )
                                gy += gridStep
                            }

                            // Ultra-thin (1px), 30%-opacity smooth curved Bezier edges
                            val edgePath = Path()
                            for ((src, dst) in edges) {
                                if (hideOrphans && (src.noteId !in connectedNodeIds || dst.noteId !in connectedNodeIds)) continue
                                if ((src.x < viewLeft && dst.x < viewLeft) ||
                                    (src.x > viewRight && dst.x > viewRight) ||
                                    (src.y < viewTop && dst.y < viewTop) ||
                                    (src.y > viewBottom && dst.y > viewBottom)
                                ) continue

                                val edgeMatchesSearch = qTrim.isNotEmpty() &&
                                    (src.title.lowercase().contains(qTrim) || dst.title.lowercase().contains(qTrim))
                                val edgeTouchesInspected = inspectedNodeId != null &&
                                    (src.noteId == inspectedNodeId || dst.noteId == inspectedNodeId)
                                val highlighted = edgeTouchesInspected || edgeMatchesSearch

                                val p0 = Offset(src.x, src.y)
                                val p1 = Offset(dst.x, dst.y)
                                val ctrl = GraphBlueprintSpec.bezierControlPoint(p0, p1)
                                edgePath.reset()
                                edgePath.moveTo(p0.x, p0.y)
                                edgePath.quadraticBezierTo(ctrl.x, ctrl.y, p1.x, p1.y)

                                drawPath(
                                    path = edgePath,
                                    color = if (highlighted) {
                                        if (isMaterialYou) m3Primary.copy(alpha = 0.88f)
                                        else GraphBlueprintSpec.SignatureAccent.copy(alpha = GraphBlueprintSpec.ACTIVE_EDGE_ALPHA)
                                    } else {
                                        if (isMaterialYou) m3OutlineVariant.copy(
                                            alpha = if (qTrim.isEmpty()) 0.40f else 0.15f
                                        )
                                        else lux.textMuted.copy(
                                            alpha = GraphBlueprintSpec.UNSELECTED_EDGE_ALPHA *
                                                (if (qTrim.isEmpty()) 1f else 0.35f)
                                        )
                                    },
                                    style = Stroke(
                                        width = (if (highlighted) {
                                            if (isMaterialYou) 2.2f else GraphBlueprintSpec.ACTIVE_EDGE_STROKE_PX
                                        } else {
                                            if (isMaterialYou) 1.2f else GraphBlueprintSpec.BASE_EDGE_STROKE_PX
                                        }) / safeScale,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }

                            // Clean minimalist blueprint shapes (neutral fill + 1px hairline border; #8B5CF6 accent reserved for active selection or core clusters)
                            val strokeUnit = 1.25f / safeScale
                            val occupiedLabelBoxes = mutableListOf<Rect>()
                            val drawOrder = visibleNodes.sortedByDescending { node ->
                                val deg = nodeDegreeMap[node.noteId] ?: 0
                                val priority = (inspectedNodeId == node.noteId) ||
                                    (qTrim.isNotEmpty() && node.title.lowercase().contains(qTrim)) ||
                                    deg >= GraphBlueprintSpec.HUB_DEGREE_THRESHOLD
                                if (priority) 1 else 0
                            }

                            for (node in drawOrder) {
                                if (node.x < viewLeft || node.x > viewRight || node.y < viewTop || node.y > viewBottom) {
                                    continue
                                }
                                val degree = nodeDegreeMap[node.noteId] ?: 0
                                val isCoreHub = degree >= GraphBlueprintSpec.HUB_DEGREE_THRESHOLD
                                val isSearchMatch = qTrim.isNotEmpty() && node.title.lowercase().contains(qTrim)
                                val isInspected = inspectedNodeId == node.noteId
                                val forceShowLabel = isInspected || isSearchMatch
                                val dimAlpha = if (qTrim.isNotEmpty() && !isSearchMatch && !isInspected) 0.25f else 1f

                                val strokeColor = when {
                                    isMaterialYou -> when {
                                        isInspected || isSearchMatch -> m3Primary
                                        isCoreHub -> m3Tertiary
                                        else -> m3OutlineVariant.copy(alpha = 0.70f * dimAlpha)
                                    }
                                    isInspected || isSearchMatch -> GraphBlueprintSpec.SignatureAccent
                                    isCoreHub -> GraphBlueprintSpec.SignatureAccent.copy(alpha = 0.78f * dimAlpha)
                                    else -> lux.textMuted.copy(alpha = 0.65f * dimAlpha)
                                }
                                val fillColor = when {
                                    isMaterialYou -> (node.folderColor ?: m3SurfaceVariant).copy(alpha = dimAlpha)
                                    else -> lux.elevatedSurface.copy(alpha = dimAlpha)
                                }

                                if (isCoreHub) {
                                    val w = 38f
                                    val h = 24f
                                    val topLeft = Offset(node.x - w / 2f, node.y - h / 2f)
                                    val corner = CornerRadius(5f, 5f)
                                    if (isInspected) {
                                        drawRoundRect(
                                            color = GraphBlueprintSpec.SignatureAccent,
                                            topLeft = Offset(node.x - (w + 10f) / 2f, node.y - (h + 10f) / 2f),
                                            size = Size(w + 10f, h + 10f),
                                            cornerRadius = CornerRadius(7f, 7f),
                                            style = Stroke(width = 1.5f / safeScale)
                                        )
                                    }
                                    drawRoundRect(
                                        color = fillColor,
                                        topLeft = topLeft,
                                        size = Size(w, h),
                                        cornerRadius = corner
                                    )
                                    drawRoundRect(
                                        color = strokeColor,
                                        topLeft = topLeft,
                                        size = Size(w, h),
                                        cornerRadius = corner,
                                        style = Stroke(width = strokeUnit)
                                    )
                                    drawCircle(
                                        color = GraphBlueprintSpec.SignatureAccent.copy(alpha = dimAlpha),
                                        radius = 2.6f,
                                        center = Offset(node.x, node.y)
                                    )
                                } else {
                                    val radius = 12f
                                    if (isInspected || isSearchMatch) {
                                        drawCircle(
                                            color = GraphBlueprintSpec.SignatureAccent,
                                            radius = radius + 6f,
                                            center = Offset(node.x, node.y),
                                            style = Stroke(width = 1.5f / safeScale)
                                        )
                                    }
                                    drawCircle(
                                        color = fillColor,
                                        radius = radius,
                                        center = Offset(node.x, node.y)
                                    )
                                    drawCircle(
                                        color = strokeColor,
                                        radius = radius,
                                        center = Offset(node.x, node.y),
                                        style = Stroke(width = strokeUnit)
                                    )
                                    drawCircle(
                                        color = (if (isInspected || isSearchMatch) GraphBlueprintSpec.SignatureAccent else lux.textMuted)
                                            .copy(alpha = dimAlpha),
                                        radius = 2.2f,
                                        center = Offset(node.x, node.y)
                                    )
                                }

                                val layout = labelLayouts[node.noteId]
                                if (layout != null) {
                                    val labelWorldX = node.x - layout.size.width / 2f
                                    val labelWorldY = node.y + (if (isCoreHub) 18f else 19f)
                                    val screenLeft = labelWorldX * safeScale + offset.x
                                    val screenTop = labelWorldY * safeScale + offset.y
                                    val candidateScreenBox = Rect(
                                        left = screenLeft,
                                        top = screenTop,
                                        right = screenLeft + layout.size.width * safeScale,
                                        bottom = screenTop + layout.size.height * safeScale
                                    )
                                    if (GraphBlueprintSpec.canPlaceLabelWithoutOverlap(
                                            candidateScreenBox = candidateScreenBox,
                                            occupiedScreenBoxes = occupiedLabelBoxes,
                                            minPaddingPx = 8f,
                                            isHighPriority = forceShowLabel
                                        )
                                    ) {
                                        occupiedLabelBoxes.add(candidateScreenBox)
                                        drawText(
                                            textLayoutResult = layout,
                                            topLeft = Offset(labelWorldX, labelWorldY),
                                            alpha = dimAlpha
                                        )
                                        val coordLayout = coordLayouts[node.noteId]
                                        if (coordLayout != null && (safeScale >= 0.72f || isInspected)) {
                                            val coordWorldX = node.x - coordLayout.size.width / 2f
                                            val coordWorldY = labelWorldY + layout.size.height + 2f
                                            drawText(
                                                textLayoutResult = coordLayout,
                                                topLeft = Offset(coordWorldX, coordWorldY),
                                                alpha = dimAlpha * 0.88f
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            for ((src, dst) in edges) {
                                if (hideOrphans && (src.noteId !in connectedNodeIds || dst.noteId !in connectedNodeIds)) continue
                                if ((src.x < viewLeft && dst.x < viewLeft) ||
                                    (src.x > viewRight && dst.x > viewRight) ||
                                    (src.y < viewTop && dst.y < viewTop) ||
                                    (src.y > viewBottom && dst.y > viewBottom)
                                ) continue
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
                                if (node.x < viewLeft || node.x > viewRight || node.y < viewTop || node.y > viewBottom) {
                                    continue
                                }
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

                                val layout = labelLayouts[node.noteId]
                                if (layout != null) {
                                    drawText(
                                        textLayoutResult = layout,
                                        topLeft = Offset(node.x - layout.size.width / 2f, node.y + 24f),
                                        alpha = dimAlpha
                                    )
                                }
                            }
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
                        shape = RoundedCornerShape(if (isLuxury) 100.dp else 22.dp),
                        color = if (isLuxury) lux.elevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        border = BorderStroke(
                            width = if (isLuxury) 1.dp else 0.75.dp,
                            color = if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = if (isLuxury) 9.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (isLuxury) lux.accent else activePreset.accentStart,
                                modifier = Modifier.size(16.dp)
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = if (isLuxury) {
                                    TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = lux.textPrimary
                                    )
                                } else {
                                    MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                cursorBrush = SolidColor(if (isLuxury) lux.accent else activePreset.accentStart),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (isLuxury) "FILTER NODES..." else "Filter nodes…",
                                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = (if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant)
                                                .copy(alpha = if (isLuxury) 0.78f else 0.55f)
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
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = if (isLuxury) lux.textMuted else LocalContentColor.current,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isLuxury) {
                        Surface(
                            onClick = { hideOrphans = !hideOrphans },
                            shape = RoundedCornerShape(100.dp),
                            color = if (hideOrphans) lux.accent.copy(alpha = 0.14f) else lux.elevatedSurface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (hideOrphans) lux.accent else lux.borderHairline
                            )
                        ) {
                            Text(
                                text = if (hideOrphans) "LINKED" else "ALL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hideOrphans) lux.accent else lux.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    } else {
                        FilterChip(
                            selected = hideOrphans,
                            onClick = { hideOrphans = !hideOrphans },
                            label = { Text(if (hideOrphans) "Linked" else "All", fontSize = 12.sp) }
                        )
                    }
                }

                if (notes.isNotEmpty() && edges.isEmpty()) {
                    Surface(
                        onClick = onNavigateTutorial,
                        shape = RoundedCornerShape(if (isLuxury) 8.dp else 18.dp),
                        color = if (isLuxury) lux.elevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, obsidianAccent.copy(alpha = if (isLuxury) 0.72f else 0.68f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 66.dp, start = 14.dp, end = 72.dp)
                    ) {
                        Text(
                            text = if (isLuxury) "NO EDGES // TAP FOR NODE LINKING SPEC →" else "No edges yet? Tap for Graph node linking guide →",
                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                // Floating Clean Vector Zoom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLuxury) 6.dp else 8.dp),
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
                        shape = RoundedCornerShape(if (isLuxury) 6.dp else 10.dp),
                        color = if (isLuxury) lux.elevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
                        border = BorderStroke(
                            width = if (isLuxury) 1.dp else 0.75.dp,
                            color = if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor
                        )
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLuxury) lux.textSecondary else MaterialTheme.colorScheme.onSurface,
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
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onNavigateTutorial,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.75.dp, obsidianAccent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentInk)
                                ) {
                                    Text("How to Make & Connect Nodes →", fontSize = 12.sp)
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
                                    val chipShape = RoundedCornerShape(if (isLuxury) 6.dp else 10.dp)
                                    Row(
                                        modifier = Modifier
                                            .clip(chipShape)
                                            .background(
                                                if (isNodeSelected) {
                                                    if (isLuxury) obsidianAccent.copy(alpha = 0.14f) else obsidianAccent.copy(alpha = 0.20f)
                                                } else {
                                                    ObsidianTokens.StatTileSurface
                                                }
                                            )
                                            .border(
                                                width = if (isLuxury || isNodeSelected) 1.dp else 0.75.dp,
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
                                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                            fontSize = if (isLuxury) 11.sp else 12.sp,
                                            fontWeight = if (isNodeSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isNodeSelected) ObsidianTokens.TextPrimary else ObsidianTokens.TextSecondary
                                        )
                                    }
                                }
                            }
                        },
                        footerContent = {
                            Text(
                                text = "Modified ${NoteJson.formatShortDateTime(inspectedNote.updatedAt)} • ${visibleNodes.size} nodes / ${edges.size} canvas edges indexed locally in Room DB • Tap card or selected chip to open",
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
    val isMaterialYou = ThemeManager.isMaterialYou
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val shape = when {
        isMaterialYou -> RoundedCornerShape(12.dp)
        isLuxury -> RoundedCornerShape(8.dp)
        else -> CircleShape
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = when {
            isMaterialYou -> MaterialTheme.colorScheme.surfaceVariant
            isLuxury -> lux.elevatedSurface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        },
        border = BorderStroke(
            width = if (isLuxury) 1.dp else 0.75.dp,
            color = when {
                isMaterialYou -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                isLuxury -> lux.borderHairline
                else -> ThemeManager.cardBorderColor
            }
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.size(if (isLuxury) 36.dp else 38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    isMaterialYou -> MaterialTheme.colorScheme.onSurfaceVariant
                    isLuxury -> lux.textPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
