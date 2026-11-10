package com.notegraph.app.ui.editor

import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class InkBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f

    fun contains(x: Float, y: Float, padding: Float = 0f): Boolean {
        return x >= (minX - padding) &&
            x <= (maxX + padding) &&
            y >= (minY - padding) &&
            y <= (maxY + padding)
    }
}

fun computeStrokeSetBounds(
    strokes: List<InkStroke>,
    selectedIndices: Set<Int>
): InkBounds? {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var hasPoints = false

    for (idx in selectedIndices) {
        if (idx !in strokes.indices) continue
        val pts = strokes[idx].points
        for (p in pts) {
            hasPoints = true
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
    }
    return if (hasPoints) InkBounds(minX, minY, maxX, maxY) else null
}

/**
 * Translates all selected strokes uniformly by (dx, dy) in any direction while keeping
 * the selection within the canvas without distorting stroke proportions.
 */
fun translateSelectedStrokes(
    strokes: List<InkStroke>,
    selectedIndices: Set<Int>,
    dx: Float,
    dy: Float,
    canvasWidth: Float = Float.POSITIVE_INFINITY,
    canvasHeight: Float = Float.POSITIVE_INFINITY
): List<InkStroke> {
    if (selectedIndices.isEmpty() || (dx == 0f && dy == 0f)) return strokes
    val bounds = computeStrokeSetBounds(strokes, selectedIndices) ?: return strokes

    val clampedDx = clampTranslationDelta(bounds.minX, bounds.maxX, dx, canvasWidth)
    val clampedDy = clampTranslationDelta(bounds.minY, bounds.maxY, dy, canvasHeight)

    if (clampedDx == 0f && clampedDy == 0f) return strokes

    return strokes.mapIndexed { idx, stroke ->
        if (idx in selectedIndices) {
            stroke.copy(
                points = stroke.points.map { pt ->
                    StrokePoint(pt.x + clampedDx, pt.y + clampedDy)
                }
            )
        } else {
            stroke
        }
    }
}

private fun clampTranslationDelta(
    minCoord: Float,
    maxCoord: Float,
    delta: Float,
    canvasLimit: Float
): Float {
    if (!canvasLimit.isFinite() || canvasLimit <= 0f) return delta
    val span = maxCoord - minCoord
    return if (span <= canvasLimit) {
        val allowedMin = min(0f, minCoord)
        val allowedMax = max(canvasLimit, maxCoord)
        val minDelta = allowedMin - minCoord
        val maxDelta = allowedMax - maxCoord
        delta.coerceIn(minDelta, maxDelta)
    } else {
        // Large selection wider/taller than canvas: preserve at least 32px overlap
        val overlap = min(32f, canvasLimit * 0.25f)
        val minDelta = min(0f, overlap - maxCoord)
        val maxDelta = max(0f, (canvasLimit - overlap) - minCoord)
        delta.coerceIn(minDelta, maxDelta)
    }
}

/**
 * Scales selected strokes around their common bounding box center.
 */
fun scaleSelectedStrokes(
    strokes: List<InkStroke>,
    selectedIndices: Set<Int>,
    factor: Float
): List<InkStroke> {
    if (factor <= 0f || selectedIndices.isEmpty()) return strokes
    val bounds = computeStrokeSetBounds(strokes, selectedIndices) ?: return strokes
    val centerX = bounds.centerX
    val centerY = bounds.centerY

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

/**
 * Point-in-polygon ray casting algorithm.
 */
fun isPointInPolygon(p: StrokePoint, polygon: List<StrokePoint>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val dy = pj.y - pi.y
        if (abs(dy) > 1e-6f &&
            (pi.y > p.y) != (pj.y > p.y) &&
            (p.x < (pj.x - pi.x) * (p.y - pi.y) / dy + pi.x)
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Determines whether any vertex, sampled segment point, or segment intersection
 * lies within or crosses the closed lasso polygon.
 */
fun isStrokeInsideLasso(stroke: InkStroke, polygon: List<StrokePoint>): Boolean {
    val pts = stroke.points
    if (pts.isEmpty() || polygon.size < 3) return false

    // Fast bounding-box rejection
    val polyMinX = polygon.minOf { it.x }
    val polyMaxX = polygon.maxOf { it.x }
    val polyMinY = polygon.minOf { it.y }
    val polyMaxY = polygon.maxOf { it.y }

    val strokeMinX = pts.minOf { it.x }
    val strokeMaxX = pts.maxOf { it.x }
    val strokeMinY = pts.minOf { it.y }
    val strokeMaxY = pts.maxOf { it.y }

    if (strokeMaxX < polyMinX || strokeMinX > polyMaxX ||
        strokeMaxY < polyMinY || strokeMinY > polyMaxY
    ) {
        return false
    }

    if (pts.any { isPointInPolygon(it, polygon) }) return true

    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val segLen = hypot(b.x - a.x, b.y - a.y)
        val samples = min(16, max(2, ceil(segLen / 12f).toInt()))
        for (step in 1 until samples) {
            val t = step.toFloat() / samples.toFloat()
            val mid = StrokePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            if (isPointInPolygon(mid, polygon)) return true
        }
        // Check if stroke segment intersects any polygon edge (handles lasso cutting across straight lines)
        for (k in polygon.indices) {
            val c = polygon[k]
            val d = polygon[(k + 1) % polygon.size]
            if (segmentsIntersect(a, b, c, d)) return true
        }
    }
    return false
}

private fun hypot(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

private fun segmentsIntersect(
    a: StrokePoint,
    b: StrokePoint,
    c: StrokePoint,
    d: StrokePoint
): Boolean {
    if (max(a.x, b.x) < min(c.x, d.x) || max(c.x, d.x) < min(a.x, b.x) ||
        max(a.y, b.y) < min(c.y, d.y) || max(c.y, d.y) < min(a.y, b.y)
    ) {
        return false
    }
    val d1 = cross(c, d, a)
    val d2 = cross(c, d, b)
    val d3 = cross(a, b, c)
    val d4 = cross(a, b, d)

    if (((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
        ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    ) {
        return true
    }
    return (d1 == 0f && onSegment(c, d, a)) ||
        (d2 == 0f && onSegment(c, d, b)) ||
        (d3 == 0f && onSegment(a, b, c)) ||
        (d4 == 0f && onSegment(a, b, d))
}

private fun cross(o: StrokePoint, a: StrokePoint, b: StrokePoint): Float =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

private fun onSegment(a: StrokePoint, b: StrokePoint, p: StrokePoint): Boolean {
    return p.x >= min(a.x, b.x) - 1e-4f && p.x <= max(a.x, b.x) + 1e-4f &&
        p.y >= min(a.y, b.y) - 1e-4f && p.y <= max(a.y, b.y) + 1e-4f
}

/**
 * Auto-straightens freehand lines and snaps closed shapes into clean geometric vectors.
 */
fun snapShape(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 5) return points

    val start = points.first()
    val end = points.last()
    val dx = end.x - start.x
    val dy = end.y - start.y
    val endToEndDist = sqrt(dx * dx + dy * dy)

    var totalLength = 0f
    for (i in 1 until points.size) {
        val segDx = points[i].x - points[i - 1].x
        val segDy = points[i].y - points[i - 1].y
        totalLength += sqrt(segDx * segDx + segDy * segDy)
    }

    // 1. Straight Line Snap
    if (endToEndDist > 40f && totalLength < endToEndDist * 1.14f) {
        return listOf(start, end)
    }

    // 2. Closed Shapes (Circle / Rectangle)
    if (endToEndDist < 45f && totalLength > 100f) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        val avgRadius = (width + height) / 4f
        var radiusDeviance = 0f
        for (p in points) {
            val dist = sqrt((p.x - centerX) * (p.x - centerX) + (p.y - centerY) * (p.y - centerY))
            radiusDeviance += abs(dist - avgRadius)
        }
        val avgDeviance = radiusDeviance / points.size

        if (avgDeviance < avgRadius * 0.22f) {
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
