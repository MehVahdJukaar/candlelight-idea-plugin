package net.mehvahdjukaar.candle.imageviewer

import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * The editable image model: a [LayerStack], selection, undo/redo, and pixel operations. Tools talk
 * to this class; the canvas owns view state and routes input.
 *
 * Pixel mutations fire [onContentChanged]; selection and paste-placement moves do not.
 */
class ImageDocument(source: BufferedImage) {

    private val stack = LayerStack(source)
    private var cachedComposite: BufferedImage? = null

    /** Flattened view of visible layers — used for save and palette scans. */
    val image: BufferedImage
        get() {
            cachedComposite?.let { return it }
            return stack.flatten().also { cachedComposite = it }
        }

    val layerStack: LayerStack get() = stack

    /** Active selection in image-pixel coordinates, or null for "whole image". */
    var selection: Rectangle? = null
        set(value) {
            field = value
            onSelectionChanged?.invoke()
        }

    var onContentChanged: (() -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null
    var onDimensionsChanged: (() -> Unit)? = null
    /** Fired when a paste layer moves — repaint only, not a file edit. */
    var onOverlayChanged: (() -> Unit)? = null
    var onLayersChanged: (() -> Unit)? = null

    private val undoStack = ArrayDeque<LayerStackSnapshot>()
    private val redoStack = ArrayDeque<LayerStackSnapshot>()

    val width: Int get() = stack.width
    val height: Int get() = stack.height

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val hasPendingPaste: Boolean get() = stack.floating != null

    val activeLayerIndex: Int get() = stack.activeLayerIndex

    fun layers(): List<Layer> = stack.layers()

    fun setActiveLayer(index: Int) {
        stack.setActiveLayer(index)
        onLayersChanged?.invoke()
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        stack.setLayerVisible(index, visible)
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun renameLayer(index: Int, name: String) {
        val current = layers().getOrNull(index) ?: return
        if (name.isBlank() || current.name == name.trim()) return
        pushUndo()
        stack.renameLayer(index, name)
        onLayersChanged?.invoke()
    }

    fun moveLayerUp(index: Int) {
        pushUndo()
        stack.moveLayerUp(index)
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun moveLayerDown(index: Int) {
        pushUndo()
        stack.moveLayerDown(index)
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun addEmptyLayer(): Int {
        pushUndo()
        val index = stack.addEmptyLayer()
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
        return index
    }

    fun deleteLayer(index: Int): Boolean {
        if (stack.layerCount <= 1) return false
        pushUndo()
        if (!stack.deleteLayer(index)) return false
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
        return true
    }

    fun deleteActiveLayer(): Boolean = deleteLayer(activeLayerIndex)

    fun pushUndo() {
        undoStack.addLast(stack.snapshot())
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previousSize = width to height
        redoStack.addLast(stack.snapshot())
        stack.restore(undoStack.removeLast())
        invalidateCache()
        if (width to height != previousSize) onDimensionsChanged?.invoke()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val previousSize = width to height
        undoStack.addLast(stack.snapshot())
        stack.restore(redoStack.removeLast())
        invalidateCache()
        if (width to height != previousSize) onDimensionsChanged?.invoke()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    // ---- floating paste -------------------------------------------------------------------------

    fun beginFloatingPaste(pixels: BufferedImage, x: Int, y: Int) {
        stack.beginFloating(pixels, x, y)
        onOverlayChanged?.invoke()
    }

    fun moveFloating(x: Int, y: Int) {
        stack.moveFloating(x, y)
        onOverlayChanged?.invoke()
    }

    /** Merges the floating paste into the active layer. */
    fun commitFloatingToActiveLayer(): Boolean {
        if (stack.floating == null) return false
        pushUndo()
        stack.commitFloatingToActiveLayer()
        invalidateCache()
        // The overlay is gone now: let listeners drop the paste hint as well as repaint the pixels.
        onOverlayChanged?.invoke()
        onContentChanged?.invoke()
        return true
    }

    /**
     * Promotes the selected region of the active layer into a new top layer at the same position.
     * With [cut] = true the pixels are removed from the source layer (leaving transparency); with
     * [cut] = false they are duplicated, leaving the source untouched.
     */
    fun layerFromSelection(region: Rectangle, cut: Boolean): Boolean {
        val r = region.intersection(Rectangle(0, 0, width, height))
            .takeIf { it.width > 0 && it.height > 0 } ?: return false
        pushUndo()
        val piece = stack.copyFromActiveLayer(r)
        // Clear from the source layer while it is still the active one, before adding the new layer.
        if (cut) stack.clearActiveLayer(r)
        stack.pasteAsNewLayer(piece, r.x, r.y)
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
        return true
    }

    /** Places clipboard content directly on a new top layer (Shift+paste). */
    fun pasteAsNewLayer(pixels: BufferedImage, x: Int, y: Int) {
        stack.pasteAsNewLayer(pixels, x, y)
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun cancelFloating() {
        if (stack.floating == null) return
        stack.cancelFloating()
        onOverlayChanged?.invoke()
    }

    // ---- pixel ops (active layer) ---------------------------------------------------------------

    fun colorAt(x: Int, y: Int): Color? {
        val argb = sampleComposite(x, y) ?: return null
        return Color(argb, true)
    }

    private fun sampleComposite(x: Int, y: Int): Int? {
        if (!inBounds(x, y)) return null
        stack.floating?.let { f ->
            val lx = x - f.x
            val ly = y - f.y
            if (lx in 0 until f.pixels.width && ly in 0 until f.pixels.height) {
                val argb = f.pixels.getRGB(lx, ly)
                if ((argb ushr 24) != 0) return argb
            }
        }
        for (index in stack.layers().indices.reversed()) {
            val layer = stack.layers()[index]
            if (!layer.visible) continue
            val argb = layer.pixels.getRGB(x, y)
            if ((argb ushr 24) != 0) return argb
        }
        return null
    }

    fun setPixel(x: Int, y: Int, argb: Int) {
        if (put(x, y, argb)) markContentChanged()
    }

    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) = drawBrushLine(x0, y0, x1, y1, 1, argb)

    fun stampBrush(cx: Int, cy: Int, size: Int, argb: Int) {
        if (putSquare(cx, cy, size, argb)) markContentChanged()
    }

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
        markContentChanged()
    }

    fun replaceColor(target: Int, replacement: Int) {
        if (target == replacement) return
        val sel = selection
        val layer = stack.activeLayer.pixels
        var changed = false
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (sel != null && !sel.contains(x, y)) continue
                if (layer.getRGB(x, y) == target) {
                    layer.setRGB(x, y, replacement)
                    changed = true
                }
            }
        }
        if (changed) markContentChanged()
    }

    fun copyRegion(region: Rectangle): BufferedImage = stack.copyRegion(region)

    fun liftRegion(region: Rectangle): BufferedImage {
        val lifted = stack.liftFromActiveLayer(region)
        markContentChanged()
        return lifted
    }

    fun clearRegion(region: Rectangle) {
        stack.clearActiveLayer(region)
        markContentChanged()
    }

    fun stamp(img: BufferedImage, x: Int, y: Int) {
        stack.stampOnActiveLayer(img, x, y)
        markContentChanged()
    }

    // ---- whole-image ops ------------------------------------------------------------------------

    fun replaceImage(newImage: BufferedImage) {
        val resized = newImage.width != width || newImage.height != height
        pushUndo()
        stack.replaceAll(newImage)
        selection = null
        invalidateCache()
        if (resized) onDimensionsChanged?.invoke()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun crop(region: Rectangle) {
        val w = region.width
        val h = region.height
        if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) return
        if (region.x == 0 && region.y == 0 && w == width && h == height) return
        pushUndo()
        stack.cropAll(region)
        selection = null
        invalidateCache()
        onDimensionsChanged?.invoke()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    fun resizeTo(newWidth: Int, newHeight: Int, smooth: Boolean = false) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == width && newHeight == height) return
        pushUndo()
        stack.resizeAll(newWidth, newHeight, smooth)
        selection = null
        invalidateCache()
        onDimensionsChanged?.invoke()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
    }

    // ---- internals ------------------------------------------------------------------------------

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
        stack.activeLayer.pixels.setRGB(x, y, argb)
        return true
    }

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    private fun invalidateCache() {
        cachedComposite = null
    }

    private fun markContentChanged() {
        invalidateCache()
        onContentChanged?.invoke()
    }

    companion object {
        private const val UNDO_LIMIT = 40
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
