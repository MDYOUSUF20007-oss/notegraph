package com.notegraph.app

import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.StrokePoint
import com.notegraph.app.ui.editor.computeStrokeSetBounds
import com.notegraph.app.ui.editor.isStrokeInsideLasso
import com.notegraph.app.ui.editor.scaleSelectedStrokes
import com.notegraph.app.ui.editor.translateSelectedStrokes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkLassoUtilsTest {

    private fun sampleStrokes(): List<InkStroke> = listOf(
        InkStroke(
            points = listOf(
                StrokePoint(100f, 100f),
                StrokePoint(140f, 120f)
            )
        ),
        InkStroke(
            points = listOf(
                StrokePoint(250f, 250f),
                StrokePoint(290f, 280f)
            )
        )
    )

    @Test
    fun `translateSelectedStrokes moves freely up and left as well as down and right`() {
        val strokes = sampleStrokes()

        // Move up-left (negative dx and dy)
        val movedUpLeft = translateSelectedStrokes(
            strokes = strokes,
            selectedIndices = setOf(0),
            dx = -35f,
            dy = -50f,
            canvasWidth = 500f,
            canvasHeight = 500f
        )

        assertEquals(65f, movedUpLeft[0].points[0].x, 0.001f)
        assertEquals(50f, movedUpLeft[0].points[0].y, 0.001f)
        assertEquals(105f, movedUpLeft[0].points[1].x, 0.001f)
        assertEquals(70f, movedUpLeft[0].points[1].y, 0.001f)

        // Unselected stroke remains untouched
        assertEquals(strokes[1], movedUpLeft[1])

        // Move down-left
        val movedDownLeft = translateSelectedStrokes(
            strokes = movedUpLeft,
            selectedIndices = setOf(0),
            dx = -15f,
            dy = 30f,
            canvasWidth = 500f,
            canvasHeight = 500f
        )
        assertEquals(50f, movedDownLeft[0].points[0].x, 0.001f)
        assertEquals(80f, movedDownLeft[0].points[0].y, 0.001f)
    }

    @Test
    fun `translateSelectedStrokes clamps uniformly at canvas edges without distorting stroke shape`() {
        val strokes = sampleStrokes()

        // Push far past top-left origin
        val clampedTopLeft = translateSelectedStrokes(
            strokes = strokes,
            selectedIndices = setOf(0),
            dx = -500f,
            dy = -500f,
            canvasWidth = 500f,
            canvasHeight = 500f
        )

        val bounds = computeStrokeSetBounds(clampedTopLeft, setOf(0))
        assertNotNull(bounds)
        assertEquals(0f, bounds!!.minX, 0.001f)
        assertEquals(0f, bounds.minY, 0.001f)
        // Original width (40f) and height (20f) are preserved
        assertEquals(40f, bounds.width, 0.001f)
        assertEquals(20f, bounds.height, 0.001f)
    }

    @Test
    fun `isStrokeInsideLasso detects two-point snapped line whose endpoints sit outside the loop`() {
        val longLine = InkStroke(
            points = listOf(
                StrokePoint(20f, 150f),
                StrokePoint(280f, 150f)
            )
        )
        // Lasso encloses only the middle of the horizontal line (x: 100..200, y: 100..200)
        val centerLoop = listOf(
            StrokePoint(100f, 100f),
            StrokePoint(200f, 100f),
            StrokePoint(200f, 200f),
            StrokePoint(100f, 200f)
        )
        assertTrue(isStrokeInsideLasso(longLine, centerLoop))

        val farAwayLine = InkStroke(
            points = listOf(
                StrokePoint(20f, 40f),
                StrokePoint(280f, 40f)
            )
        )
        assertFalse(isStrokeInsideLasso(farAwayLine, centerLoop))
    }

    @Test
    fun `scaleSelectedStrokes scales around centroid while keeping unselected strokes unchanged`() {
        val strokes = sampleStrokes()
        val beforeBounds = computeStrokeSetBounds(strokes, setOf(0))!!

        val scaled = scaleSelectedStrokes(strokes, setOf(0), 1.5f)
        val afterBounds = computeStrokeSetBounds(scaled, setOf(0))!!

        assertEquals(beforeBounds.centerX, afterBounds.centerX, 0.001f)
        assertEquals(beforeBounds.centerY, afterBounds.centerY, 0.001f)
        assertEquals(beforeBounds.width * 1.5f, afterBounds.width, 0.001f)
        assertEquals(beforeBounds.height * 1.5f, afterBounds.height, 0.001f)
        assertEquals(strokes[1], scaled[1])
    }
}
