package net.mehvahdjukaar.candle.imageviewer

import java.awt.Rectangle
import java.awt.image.BufferedImage

/**
 * Ordered stack of [Layer]s plus an optional [floating] overlay for in-progress paste placement.
 */
class LayerStack(source: BufferedImage) {

    private val layers = mutableListOf(Layer("Background", source.toArgb()))

    var activeLayerIndex: Int = 0
        private set

    /** Uncommitted paste preview — not part of [layers]. */
    var floating: FloatingPlacement? = null
        private set

    val layerCount: Int get() = layers.size
    val activeLayer: Layer get() = layers[activeLayerIndex.coerceIn(0, layers.lastIndex)]

    val width: Int get() = layers.first().pixels.width
    val height: Int get() = layers.first().pixels.height

    fun layers(): List<Layer> = layers.toList()

    fun setActiveLayer(index: Int) {
        if (layers.isEmpty()) return
        activeLayerIndex = index.coerceIn(0, layers.lastIndex)
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        if (index !in layers.indices) return
        layers[index].visible = visible
    }

    fun renameLayer(index: Int, name: String) {
        if (index !in layers.indices) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        layers[index].name = trimmed
    }

    fun moveLayer(from: Int, to: Int) {
        if (from !in layers.indices || to !in layers.indices || from == to) return
        val layer = layers.removeAt(from)
        layers.add(to, layer)
        activeLayerIndex = when {
            activeLayerIndex == from -> to
            from < activeLayerIndex && to >= activeLayerIndex -> activeLayerIndex - 1
            from > activeLayerIndex && to <= activeLayerIndex -> activeLayerIndex + 1
            else -> activeLayerIndex
        }
    }

    /** Moves one step toward the front (top) of the stack. */
    fun moveLayerUp(index: Int) {
        if (index !in layers.indices || index >= layers.lastIndex) return
        moveLayer(index, index + 1)
    }

    /** Moves one step toward the back (bottom) of the stack. */
    fun moveLayerDown(index: Int) {
        if (index !in 1 until layers.size) return
        moveLayer(index, index - 1)
    }

    fun addEmptyLayer(name: String = nextLayerName()): Int {
        val blank = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        layers.add(Layer(name, blank))
        activeLayerIndex = layers.lastIndex
        return activeLayerIndex
    }

    fun deleteLayer(index: Int): Boolean {
        if (layers.size <= 1 || index !in layers.indices) return false
        layers.removeAt(index)
        if (activeLayerIndex >= layers.size) activeLayerIndex = layers.lastIndex
        else if (activeLayerIndex > index) activeLayerIndex--
        return true
    }

    fun flatten(includeFloating: Boolean = false): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            for (layer in layers) {
                if (layer.visible) drawImage(layer.pixels, 0, 0, null)
            }
            if (includeFloating) floating?.let { f -> drawImage(f.pixels, f.x, f.y, null) }
            dispose()
        }
        return out
    }

    fun copyRegion(region: Rectangle): BufferedImage {
        val flat = flatten(includeFloating = true)
        val r = region.intersection(Rectangle(0, 0, width, height))
        val out = BufferedImage(r.width.coerceAtLeast(1), r.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(flat, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null)
            dispose()
        }
        return out
    }

    fun beginFloating(pixels: BufferedImage, x: Int, y: Int) {
        cancelFloating()
        floating = FloatingPlacement(pixels.copyArgb(), x, y)
    }

    fun moveFloating(x: Int, y: Int) {
        val f = floating ?: return
        f.x = x
        f.y = y
    }

    /** Merges the floating paste into the currently active layer. */
    fun commitFloatingToActiveLayer() {
        val f = floating ?: return
        activeLayer.pixels.createGraphics().apply {
            drawImage(f.pixels, f.x, f.y, null)
            dispose()
        }
        floating = null
    }

    /** Creates a new top layer with [tight] at ([x], [y]) and makes it active. */
    fun pasteAsNewLayer(tight: BufferedImage, x: Int, y: Int): Layer {
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        canvas.createGraphics().apply {
            drawImage(tight, x, y, null)
            dispose()
        }
        val layer = Layer(nextLayerName("Paste"), canvas)
        layers.add(layer)
        activeLayerIndex = layers.lastIndex
        return layer
    }

    fun cancelFloating() {
        floating = null
    }

    fun liftFromActiveLayer(region: Rectangle): BufferedImage {
        val lifted = copyFromActiveLayer(region)
        clearActiveLayer(region)
        return lifted
    }

    fun copyFromActiveLayer(region: Rectangle): BufferedImage {
        val src = activeLayer.pixels
        val r = region.intersection(Rectangle(0, 0, width, height))
        val out = BufferedImage(r.width.coerceAtLeast(1), r.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(src, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null)
            dispose()
        }
        return out
    }

    fun clearActiveLayer(region: Rectangle) {
        val r = region.intersection(Rectangle(0, 0, width, height))
        if (r.isEmpty) return
        activeLayer.pixels.createGraphics().apply {
            composite = java.awt.AlphaComposite.Clear
            fillRect(r.x, r.y, r.width, r.height)
            dispose()
        }
    }

    fun stampOnActiveLayer(img: BufferedImage, x: Int, y: Int) {
        activeLayer.pixels.createGraphics().apply {
            drawImage(img, x, y, null)
            dispose()
        }
    }

    fun replaceAll(image: BufferedImage) {
        layers.clear()
        layers.add(Layer("Background", image.toArgb()))
        activeLayerIndex = 0
        floating = null
    }

    fun resizeAll(newWidth: Int, newHeight: Int, smooth: Boolean) {
        replaceAll(resizeImage(flatten(includeFloating = false), newWidth, newHeight, smooth))
    }

    fun cropAll(region: Rectangle) {
        val w = region.width
        val h = region.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(flatten(includeFloating = false), -region.x, -region.y, null)
            dispose()
        }
        replaceAll(out)
    }

    internal fun snapshot(): LayerStackSnapshot = LayerStackSnapshot(
        layers = layers.map { LayerSnapshot(it.name, it.pixels.copyArgb(), it.visible, it.opacity) },
        activeLayerIndex = activeLayerIndex,
        floating = floating?.let { FloatingPlacement(it.pixels.copyArgb(), it.x, it.y) },
    )

    internal fun restore(snapshot: LayerStackSnapshot) {
        layers.clear()
        for (entry in snapshot.layers) {
            layers.add(Layer(entry.name, entry.pixels.copyArgb(), entry.visible, entry.opacity))
        }
        activeLayerIndex = snapshot.activeLayerIndex.coerceIn(0, layers.lastIndex)
        floating = snapshot.floating?.let { FloatingPlacement(it.pixels.copyArgb(), it.x, it.y) }
    }

    private fun nextLayerName(preferred: String = "Layer"): String {
        if (layers.none { it.name == preferred }) return preferred
        var n = 2
        while (layers.any { it.name == "$preferred $n" }) n++
        return "$preferred $n"
    }

    private fun resizeImage(source: BufferedImage, newWidth: Int, newHeight: Int, smooth: Boolean): BufferedImage {
        val out = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                if (smooth) java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                else java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            drawImage(source, 0, 0, newWidth, newHeight, null)
            dispose()
        }
        return out
    }
}
