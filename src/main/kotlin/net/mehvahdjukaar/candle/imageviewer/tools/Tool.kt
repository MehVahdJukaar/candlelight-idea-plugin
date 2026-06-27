package net.mehvahdjukaar.candle.imageviewer.tools

import net.mehvahdjukaar.candle.imageviewer.ImageDocument
import net.mehvahdjukaar.candle.imageviewer.Viewport
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point

/**
 * Per-event state handed to a [Tool]: the model, the view transform, the mouse position already
 * mapped to image-pixel coordinates, and callbacks for reading/writing the active color.
 */
class ToolContext(
    val document: ImageDocument,
    val viewport: Viewport,
    val imagePoint: Point,
    val color: Color,
    val setColor: (Color) -> Unit,
)

/**
 * An editing tool. One instance lives for the canvas's lifetime, so a tool may keep transient state
 * (e.g. the last drawn point, or a floating selection) between press/drag/release.
 *
 * Add a new tool by implementing this interface and registering it in the canvas's tool list.
 */
interface Tool {
    /** Stable id, also used for persistence/selection. */
    val id: String

    /** Short label shown on the palette button. */
    val displayName: String

    /** Tooltip describing what the tool does. */
    val description: String

    val cursor: Cursor get() = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

    fun onPress(ctx: ToolContext) {}
    fun onDrag(ctx: ToolContext) {}
    fun onRelease(ctx: ToolContext) {}

    /** Optional transient overlay drawn on top of the image (component-space graphics). */
    fun paintOverlay(g: Graphics2D, viewport: Viewport) {}
}
