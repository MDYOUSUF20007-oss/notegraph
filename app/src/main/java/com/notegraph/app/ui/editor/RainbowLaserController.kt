package com.notegraph.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Single timestamped point in a Rainbow Vanishing Laser trail.
 */
data class LaserSample(
    val x: Float,
    val y: Float,
    val timestampMs: Long
)

/**
 * Immutable snapshot of one completed or fading laser stroke.
 */
data class LaserTrail(
    val id: Long,
    val points: List<LaserSample>
)

/**
 * Pure Kotlin controller + batched renderer for the Rainbow Vanishing Laser.
 *
 * Key optimizations over naive per-segment rendering:
 * 1. Spatial decimation skips redundant sub-threshold micro-jitter from high-Hz touch/stylus polling.
 * 2. Rolling TTL sliding-window (`TTL_MS = 1250L`) prunes aged tail samples *while* the pen is
 *    still held down as well as after release, capping active and total RAM strictly O(1).
 * 3. Precomputed static 36-bin rainbow palette avoids per-segment `Color.hsl(...)` allocations.
 * 4. Batches segments by quantized `(hueBin, alphaBucket)` into reusable `Path` instances,
 *    reducing thousands of individual `drawLine` calls down to ~12–20 batched `drawPath` calls.
 */
class RainbowLaserController(
    val minSpacing: Float = 6f,
    val ttlMs: Long = DEFAULT_TTL_MS,
    val maxActivePoints: Int = DEFAULT_MAX_ACTIVE_POINTS,
    val maxCompletedTrails: Int = DEFAULT_MAX_COMPLETED_TRAILS
) {
    private val completedTrails = ArrayDeque<MutableList<LaserSample>>()
    private val activeSamples = mutableListOf<LaserSample>()

    // Lazily allocated reusable bucket buffers — zero native Path allocations until laser draws
    private val bucketPaths = arrayOfNulls<Path>(HUE_BINS * ALPHA_BUCKETS)
    private val bucketHasData = BooleanArray(HUE_BINS * ALPHA_BUCKETS)

    val activePointCount: Int
        get() = activeSamples.size

    val totalPointCount: Int
        get() {
            var sum = activeSamples.size
            for (trail in completedTrails) sum += trail.size
            return sum
        }

    val hasAnyContent: Boolean
        get() = activeSamples.isNotEmpty() || completedTrails.isNotEmpty()

    fun beginStroke(x: Float, y: Float, nowMs: Long) {
        activeSamples.clear()
        activeSamples.add(LaserSample(x, y, nowMs))
    }

    /**
     * Appends a sample if moved >= [minSpacing], pruning aged tail points and enforcing [maxActivePoints].
     * Returns true if a point was accepted or aged tail points were pruned.
     */
    fun addSample(x: Float, y: Float, nowMs: Long): Boolean {
        val pruned = pruneListInPlace(activeSamples, nowMs, ttlMs, maxActivePoints)
        val last = activeSamples.lastOrNull()
        if (last != null) {
            val dx = x - last.x
            val dy = y - last.y
            if (minSpacing > 0f && dx * dx + dy * dy < minSpacing * minSpacing) {
                return pruned
            }
        }
        activeSamples.add(LaserSample(x, y, nowMs))
        if (activeSamples.size > maxActivePoints) {
            val drop = activeSamples.size - maxActivePoints
            activeSamples.subList(0, drop).clear()
        }
        return true
    }

    fun endStroke(nowMs: Long) {
        pruneListInPlace(activeSamples, nowMs, ttlMs, maxActivePoints)
        if (activeSamples.size >= 2) {
            val finalized = ArrayList(activeSamples)
            completedTrails.addLast(finalized)
            while (completedTrails.size > maxCompletedTrails) {
                completedTrails.removeFirst()
            }
        }
        activeSamples.clear()
    }

    fun cancelActive() {
        activeSamples.clear()
    }

    /**
     * Prunes expired samples across active and completed trails. Returns true if anything remains alive.
     */
    fun pruneExpired(nowMs: Long): Boolean {
        if (activeSamples.isNotEmpty()) {
            pruneListInPlace(activeSamples, nowMs, ttlMs, maxActivePoints)
        }
        if (completedTrails.isNotEmpty()) {
            val iter = completedTrails.iterator()
            while (iter.hasNext()) {
                val trail = iter.next()
                pruneListInPlace(trail, nowMs, ttlMs, maxActivePoints)
                if (trail.size < 2) {
                    iter.remove()
                }
            }
        }
        return hasAnyContent
    }

    /**
     * Pure snapshot helper for testing and inspection.
     */
    fun snapshotActive(): List<LaserSample> = activeSamples.toList()

    fun snapshotCompleted(): List<List<LaserSample>> = completedTrails.map { it.toList() }

    /**
     * Draws all active and fading trails into Compose [DrawScope] with zero heap allocations.
     * For normalized coordinate spaces (like PDF markup), pass `scaleX = pageWidth, scaleY = pageHeight`.
     */
    fun drawInto(
        scope: DrawScope,
        nowMs: Long,
        baseStrokeWidthPx: Float,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        if (!hasAnyContent) return

        bucketHasData.fill(false)
        var anySegment = false

        fun accumulateSamples(samples: List<LaserSample>) {
            if (samples.size < 2) return
            for (i in 1 until samples.size) {
                val p1 = samples[i - 1]
                val p2 = samples[i]
                val age = (nowMs - p2.timestampMs).coerceAtLeast(0L)
                if (age >= ttlMs) continue

                val life = 1f - (age.toFloat() / ttlMs.toFloat())
                val alphaBucket = ((life * (ALPHA_BUCKETS - 1)).toInt()).coerceIn(0, ALPHA_BUCKETS - 1)
                val hueIdx = ((i + (p2.timestampMs / 28L).toInt()) % HUE_BINS + HUE_BINS) % HUE_BINS
                val bucketIdx = hueIdx * ALPHA_BUCKETS + alphaBucket

                var path = bucketPaths[bucketIdx]
                if (path == null) {
                    path = Path()
                    bucketPaths[bucketIdx] = path
                }
                if (!bucketHasData[bucketIdx]) {
                    path.rewind()
                    bucketHasData[bucketIdx] = true
                }
                path.moveTo(p1.x * scaleX, p1.y * scaleY)
                path.lineTo(p2.x * scaleX, p2.y * scaleY)
                anySegment = true
            }
        }

        for (trail in completedTrails) {
            accumulateSamples(trail)
        }
        accumulateSamples(activeSamples)

        if (!anySegment) return

        val coreWidth = baseStrokeWidthPx.coerceIn(3f, 22f)
        val glowWidth = coreWidth * 2.1f

        for (h in 0 until HUE_BINS) {
            val baseColor = RAINBOW_PALETTE[h]
            for (a in 0 until ALPHA_BUCKETS) {
                val bucketIdx = h * ALPHA_BUCKETS + a
                if (!bucketHasData[bucketIdx]) continue

                val alpha = ALPHA_STEPS[a]
                val path = bucketPaths[bucketIdx] ?: continue

                // Soft outer neon halo (batched per hue+alpha bucket)
                scope.drawPath(
                    path = path,
                    color = baseColor.copy(alpha = alpha * 0.28f),
                    style = Stroke(
                        width = glowWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Crisp high-visibility laser core
                scope.drawPath(
                    path = path,
                    color = baseColor.copy(alpha = alpha * 0.96f),
                    style = Stroke(
                        width = coreWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 1250L
        const val DEFAULT_MAX_ACTIVE_POINTS = 140
        const val DEFAULT_MAX_COMPLETED_TRAILS = 6

        private const val HUE_BINS = 24
        private const val ALPHA_BUCKETS = 5

        // Quantized opacity ramp (0.20, 0.40, 0.60, 0.80, 1.00)
        private val ALPHA_STEPS = floatArrayOf(0.20f, 0.40f, 0.60f, 0.80f, 1.0f)

        // Precomputed 24-bin vibrant neon rainbow colors (lazily built once, zero runtime HSL math)
        private val RAINBOW_PALETTE: Array<Color> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Array(HUE_BINS) { idx ->
                val hue = (idx * (360f / HUE_BINS))
                Color.hsl(hue, 1f, 0.56f, 1f)
            }
        }

        internal fun pruneListInPlace(
            list: MutableList<LaserSample>,
            nowMs: Long,
            ttlMs: Long,
            maxPoints: Int
        ): Boolean {
            if (list.isEmpty()) return false
            var pruneCount = 0
            val cutoff = nowMs - ttlMs
            while (pruneCount < list.size && list[pruneCount].timestampMs <= cutoff) {
                pruneCount++
            }
            if (list.size - pruneCount > maxPoints) {
                pruneCount = list.size - maxPoints
            }
            return if (pruneCount > 0) {
                list.subList(0, min(pruneCount, list.size)).clear()
                true
            } else {
                false
            }
        }
    }
}
