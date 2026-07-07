package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Builds and caches custom mouse cursors for the editing tools, reusing the glyph routines from
 * [ToolIcons].
 */
object ToolCursors {

    private data class GridHotspot(val x: Float, val y: Float)

    private val cache = HashMap<String, Cursor>()

    private val crosshair: Cursor get() = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

    fun eyedropper(): Cursor = cursor("img-eyedropper", GridHotspot(3f, 13f), ::drawEyedropperGlyph)
    fun pencil(): Cursor = cursor("img-pencil", GridHotspot(3.5f, 12.5f), ::drawPencilGlyph)
    fun eraser(): Cursor = cursor("img-eraser", GridHotspot(6.5f, 11.5f), ::drawEraserGlyph)
    fun zoom(): Cursor = cursor("img-zoom", GridHotspot(7f, 7f), ::drawZoomGlyph)
    fun hand(): Cursor = cursor("img-hand", GridHotspot(8f, 9f), ::drawHandGlyph)
    fun dot(): Cursor = cursor("img-dot", GridHotspot(8f, 8f), ::drawDotGlyph)
    fun bucket(): Cursor = cursor("img-bucket", GridHotspot(7.5f, 13f), ::drawBucketGlyph)
    fun rotate(): Cursor = cursor("img-rotate", GridHotspot(8f, 8f), ::drawRotateGlyph)

    /** Predefined directional resize cursor for a transform handle at ([hx], [hy]) in -1/0/1. */
    fun resize(hx: Int, hy: Int): Cursor = Cursor.getPredefinedCursor(
        when {
            hx < 0 && hy < 0 -> Cursor.NW_RESIZE_CURSOR
            hx > 0 && hy < 0 -> Cursor.NE_RESIZE_CURSOR
            hx < 0 && hy > 0 -> Cursor.SW_RESIZE_CURSOR
            hx > 0 && hy > 0 -> Cursor.SE_RESIZE_CURSOR
            hy < 0 -> Cursor.N_RESIZE_CURSOR
            hy > 0 -> Cursor.S_RESIZE_CURSOR
            hx < 0 -> Cursor.W_RESIZE_CURSOR
            else -> Cursor.E_RESIZE_CURSOR
        },
    )

    /**
     * Fully transparent cursor for tools that draw their own on-canvas pointer. Sized to the
     * platform's preferred cursor dimensions so Linux/Wayland actually hides the OS glyph.
     */
    fun blank(): Cursor = cache.getOrPut("img-blank") {
        try {
            val toolkit = Toolkit.getDefaultToolkit()
            val best = toolkit.getBestCursorSize(32, 32)
            val w = best.width.coerceAtLeast(1)
            val h = best.height.coerceAtLeast(1)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            toolkit.createCustomCursor(image, java.awt.Point(w / 2, h / 2), "img-blank") ?: crosshair
        } catch (_: Throwable) {
            crosshair
        }
    }

    private fun cursor(name: String, hotspot: GridHotspot, paint: (Graphics2D) -> Unit): Cursor =
        cache.getOrPut(name) { build(name, hotspot, paint) }

    private fun build(name: String, hotspot: GridHotspot, paint: (Graphics2D) -> Unit): Cursor {
        return try {
            val toolkit = Toolkit.getDefaultToolkit()
            val best = toolkit.getBestCursorSize(GLYPH_GRID * 2, GLYPH_GRID * 2)
            val size = min(best.width, best.height)
            if (size <= 0) return crosshair

            val image = BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            try {
                setupGlyphGraphics(g, size)
                paint(g)
            } finally {
                g.dispose()
            }

            val scale = size.toFloat() / GLYPH_GRID
            val hx = (hotspot.x * scale).roundToInt().coerceIn(0, best.width - 1)
            val hy = (hotspot.y * scale).roundToInt().coerceIn(0, best.height - 1)

            toolkit.createCustomCursor(image, java.awt.Point(hx, hy), name) ?: crosshair
        } catch (_: Throwable) {
            crosshair
        }
    }
}
