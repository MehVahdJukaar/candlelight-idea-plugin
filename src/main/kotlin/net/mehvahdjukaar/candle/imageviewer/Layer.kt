package net.mehvahdjukaar.candle.imageviewer

import java.awt.image.BufferedImage

/**
 * One raster layer in a [LayerStack].
 */
class Layer(
    var name: String,
    val pixels: BufferedImage,
    var visible: Boolean = true,
    var opacity: Float = 1f,
)

/** Temporary paste preview hovering above the stack — not shown in the layer list. */
class FloatingPlacement(
    val pixels: BufferedImage,
    var x: Int,
    var y: Int,
)

/** Full stack snapshot for undo/redo. */
internal data class LayerStackSnapshot(
    val layers: List<LayerSnapshot>,
    val activeLayerIndex: Int,
    val floating: FloatingPlacement?,
)

internal data class LayerSnapshot(
    val name: String,
    val pixels: BufferedImage,
    val visible: Boolean,
    val opacity: Float,
)
