package net.mehvahdjukaar.candle.imageviewer

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * The editable image model: the pixel buffer (always ARGB), the current selection, an undo/redo
 * history, and the primitive mutation operations. It has no notion of view transform or tools.
 *
 * Pixel mutations fire [onContentChanged]; selection changes do not (selecting is not a file edit).
 */
class ImageDocument(source: BufferedImage) {

    var image: BufferedImage = source.toArgb()
        private set

    /** Active selection in image-pixel coordinates, or null for "whole image". */
    var selection: Rectangle? = null

    /** Invoked whenever the pixels change (draw, erase, move, undo, redo) — i.e. a real edit. */
    var onContentChanged: (() -> Unit)? = null

    private val undoStack = ArrayDeque<BufferedImage>()
    private val redoStack = ArrayDeque<BufferedImage>()

    val width: Int get() = image.width
    val height: Int get() = image.height

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    // ---- history --------------------------------------------------------------------------------

    /** Records the current state so the next mutation can be undone. Call before a stroke/move. */
    fun pushUndo() {
        undoStack.addLast(image.copyArgb())
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(image.copyArgb())
        image = undoStack.removeLast()
        onContentChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(image.copyArgb())
        image = redoStack.removeLast()
        onContentChanged?.invoke()
    }

    // ---- pixel ops ------------------------------------------------------------------------------

    fun colorAt(x: Int, y: Int): Color? =
        if (inBounds(x, y)) Color(image.getRGB(x, y), true) else null

    fun setPixel(x: Int, y: Int, argb: Int) {
        if (put(x, y, argb)) onContentChanged?.invoke()
    }

    /** Plots a line between two image-space points (Bresenham), respecting the selection. */
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x)
        val dy = -abs(y1 - y)
        val sx = if (x < x1) 1 else -1
        val sy = if (y < y1) 1 else -1
        var err = dx + dy
        while (true) {
            put(x, y, argb)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
        onContentChanged?.invoke()
    }

    /** Replaces every pixel whose exact ARGB equals [target] with [replacement], within the selection. */
    fun replaceColor(target: Int, replacement: Int) {
        if (target == replacement) return
        val sel = selection
        var changed = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (sel != null && !sel.contains(x, y)) continue
                if (image.getRGB(x, y) == target) {
                    image.setRGB(x, y, replacement)
                    changed = true
                }
            }
        }
        if (changed) onContentChanged?.invoke()
    }

    /** Copies a region into a standalone image and clears it from the document (for Move). */
    fun liftRegion(region: Rectangle): BufferedImage {
        val r = region.intersection(Rectangle(0, 0, width, height))
        val lifted = BufferedImage(r.width.coerceAtLeast(1), r.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        lifted.createGraphics().apply {
            drawImage(image, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null)
            dispose()
        }
        image.createGraphics().apply {
            composite = AlphaComposite.Clear
            fillRect(r.x, r.y, r.width, r.height)
            dispose()
        }
        onContentChanged?.invoke()
        return lifted
    }

    /** Composites [img] onto the document at ([x], [y]). */
    fun stamp(img: BufferedImage, x: Int, y: Int) {
        image.createGraphics().apply {
            drawImage(img, x, y, null)
            dispose()
        }
        onContentChanged?.invoke()
    }

    private fun put(x: Int, y: Int, argb: Int): Boolean {
        if (!inBounds(x, y)) return false
        val sel = selection
        if (sel != null && !sel.contains(x, y)) return false
        image.setRGB(x, y, argb)
        return true
    }

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    companion object {
        private const val UNDO_LIMIT = 40
    }
}

/** Returns an independent ARGB copy of this image. */
internal fun BufferedImage.copyArgb(): BufferedImage = toArgb()

/** Returns a copy of this image in [BufferedImage.TYPE_INT_ARGB] so it is mutable with alpha. */
internal fun BufferedImage.toArgb(): BufferedImage {
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        drawImage(this@toArgb, 0, 0, null)
        dispose()
    }
    return out
}
