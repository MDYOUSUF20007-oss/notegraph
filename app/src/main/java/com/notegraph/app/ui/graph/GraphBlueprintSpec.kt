package com.notegraph.app.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

/**
 * Pure geometry and style specification for the Bespoke Luxury
 * technical-blueprint Knowledge Graph renderer.
 */
object GraphBlueprintSpec {
    val SignatureAccent = Color(0xFF8B5CF6)

    const val UNSELECTED_EDGE_ALPHA = 0.30f
    const val ACTIVE_EDGE_ALPHA = 0.85f
    const val BASE_EDGE_STROKE_PX = 1f
    const val ACTIVE_EDGE_STROKE_PX = 2f
    const val HUB_DEGREE_THRESHOLD = 2

    /**
     * Computes a deterministic quadratic Bezier control point so graph connections
     * render as smooth, legible architectural curves instead of rigid spokes.
     */
    fun bezierControlPoint(
        src: Offset,
        dst: Offset,
        curvatureRatio: Float = 0.14f,
        maxOffsetPx: Float = 44f
    ): Offset {
        val midX = (src.x + dst.x) * 0.5f
        val midY = (src.y + dst.y) * 0.5f
        val dx = dst.x - src.x
        val dy = dst.y - src.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return Offset(midX, midY)

        // Canonical orientation ensures the arc is independent of endpoint order
        val sign = if (dx > 0f || (dx == 0f && dy >= 0f)) 1f else -1f
        val nx = (-dy / len) * sign
        val ny = (dx / len) * sign
        val shift = (len * curvatureRatio).coerceAtMost(maxOffsetPx)
        return Offset(midX + nx * shift, midY + ny * shift)
    }

    /**
     * Generous-clearance collision test preventing overlapping technical node labels.
     */
    fun canPlaceLabelWithoutOverlap(
        candidateScreenBox: Rect,
        occupiedScreenBoxes: List<Rect>,
        minPaddingPx: Float = 8f,
        isHighPriority: Boolean = false
    ): Boolean {
        if (isHighPriority) return true
        val padded = Rect(
            left = candidateScreenBox.left - minPaddingPx,
            top = candidateScreenBox.top - minPaddingPx,
            right = candidateScreenBox.right + minPaddingPx,
            bottom = candidateScreenBox.bottom + minPaddingPx
        )
        for (existing in occupiedScreenBoxes) {
            if (padded.overlaps(existing)) {
                return false
            }
        }
        return true
    }

    fun formatCoordinateTag(x: Float, y: Float): String = "[${x.toInt()},${y.toInt()}]"
}
