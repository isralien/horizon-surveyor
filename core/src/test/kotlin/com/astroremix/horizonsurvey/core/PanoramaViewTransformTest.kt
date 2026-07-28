package com.astroremix.horizonsurvey.core

import com.astroremix.horizonsurvey.core.PanoramaViewTransform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanoramaViewTransformTest {

    @Test
    fun `fitHeight scales content height to exactly fill the view height`() {
        val t = PanoramaViewTransform.fitHeight(contentWidth = 2160f, contentHeight = 400f, viewWidth = 1080f, viewHeight = 800f)
        assertEquals(2f, t.scale, 1e-6f) // 800 / 400
        assertEquals(0f, t.translateY, 1e-6f)
    }

    @Test
    fun `view-to-content and back round-trips exactly`() {
        val t = Transform(scale = 2.5f, translateX = -300f, translateY = 40f)
        val viewX = 517f
        val viewY = 88f
        val contentX = PanoramaViewTransform.viewToContentX(viewX, t)
        val contentY = PanoramaViewTransform.viewToContentY(viewY, t)
        assertEquals(viewX, PanoramaViewTransform.contentToViewX(contentX, t), 1e-3f)
        assertEquals(viewY, PanoramaViewTransform.contentToViewY(contentY, t), 1e-3f)
    }

    @Test
    fun `clamp centers content smaller than the viewport on that axis`() {
        val t = PanoramaViewTransform.clamp(
            Transform(scale = 1f, translateX = 999f, translateY = 999f),
            contentWidth = 100f, contentHeight = 100f,
            viewWidth = 400f, viewHeight = 400f,
            minScale = 1f, maxScale = 4f,
        )
        assertEquals(150f, t.translateX, 1e-6f) // (400 - 100) / 2
        assertEquals(150f, t.translateY, 1e-6f)
    }

    @Test
    fun `clamp keeps content covering the viewport when larger than it`() {
        // Content wider than the view: translateX must never reveal empty space on either edge.
        val t = PanoramaViewTransform.clamp(
            Transform(scale = 1f, translateX = 500f, translateY = 0f), // way past the left edge
            contentWidth = 2000f, contentHeight = 400f,
            viewWidth = 1000f, viewHeight = 400f,
            minScale = 1f, maxScale = 4f,
        )
        assertEquals(0f, t.translateX, 1e-6f) // clamped to the left edge, not left dangling at +500

        val t2 = PanoramaViewTransform.clamp(
            Transform(scale = 1f, translateX = -5000f, translateY = 0f), // way past the right edge
            contentWidth = 2000f, contentHeight = 400f,
            viewWidth = 1000f, viewHeight = 400f,
            minScale = 1f, maxScale = 4f,
        )
        assertEquals(-1000f, t2.translateX, 1e-6f) // clamped so the right edge lands at the view's right edge
    }

    @Test
    fun `clamp restricts scale to the given range`() {
        val tooSmall = PanoramaViewTransform.clamp(
            Transform(scale = 0.1f, translateX = 0f, translateY = 0f),
            contentWidth = 100f, contentHeight = 100f, viewWidth = 400f, viewHeight = 400f,
            minScale = 1f, maxScale = 4f,
        )
        assertEquals(1f, tooSmall.scale, 1e-6f)

        val tooBig = PanoramaViewTransform.clamp(
            Transform(scale = 99f, translateX = 0f, translateY = 0f),
            contentWidth = 100f, contentHeight = 100f, viewWidth = 400f, viewHeight = 400f,
            minScale = 1f, maxScale = 4f,
        )
        assertEquals(4f, tooBig.scale, 1e-6f)
    }

    @Test
    fun `fitHeight allows zooming in beyond the fitted scale`() {
        val t = PanoramaViewTransform.fitHeight(contentWidth = 2160f, contentHeight = 400f, viewWidth = 1080f, viewHeight = 800f)
        assertTrue(t.scale < PanoramaViewTransform.clamp(
            Transform(99f, 0f, 0f), 2160f, 400f, 1080f, 800f, t.scale, t.scale * 6f,
        ).scale)
    }
}
