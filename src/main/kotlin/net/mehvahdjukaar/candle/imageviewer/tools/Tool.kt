package net.mehvahdjukaar.candle.imageviewer.tools

import net.mehvahdjukaar.candle.imageviewer.ImageDocument
import net.mehvahdjukaar.candle.imageviewer.Viewport
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import javax.swing.Icon

/**
 * Per-event state handed to a [Tool]: the model, the view transform, the mouse position already
 * mapped to image-pixel coordinates, and callbacks for reading/writing the active color.
 */
class ToolContext(
    val document: ImageDocument,
    val viewport: Viewport,
    val imagePoint: Point,
    val componentPoint: Point,
    val altDown: Boolean,
    val shiftDown: Boolean,
    val ctrlDown: Boolean,
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

    /** Toolbar glyph for this tool (programmatically drawn; see [ToolIcon]). */
    val icon: Icon

    val cursor: Cursor get() = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

    /**
     * True if holding Alt should temporarily switch to the eyedropper while this tool is active,
     * Photoshop-style. Set on color-painting tools (pencil, eraser, recolor).
     */
    val altPicksColor: Boolean get() = false

    /**
     * True if the OS cursor should be hidden while this tool is active over the canvas, because the
     * tool already draws its own pointer (e.g. the brush outline from [paintHover]).
     */
    val hidesCursor: Boolean get() = false

    /**
     * Called when this tool becomes the active one, so it can seed transient state from the document
     * (e.g. the crop tool adopting the current selection as its starting rectangle). Not called for
     * the initially-selected tool at startup.
     */
    fun onActivated(document: ImageDocument) {}

    /**
     * Called when this tool stops being the active one, so it can bake or drop any staged gesture
     * (e.g. the transform tool commits its floating buffer rather than leaving lifted pixels behind).
     */
    fun onDeactivated(document: ImageDocument) {}

    fun onPress(ctx: ToolContext) {}
    fun onDrag(ctx: ToolContext) {}
    fun onRelease(ctx: ToolContext) {}

    /**
     * Applies any pending gesture (e.g. the crop tool commits its rectangle). Called on Enter or a
     * double-click. Returns true if something was committed, so the caller can repaint.
     */
    fun onCommit(document: ImageDocument): Boolean = false

    /**
     * Discards any pending gesture (e.g. the crop tool clears its rectangle). Called on Escape before
     * the canvas falls back to clearing the selection. Returns true if something was cancelled.
     */
    fun onCancel(document: ImageDocument): Boolean = false

    /** Optional transient overlay drawn on top of the image (component-space graphics). */
    fun paintOverlay(g: Graphics2D, viewport: Viewport) {}

    /**
     * Optional preview drawn at the hovered image pixel ([hover]), e.g. the pencil's brush outline.
     * Called with the mouse position mapped to image-pixel coordinates while it is over the canvas.
     */
    fun paintHover(g: Graphics2D, viewport: Viewport, hover: Point) {}
}
