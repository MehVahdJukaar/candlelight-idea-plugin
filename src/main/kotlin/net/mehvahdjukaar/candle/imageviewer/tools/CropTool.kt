package net.mehvahdjukaar.candle.imageviewer.tools

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.mehvahdjukaar.candle.imageviewer.CanvasRender
import net.mehvahdjukaar.candle.imageviewer.ImageDocument
import net.mehvahdjukaar.candle.imageviewer.Viewport
import net.mehvahdjukaar.candle.imageviewer.tools.CropTool.Companion.EXPAND
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Area
import javax.swing.Icon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drags out a crop rectangle (image-pixel coordinates) that can then be nudged and resized by its
 * eight handles or dragged from the inside. Press Enter or double-click to crop the image down to
 * it; Escape clears the pending rectangle. Unlike [SelectTool] the rectangle is the tool's own state
 * (not the document selection), so switching away discards it without touching a real selection.
 */
class CropTool : Tool {

    override val id = "crop"
    override val displayName = "Crop"
    override val description = "Drag a box, tweak its handles, then Enter or double-click to crop"
    override val icon: Icon = ToolIcons.CROP
    override val cursor: Cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

    private enum class Mode { NONE, NEW, MOVE, RESIZE }

    /** Pending crop rectangle in image space, or null when nothing is staged. */
    private var rect: Rectangle? = null
    private var mode = Mode.NONE

    // Gesture bookkeeping captured on press.
    private var handleX = 0 // -1 left, 0 none, 1 right
    private var handleY = 0 // -1 top, 0 none, 1 bottom
    private var pressImage: Point? = null
    private var origRect: Rectangle? = null

    /** Current image bounds (image space), cached so [paintOverlay] can shade the expanded area. */
    private var docBounds: Rectangle? = null

    override fun onActivated(document: ImageDocument) {
        docBounds = Rectangle(0, 0, document.width, document.height)
        // Swap an existing selection into our own crop box so "select, then crop" works, then clear
        // the selection so only the crop rectangle is shown.
        val sel = document.selection?.takeIf { it.width > 0 && it.height > 0 }
        rect = sel?.let { Rectangle(it) }
        mode = Mode.NONE
        if (sel != null) document.selection = null
    }

    override fun onPress(ctx: ToolContext) {
        docBounds = Rectangle(0, 0, ctx.document.width, ctx.document.height)
        val current = rect
        pressImage = ctx.imagePoint
        origRect = current
        if (current != null) {
            val handle = handleAt(current, ctx)
            if (handle != null) {
                handleX = handle.first
                handleY = handle.second
                mode = Mode.RESIZE
                return
            }
            if (ctx.viewport.toComponent(current).contains(ctx.componentPoint)) {
                mode = Mode.MOVE
                return
            }
        }
        mode = Mode.NEW
        rect = rectBetween(ctx.imagePoint, ctx.imagePoint, ctx.document)
    }

    override fun onDrag(ctx: ToolContext) {
        when (mode) {
            Mode.NEW -> rect = rectBetween(pressImage ?: return, ctx.imagePoint, ctx.document)
            Mode.MOVE -> moveBy(ctx)
            Mode.RESIZE -> resizeBy(ctx)
            Mode.NONE -> {}
        }
    }

    override fun onRelease(ctx: ToolContext) {
        // Drop a zero-area box (a bare click on empty canvas) rather than leaving a degenerate rect.
        rect = rect?.takeIf { it.width > 0 && it.height > 0 }
        mode = Mode.NONE
    }

    override fun onCommit(document: ImageDocument): Boolean {
        val r = rect ?: return false
        if (r.width <= 0 || r.height <= 0) return false
        document.crop(r)
        rect = null
        mode = Mode.NONE
        return true
    }

    override fun onCancel(document: ImageDocument): Boolean {
        if (rect == null) return false
        rect = null
        mode = Mode.NONE
        return true
    }

    // ---- gesture helpers ------------------------------------------------------------------------

    private fun moveBy(ctx: ToolContext) {
        val base = origRect ?: return
        val from = pressImage ?: return
        val dx = ctx.imagePoint.x - from.x
        val dy = ctx.imagePoint.y - from.y
        // Allow the box to travel out past the image edges (into the expandable canvas margin).
        val x = (base.x + dx).coerceIn(-EXPAND, ctx.document.width + EXPAND - base.width)
        val y = (base.y + dy).coerceIn(-EXPAND, ctx.document.height + EXPAND - base.height)
        rect = Rectangle(x, y, base.width, base.height)
    }

    private fun resizeBy(ctx: ToolContext) {
        val base = origRect ?: return
        var left = base.x
        var top = base.y
        var right = base.x + base.width
        var bottom = base.y + base.height
        val p = clampToCanvas(ctx.imagePoint, ctx.document)
        if (handleX < 0) left = p.x else if (handleX > 0) right = p.x
        if (handleY < 0) top = p.y else if (handleY > 0) bottom = p.y
        rect = Rectangle(min(left, right), min(top, bottom), abs(right - left), abs(top - bottom))
    }

    /** Which handle of [r] (component space) sits under the cursor, as (x,y) in -1/0/1, or null. */
    private fun handleAt(r: Rectangle, ctx: ToolContext): Pair<Int, Int>? {
        val c = ctx.viewport.toComponent(r)
        val tol = JBUI.scale(HANDLE_HIT)
        for (hy in -1..1) {
            for (hx in -1..1) {
                if (hx == 0 && hy == 0) continue
                val hp = handlePoint(c, hx, hy)
                if (abs(ctx.componentPoint.x - hp.x) <= tol && abs(ctx.componentPoint.y - hp.y) <= tol) {
                    return hx to hy
                }
            }
        }
        return null
    }

    private fun handlePoint(c: Rectangle, hx: Int, hy: Int): Point = Point(
        c.x + (c.width * (hx + 1)) / 2,
        c.y + (c.height * (hy + 1)) / 2,
    )

    private fun rectBetween(a: Point, b: Point, document: ImageDocument): Rectangle {
        val pa = clampToCanvas(a, document)
        val pb = clampToCanvas(b, document)
        return Rectangle(min(pa.x, pb.x), min(pa.y, pb.y), abs(pb.x - pa.x), abs(pb.y - pa.y))
    }

    /**
     * Clamps an image point to the reachable pixel-boundary range. It extends [EXPAND] past each edge
     * so the box can be dragged outside the current image to grow the canvas, not just crop into it.
     */
    private fun clampToCanvas(p: Point, document: ImageDocument): Point = Point(
        p.x.coerceIn(-EXPAND, document.width + EXPAND),
        p.y.coerceIn(-EXPAND, document.height + EXPAND),
    )

    // ---- painting -------------------------------------------------------------------------------

    override fun paintOverlay(g: Graphics2D, viewport: Viewport) {
        val r = rect ?: return
        val c = viewport.toComponent(r)
        val bounds = g.clipBounds ?: return

        // Dim everything outside the crop box so the kept region stands out.
        g.color = SHADE
        fillOutside(g, bounds, c)

        // Where the box reaches past the current image, preview the transparent padding it will add.
        drawExpansionHint(g, viewport, c, bounds)

        CanvasRender.selectionOutline(g, c)
        drawHandles(g, c)
        drawSizeLabel(g, c, r)
    }

    /** Paints a transparency checkerboard over the part of the crop box that lies beyond the image. */
    private fun drawExpansionHint(g: Graphics2D, viewport: Viewport, c: Rectangle, bounds: Rectangle) {
        val image = docBounds?.let { viewport.toComponent(it) } ?: return
        val visible = c.intersection(bounds)
        if (visible.isEmpty) return
        val expansion = Area(visible).apply { subtract(Area(image)) }
        if (expansion.isEmpty) return
        val cg = g.create() as Graphics2D
        try {
            cg.clip(expansion)
            CanvasRender.checkerboard(cg, visible)
        } finally {
            cg.dispose()
        }
    }

    private fun fillOutside(g: Graphics2D, bounds: Rectangle, c: Rectangle) {
        val x0 = bounds.x
        val y0 = bounds.y
        val x1 = bounds.x + bounds.width
        val y1 = bounds.y + bounds.height
        val cl = max(c.x, x0)
        val cr = min(c.x + c.width, x1)
        val ct = max(c.y, y0)
        val cb = min(c.y + c.height, y1)
        g.fillRect(x0, y0, bounds.width, (ct - y0).coerceAtLeast(0))          // above
        g.fillRect(x0, cb, bounds.width, (y1 - cb).coerceAtLeast(0))          // below
        g.fillRect(x0, ct, (cl - x0).coerceAtLeast(0), (cb - ct).coerceAtLeast(0)) // left
        g.fillRect(cr, ct, (x1 - cr).coerceAtLeast(0), (cb - ct).coerceAtLeast(0)) // right
    }

    private fun drawHandles(g: Graphics2D, c: Rectangle) {
        val s = JBUI.scale(HANDLE_SIZE)
        for (hy in -1..1) {
            for (hx in -1..1) {
                if (hx == 0 && hy == 0) continue
                val hp = handlePoint(c, hx, hy)
                g.color = JBColor.white
                g.fillRect(hp.x - s / 2, hp.y - s / 2, s, s)
                g.color = JBColor.black
                g.drawRect(hp.x - s / 2, hp.y - s / 2, s, s)
            }
        }
    }

    private fun drawSizeLabel(g: Graphics2D, c: Rectangle, r: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = UIUtil.getLabelFont()
        val text = "${r.width}×${r.height}"
        val fm = g.fontMetrics
        val pad = JBUI.scale(4)
        val w = fm.stringWidth(text) + pad * 2
        val h = fm.height
        val x = c.x
        val y = (c.y - h - JBUI.scale(2)).coerceAtLeast(0)
        g.color = Color(0, 0, 0, 150)
        g.fillRoundRect(x, y, w, h, JBUI.scale(4), JBUI.scale(4))
        g.color = Color.WHITE
        g.drawString(text, x + pad, y + fm.ascent)
    }

    companion object {
        // Handle hit radius and drawn size, in unscaled px (run through JBUI.scale at use).
        private const val HANDLE_HIT = 8
        private const val HANDLE_SIZE = 7
        private val SHADE = Color(0, 0, 0, 120)

        // How far (in image pixels) past each edge the crop box may be dragged to grow the canvas.
        private const val EXPAND = 4096
    }
}
