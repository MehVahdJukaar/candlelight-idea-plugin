package net.mehvahdjukaar.candle.imageviewer

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
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
        set(value) {
            field = value
            onSelectionChanged?.invoke()
        }

    /** Invoked whenever the pixels change (draw, erase, move, undo, redo) — i.e. a real edit. */
    var onContentChanged: (() -> Unit)? = null

    /** Invoked whenever the selection changes (not a file edit), so the UI can refresh copy/cut state. */
    var onSelectionChanged: (() -> Unit)? = null

    /** Invoked whenever the image's width/height changes (crop/resize, or an undo/redo of one). */
    var onDimensionsChanged: (() -> Unit)? = null

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
        val previous = image
        redoStack.addLast(image.copyArgb())
        image = undoStack.removeLast()
        if (sizeChanged(previous)) onDimensionsChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val previous = image
        undoStack.addLast(image.copyArgb())
        image = redoStack.removeLast()
        if (sizeChanged(previous)) onDimensionsChanged?.invoke()
        onContentChanged?.invoke()
    }

    private fun sizeChanged(previous: BufferedImage): Boolean =
        previous.width != image.width || previous.height != image.height

    // ---- pixel ops ------------------------------------------------------------------------------

    fun colorAt(x: Int, y: Int): Color? =
        if (inBounds(x, y)) Color(image.getRGB(x, y), true) else null

    fun setPixel(x: Int, y: Int, argb: Int) {
        if (put(x, y, argb)) onContentChanged?.invoke()
    }

    /** Plots a line between two image-space points (Bresenham), respecting the selection. */
    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) = drawBrushLine(x0, y0, x1, y1, 1, argb)

    /** Stamps a [size]x[size] square of [argb] centered on ([cx], [cy]), respecting the selection. */
    fun stampBrush(cx: Int, cy: Int, size: Int, argb: Int) {
        if (putSquare(cx, cy, size, argb)) onContentChanged?.invoke()
    }

    /** Plots a line (Bresenham) stamping a [size]x[size] square brush at every step. */
    fun drawBrushLine(x0: Int, y0: Int, x1: Int, y1: Int, size: Int, argb: Int) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x)
        val dy = -abs(y1 - y)
        val sx = if (x < x1) 1 else -1
        val sy = if (y < y1) 1 else -1
        var err = dx + dy
        while (true) {
            putSquare(x, y, size, argb)
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

    /** Returns an independent ARGB copy of [region] (clamped to the image) without modifying the document. */
    fun copyRegion(region: Rectangle): BufferedImage {
        val r = region.intersection(Rectangle(0, 0, width, height))
        val out = BufferedImage(r.width.coerceAtLeast(1), r.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(image, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null)
            dispose()
        }
        return out
    }

    /** Copies a region into a standalone image and clears it from the document (for Move/Cut). */
    fun liftRegion(region: Rectangle): BufferedImage {
        val lifted = copyRegion(region)
        clearRegion(region)
        return lifted
    }

    /** Clears [region] to transparency (eraser), clamped to the image bounds. */
    fun clearRegion(region: Rectangle) {
        val r = region.intersection(Rectangle(0, 0, width, height))
        if (r.isEmpty) return
        image.createGraphics().apply {
            composite = AlphaComposite.Clear
            fillRect(r.x, r.y, r.width, r.height)
            dispose()
        }
        onContentChanged?.invoke()
    }

    /** Composites [img] onto the document at ([x], [y]). */
    fun stamp(img: BufferedImage, x: Int, y: Int) {
        image.createGraphics().apply {
            drawImage(img, x, y, null)
            dispose()
        }
        onContentChanged?.invoke()
    }

    // ---- whole-image ops ------------------------------------------------------------------------

    /**
     * Replaces the whole pixel buffer with [newImage] (any size), recording undo and clearing the
     * selection. Fires [onDimensionsChanged] when the size differs. Backing store for crop/resize.
     */
    fun replaceImage(newImage: BufferedImage) {
        val next = newImage.toArgb()
        val resized = next.width != width || next.height != height
        pushUndo()
        image = next
        selection = null
        if (resized) onDimensionsChanged?.invoke()
        onContentChanged?.invoke()
    }

    /**
     * Re-frames the image to [region] (image-pixel coordinates), keeping pixels 1:1. The region may
     * extend outside the current bounds to *grow* the canvas — the area beyond the old image becomes
     * transparent padding — or sit inside it to crop. A no-op if it matches the current bounds.
     */
    fun crop(region: Rectangle) {
        val w = region.width
        val h = region.height
        if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) return
        if (region.x == 0 && region.y == 0 && w == width && h == height) return
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(image, -region.x, -region.y, null)
            dispose()
        }
        replaceImage(out)
    }

    /** Scales the whole image to [newWidth]x[newHeight], nearest-neighbour unless [smooth]. */
    fun resizeTo(newWidth: Int, newHeight: Int, smooth: Boolean = false) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == width && newHeight == height) return
        val out = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                if (smooth) RenderingHints.VALUE_INTERPOLATION_BILINEAR
                else RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            drawImage(image, 0, 0, newWidth, newHeight, null)
            dispose()
        }
        replaceImage(out)
    }

    /** Writes a [size]x[size] square of [argb] centered on ([cx], [cy]); returns true if any pixel was set. */
    private fun putSquare(cx: Int, cy: Int, size: Int, argb: Int): Boolean {
        if (size <= 1) return put(cx, cy, argb)
        val half = (size - 1) / 2
        var changed = false
        for (oy in 0 until size) {
            for (ox in 0 until size) {
                if (put(cx - half + ox, cy - half + oy, argb)) changed = true
            }
        }
        return changed
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

        /** Hard ceiling on a crop/resize dimension, guarding against a runaway allocation. */
        const val MAX_DIMENSION = 16384
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
