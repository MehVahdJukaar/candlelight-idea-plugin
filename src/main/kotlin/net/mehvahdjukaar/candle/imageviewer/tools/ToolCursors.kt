package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Builds and caches custom mouse cursors for the editing tools, reusing the glyph routines from
 * [ToolIcons].
 *
 * Cursor creation is environment-sensitive (the platform may only support certain sizes, or none in
 * a headless context), so every build is wrapped in a try/catch that falls back to a predefined
 * cursor and never throws. Built cursors are cached, since constructing a [BufferedImage] and
 * calling the toolkit on every property access would be wasteful.
 */
object ToolCursors {

    /** A hotspot expressed in the [GLYPH_GRID] authoring grid, later scaled to the cursor size. */
    private data class GridHotspot(val x: Float, val y: Float)

    private val cache = HashMap<String, Cursor>()

    private val crosshair: Cursor get() = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

    /** Eyedropper cursor, hotspot at the dropper tip (bottom-left of the glyph). */
    fun eyedropper(): Cursor =
        cursor("img-eyedropper", GridHotspot(3f, 13f), ::drawEyedropperGlyph)

    /** Pencil cursor, hotspot at the drawing tip (bottom-left of the glyph). */
    fun pencil(): Cursor =
        cursor("img-pencil", GridHotspot(3.5f, 12.5f), ::drawPencilGlyph)

    /** Eraser cursor, hotspot at the working tip (bottom-left of the glyph). */
    fun eraser(): Cursor =
        cursor("img-eraser", GridHotspot(6.5f, 11.5f), ::drawEraserGlyph)

    /** Zoom cursor, hotspot at the magnifier lens center. */
    fun zoom(): Cursor =
        cursor("img-zoom", GridHotspot(7f, 7f), ::drawZoomGlyph)

    /** Hand (pan) cursor, hotspot at the palm center. */
    fun hand(): Cursor =
        cursor("img-hand", GridHotspot(8f, 9f), ::drawHandGlyph)

    private fun cursor(name: String, hotspot: GridHotspot, paint: (Graphics2D) -> Unit): Cursor =
        cache.getOrPut(name) { build(name, hotspot, paint) }

    private fun build(name: String, hotspot: GridHotspot, paint: (Graphics2D) -> Unit): Cursor {
        return try {
            val toolkit = Toolkit.getDefaultToolkit()
            val best = toolkit.getBestCursorSize(GLYPH_GRID * 2, GLYPH_GRID * 2)
            val size = min(best.width, best.height)
            if (size <= 0) return crosshair // headless / unsupported

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

            val cursor = toolkit.createCustomCursor(image, Point(hx, hy), name)
            cursor ?: crosshair
        } catch (_: Throwable) {
            crosshair
        }
    }
}
