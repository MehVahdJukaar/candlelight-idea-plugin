package net.mehvahdjukaar.candle.imageviewer

import java.awt.Point
import java.awt.Rectangle
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * View transform for the canvas: the zoom level and the pixel offset of the image's top-left corner
 * within the component, plus conversions between component space and image space.
 *
 * Pure view state — it knows nothing about the image contents or the editing tools.
 */
class Viewport {

    var zoom: Double = 1.0
        private set
    var offsetX: Double = 0.0
        private set
    var offsetY: Double = 0.0
        private set

    /** True once the user has manually zoomed/panned, so we stop auto-fitting on resize. */
    var userInteracted: Boolean = false
        private set

    /** Scales [imageW]x[imageH] to fit within [componentW]x[componentH] and centers it. */
    fun fit(componentW: Int, componentH: Int, imageW: Int, imageH: Int) {
        val cw = componentW.coerceAtLeast(1)
        val ch = componentH.coerceAtLeast(1)
        zoom = min(cw.toDouble() / imageW, ch.toDouble() / imageH)
        offsetX = (cw - imageW * zoom) / 2.0
        offsetY = (ch - imageH * zoom) / 2.0
        userInteracted = false
    }

    /** Multiplies zoom by [factor], keeping the point ([px], [py]) in component space fixed. */
    fun zoomAt(px: Double, py: Double, factor: Double) {
        val clamped = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = clamped / zoom
        offsetX = px - (px - offsetX) * applied
        offsetY = py - (py - offsetY) * applied
        zoom = clamped
        userInteracted = true
    }

    fun setZoom(target: Double, centerX: Double, centerY: Double) = zoomAt(centerX, centerY, target / zoom)

    fun pan(dx: Double, dy: Double) {
        offsetX += dx
        offsetY += dy
        userInteracted = true
    }

    /** Re-centers the image at the current zoom level within [componentW]x[componentH]. */
    fun center(componentW: Int, componentH: Int, imageW: Int, imageH: Int) {
        offsetX = (componentW - imageW * zoom) / 2.0
        offsetY = (componentH - imageH * zoom) / 2.0
        userInteracted = true
    }

    /**
     * Keeps the image from being panned or zoomed entirely out of view by ensuring at least [margin]
     * pixels of it always remain on-screen (or re-centers the axis if it can't fit the margin).
     */
    fun clamp(componentW: Int, componentH: Int, imageW: Int, imageH: Int, margin: Int) {
        offsetX = clampAxis(offsetX, imageW * zoom, componentW, margin)
        offsetY = clampAxis(offsetY, imageH * zoom, componentH, margin)
    }

    private fun clampAxis(offset: Double, imageSize: Double, componentSize: Int, margin: Int): Double {
        val m = margin.toDouble()
        val min = m - imageSize
        val max = componentSize - m
        return if (min <= max) offset.coerceIn(min, max) else (componentSize - imageSize) / 2.0
    }

    /** Component coordinate -> image pixel coordinate (floored). May be out of the image bounds. */
    fun toImage(px: Int, py: Int): Point =
        Point(floor((px - offsetX) / zoom).toInt(), floor((py - offsetY) / zoom).toInt())

    /** The image's bounding box in component space. */
    fun imageRect(imageW: Int, imageH: Int): Rectangle = toComponent(Rectangle(0, 0, imageW, imageH))

    /** Maps a rectangle in image space to component space. */
    fun toComponent(r: Rectangle): Rectangle = Rectangle(
        (offsetX + r.x * zoom).roundToInt(),
        (offsetY + r.y * zoom).roundToInt(),
        (r.width * zoom).roundToInt().coerceAtLeast(1),
        (r.height * zoom).roundToInt().coerceAtLeast(1),
    )

    companion object {
        private const val MIN_ZOOM = 0.02
        private const val MAX_ZOOM = 64.0
    }
}
