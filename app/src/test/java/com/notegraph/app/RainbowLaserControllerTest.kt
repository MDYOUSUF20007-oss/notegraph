package com.notegraph.app

import com.notegraph.app.ui.editor.RainbowLaserController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainbowLaserControllerTest {

    @Test
    fun `spatial decimation skips sub-threshold micro-jitter and accepts spaced points`() {
        val controller = RainbowLaserController(minSpacing = 6f, ttlMs = 1250L)
        controller.beginStroke(100f, 100f, nowMs = 1000L)

        // Sub-threshold moves (< 6px Euclidean distance) should be ignored
        assertFalse(controller.addSample(102f, 102f, nowMs = 1010L))
        assertFalse(controller.addSample(104f, 104f, nowMs = 1020L))
        assertEquals(1, controller.activePointCount)

        // Move >= 6px should be accepted
        assertTrue(controller.addSample(110f, 100f, nowMs = 1030L))
        assertEquals(2, controller.activePointCount)
    }

    @Test
    fun `rolling TTL window prunes aged tail samples while pen stays held down`() {
        val controller = RainbowLaserController(minSpacing = 6f, ttlMs = 1250L, maxActivePoints = 140)
        controller.beginStroke(0f, 0f, nowMs = 0L)
        controller.addSample(20f, 0f, nowMs = 300L)
        controller.addSample(40f, 0f, nowMs = 600L)
        controller.addSample(60f, 0f, nowMs = 1400L) // cutoff = 1400 - 1250 = 150 -> t=0 pruned!
        controller.addSample(80f, 0f, nowMs = 2000L) // cutoff = 750 -> t=300 & t=600 pruned!

        val remaining = controller.snapshotActive()
        assertEquals(2, remaining.size)
        assertEquals(1400L, remaining[0].timestampMs)
        assertEquals(2000L, remaining[1].timestampMs)
    }

    @Test
    fun `maxActivePoints enforces strict O(1) memory bound during long continuous strokes`() {
        val controller = RainbowLaserController(minSpacing = 5f, ttlMs = 60_000L, maxActivePoints = 20)
        controller.beginStroke(0f, 0f, nowMs = 1000L)
        for (i in 1..50) {
            controller.addSample(i * 10f, 0f, nowMs = 1000L + i * 10L)
        }

        assertEquals(20, controller.activePointCount)
        val snap = controller.snapshotActive()
        assertEquals(500f, snap.last().x, 0.001f)
    }

    @Test
    fun `maxCompletedTrails caps rapid tap-and-flick strokes`() {
        val controller = RainbowLaserController(minSpacing = 4f, ttlMs = 1250L, maxCompletedTrails = 3)
        for (i in 1..6) {
            val baseT = 1000L + i * 20L
            controller.beginStroke(i * 20f, 0f, baseT)
            controller.addSample(i * 20f, 30f, baseT + 5L)
            controller.endStroke(baseT + 10L)
        }

        assertEquals(0, controller.activePointCount)
        assertEquals(3, controller.snapshotCompleted().size)
        // Oldest 3 trails dropped, newest 3 retained
        assertEquals(80f, controller.snapshotCompleted().first().first().x, 0.001f)
        assertEquals(120f, controller.snapshotCompleted().last().first().x, 0.001f)
    }

    @Test
    fun `pruneExpired empties trails after TTL so animation loop goes idle at zero CPU`() {
        val controller = RainbowLaserController(minSpacing = 4f, ttlMs = 1250L)
        controller.beginStroke(10f, 10f, 1000L)
        controller.addSample(40f, 20f, 1100L)
        controller.endStroke(1120L)

        assertTrue(controller.hasAnyContent)
        assertTrue(controller.pruneExpired(nowMs = 1800L))
        assertEquals(2, controller.totalPointCount)

        // Past t=1100 + 1250 = 2350 -> all samples expired
        assertFalse(controller.pruneExpired(nowMs = 2400L))
        assertFalse(controller.hasAnyContent)
        assertEquals(0, controller.totalPointCount)
    }

    @Test
    fun `single-point stroke and cancelActive do not pollute completed trails`() {
        val controller = RainbowLaserController()
        controller.beginStroke(50f, 50f, 1000L)
        controller.endStroke(1010L)
        assertFalse(controller.hasAnyContent)

        controller.beginStroke(50f, 50f, 2000L)
        controller.addSample(90f, 50f, 2050L)
        assertTrue(controller.hasAnyContent)
        controller.cancelActive()
        assertFalse(controller.hasAnyContent)
    }
}
