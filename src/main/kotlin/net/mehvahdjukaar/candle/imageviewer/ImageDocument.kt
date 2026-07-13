package net.mehvahdjukaar.candle.imageviewer

import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /** Merges all visible layers into one (Photoshop's "Merge Visible"); false if fewer than two. */
    fun mergeVisibleLayers(): Boolean {
        if (layers().count { it.visible } < 2) return false
        pushUndo()
        stack.mergeVisible()
        invalidateCache()
        onLayersChanged?.invoke()
        onContentChanged?.invoke()
        return true
    }

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

    /** An independent copy of the active layer's pixels, e.g. as the base for a reversible preview. */
    fun snapshotActiveLayer(): BufferedImage = stack.activeLayer.pixels.copyArgb()

    /** Blits [base] straight back into the active layer, undoing an in-progress preview exactly. */
    fun restoreActiveLayer(base: BufferedImage) {
        val layer = stack.activeLayer.pixels
        layer.setRGB(0, 0, width, height, base.getRGB(0, 0, width, height, null, 0, width), 0, width)
        markContentChanged()
    }

    /**
     * Rewrites the active layer by applying an HSB shift to every opaque pixel of [base] (its
     * pre-adjust snapshot): hue rotated by [hueShift] turns (wrapping), saturation and brightness
     * offset by [satDelta]/[briDelta] in -1..1 then clamped. Honors the active selection; alpha is
     * preserved. Used for the whole-image Hue/Saturation/Brightness adjuster.
     */
    fun applyHsbAdjustment(base: BufferedImage, hueShift: Float, satDelta: Float, briDelta: Float) {
        val sel = selection
        val layer = stack.activeLayer.pixels
        val hsb = FloatArray(3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (sel != null && !sel.contains(x, y)) continue
                val argb = base.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    layer.setRGB(x, y, argb)
                    continue
                }
                Color.RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, hsb)
                var h = hsb[0] + hueShift
                h -= floor(h)
                val s = (hsb[1] + satDelta).coerceIn(0f, 1f)
                val b = (hsb[2] + briDelta).coerceIn(0f, 1f)
                val rgb = Color.HSBtoRGB(h, s, b) and 0xFFFFFF
                layer.setRGB(x, y, (a shl 24) or rgb)
            }
        }
        markContentChanged()
    }

    /**
     * Rewrites the active layer by applying a Photoshop-style brightness/contrast to every opaque
     * pixel of [base]: contrast pivots each channel around 128 by a factor derived from [contrast]
     * (-1..1), then [brightness] (-1..1, i.e. ±255) shifts it. Honors the active selection; alpha is
     * preserved.
     */
    fun applyBrightnessContrast(base: BufferedImage, brightness: Float, contrast: Float) {
        val sel = selection
        val layer = stack.activeLayer.pixels
        val c = contrast.coerceIn(-1f, 1f) * 255f
        val factor = (259f * (c + 255f)) / (255f * (259f - c))
        val briShift = brightness.coerceIn(-1f, 1f) * 255f
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (sel != null && !sel.contains(x, y)) continue
                val argb = base.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    layer.setRGB(x, y, argb)
                    continue
                }
                val r = adjustChannel((argb ushr 16) and 0xFF, factor, briShift)
                val g = adjustChannel((argb ushr 8) and 0xFF, factor, briShift)
                val b = adjustChannel(argb and 0xFF, factor, briShift)
                layer.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        markContentChanged()
    }

    private fun adjustChannel(v: Int, factor: Float, briShift: Float): Int =
        (factor * (v - 128f) + 128f + briShift).roundToInt().coerceIn(0, 255)

    /**
     * Overlays a two-color gradient onto every opaque pixel of [base]: linear along [angleDeg] or
     * [radial] from the region center, spanning the active selection (or the whole image) times
     * [scale], blended by [opacity] and each gradient color's own alpha. The layer's own alpha is
     * kept, so the overlay only tints existing content. Honors the active selection.
     */
    fun applyGradientOverlay(
        base: BufferedImage,
        color1: Color,
        color2: Color,
        radial: Boolean,
        angleDeg: Double,
        scale: Double,
        opacity: Float,
    ) {
        val sel = selection
        val layer = stack.activeLayer.pixels
        val region = sel ?: Rectangle(0, 0, width, height)
        val cx = region.x + region.width / 2.0
        val cy = region.y + region.height / 2.0
        val s = scale.coerceAtLeast(0.01)
        val op = opacity.coerceIn(0f, 1f)

        val ang = Math.toRadians(angleDeg)
        val dx = cos(ang)
        val dy = sin(ang)
        // Half the region's span projected onto the gradient axis (so scale 1 fills the region).
        val linHalfSpan = ((abs(region.width * dx) + abs(region.height * dy)) / 2.0).coerceAtLeast(0.5)
        val radius = (hypot(region.width.toDouble(), region.height.toDouble()) / 2.0).coerceAtLeast(0.5)

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (sel != null && !sel.contains(x, y)) continue
                val argb = base.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    layer.setRGB(x, y, argb)
                    continue
                }
                val t = if (radial) {
                    (hypot(x + 0.5 - cx, y + 0.5 - cy) / (radius * s)).coerceIn(0.0, 1.0)
                } else {
                    val proj = (x + 0.5 - cx) * dx + (y + 0.5 - cy) * dy
                    (0.5 + proj / (2.0 * linHalfSpan * s)).coerceIn(0.0, 1.0)
                }
                val eff = op * (lerp(color1.alpha, color2.alpha, t) / 255f)
                val nr = blend((argb ushr 16) and 0xFF, lerp(color1.red, color2.red, t), eff)
                val ng = blend((argb ushr 8) and 0xFF, lerp(color1.green, color2.green, t), eff)
                val nb = blend(argb and 0xFF, lerp(color1.blue, color2.blue, t), eff)
                layer.setRGB(x, y, (a shl 24) or (nr shl 16) or (ng shl 8) or nb)
            }
        }
        markContentChanged()
    }

    private fun lerp(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).roundToInt()

    private fun blend(src: Int, top: Int, alpha: Float): Int =
        (src * (1f - alpha) + top * alpha).roundToInt().coerceIn(0, 255)

    /**
     * Adds a soft outer glow around the active layer's content: the silhouette of [base] is feathered
     * outward by [radius] pixels (a blurred alpha field) and composited behind the pixels in [color],
     * scaled by [intensity] (0..1) and the color's alpha. The glow is clipped to the layer bounds.
     * Ignores the selection (it works on the whole layer silhouette).
     */
    fun applyOuterGlow(base: BufferedImage, color: Color, radius: Int, intensity: Float) {
        val layer = stack.activeLayer.pixels
        val n = width * height
        val srcArgb = IntArray(n)
        base.getRGB(0, 0, width, height, srcArgb, 0, width)
        val srcA = FloatArray(n) { ((srcArgb[it] ushr 24) and 0xFF) / 255f }

        val glow = boxBlurAlpha(srcA, width, height, radius.coerceAtLeast(1))

        val gi = intensity.coerceIn(0f, 1f)
        val gr = color.red
        val gg = color.green
        val gb = color.blue
        val gColorAlpha = color.alpha / 255f
        val out = IntArray(n)
        for (i in 0 until n) {
            val sa = srcA[i]
            val ga = (glow[i] * GLOW_GAIN).coerceIn(0f, 1f) * gi * gColorAlpha
            if (ga <= 0f) {
                out[i] = srcArgb[i]
                continue
            }
            // Source OVER glow (the glow sits behind the layer content).
            val outA = sa + ga * (1f - sa)
            if (outA <= 0f) {
                out[i] = 0
                continue
            }
            val w2 = ga * (1f - sa)
            val or = (((((srcArgb[i] ushr 16) and 0xFF) * sa + gr * w2) / outA)).roundToInt().coerceIn(0, 255)
            val og = (((((srcArgb[i] ushr 8) and 0xFF) * sa + gg * w2) / outA)).roundToInt().coerceIn(0, 255)
            val ob = ((((srcArgb[i] and 0xFF) * sa + gb * w2) / outA)).roundToInt().coerceIn(0, 255)
            val oa = (outA * 255f).roundToInt().coerceIn(0, 255)
            out[i] = (oa shl 24) or (or shl 16) or (og shl 8) or ob
        }
        layer.setRGB(0, 0, width, height, out, 0, width)
        markContentChanged()
    }

    /** Three box-blur passes over an alpha field (a fast Gaussian approximation), clamping at edges. */
    private fun boxBlurAlpha(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        var buf = src.copyOf()
        val tmp = FloatArray(src.size)
        repeat(3) {
            boxBlurH(buf, tmp, w, h, radius)
            boxBlurV(tmp, buf, w, h, radius)
        }
        return buf
    }

    private fun boxBlurH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + (x + r + 1).coerceIn(0, w - 1)] - src[row + (x - r).coerceIn(0, w - 1)]
            }
        }
    }

    private fun boxBlurV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val div = (2 * r + 1).toFloat()
        for (x in 0 until w) {
            var sum = 0f
            for (i in -r..r) sum += src[(i.coerceIn(0, h - 1)) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[(y + r + 1).coerceIn(0, h - 1) * w + x] - src[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
    }

    /**
     * Tight bounds (image space) of the active layer's non-transparent pixels, or null when the layer
     * is fully transparent. Used to arm the move/transform box around a layer's actual content rather
     * than the whole canvas.
     */
    fun activeLayerOpaqueBounds(): Rectangle? {
        val px = stack.activeLayer.pixels
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until px.height) {
            for (x in 0 until px.width) {
                if ((px.getRGB(x, y) ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        return Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
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

        // Boosts the blurred silhouette so an outer glow reads at full strength near the edge.
        private const val GLOW_GAIN = 2.2f
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
