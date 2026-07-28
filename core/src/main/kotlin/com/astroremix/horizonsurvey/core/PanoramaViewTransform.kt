package com.astroremix.horizonsurvey.core

/**
 * Pure scale/pan math for a zoomable panorama view: a simple uniform-scale
 * affine transform (no rotation), kept as plain floats rather than
 * android.graphics.Matrix so the clamping and coordinate-conversion logic is
 * unit-testable without an Android runtime.
 */
object PanoramaViewTransform {

    data class Transform(val scale: Float, val translateX: Float, val translateY: Float)

    /**
     * Clamps [transform] so scale stays within [minScale, maxScale] and the
     * content never pans past its own edges (or, if smaller than the
     * viewport on an axis, stays centered on that axis).
     */
    fun clamp(
        transform: Transform,
        contentWidth: Float,
        contentHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
        minScale: Float,
        maxScale: Float,
    ): Transform {
        val scale = transform.scale.coerceIn(minScale, maxScale)
        val scaledWidth = contentWidth * scale
        val scaledHeight = contentHeight * scale

        val tx = if (scaledWidth <= viewWidth) {
            (viewWidth - scaledWidth) / 2f
        } else {
            transform.translateX.coerceIn(viewWidth - scaledWidth, 0f)
        }
        val ty = if (scaledHeight <= viewHeight) {
            (viewHeight - scaledHeight) / 2f
        } else {
            transform.translateY.coerceIn(viewHeight - scaledHeight, 0f)
        }
        return Transform(scale, tx, ty)
    }

    /** A transform that fits [contentHeight] exactly to [viewHeight], anchored at the content's left edge. */
    fun fitHeight(contentWidth: Float, contentHeight: Float, viewWidth: Float, viewHeight: Float): Transform {
        val scale = if (contentHeight > 0f) viewHeight / contentHeight else 1f
        return clamp(
            Transform(scale, 0f, 0f),
            contentWidth, contentHeight, viewWidth, viewHeight,
            minScale = scale, maxScale = scale.coerceAtLeast(1f) * MAX_ZOOM_MULTIPLIER,
        )
    }

    fun viewToContentX(viewX: Float, transform: Transform): Float = (viewX - transform.translateX) / transform.scale
    fun viewToContentY(viewY: Float, transform: Transform): Float = (viewY - transform.translateY) / transform.scale

    fun contentToViewX(contentX: Float, transform: Transform): Float = contentX * transform.scale + transform.translateX
    fun contentToViewY(contentY: Float, transform: Transform): Float = contentY * transform.scale + transform.translateY

    private const val MAX_ZOOM_MULTIPLIER = 6f
}
