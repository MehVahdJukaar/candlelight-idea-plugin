package net.mehvahdjukaar.candle.imageviewer.tools

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.CanvasRender
import net.mehvahdjukaar.candle.imageviewer.ImageDocument
import net.mehvahdjukaar.candle.imageviewer.Viewport
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Free-transform: lifts the selection (or whole layer) into a floating buffer that can be stretched
 * by its eight handles and rotated in 90° steps by dragging the ring just outside a corner
 * (Photoshop-style). The gesture is staged — press Enter or double-click to bake it into the layer,
 * Escape to drop it back where it started. Switching to another tool bakes any pending transform.
 *
 * Because rotation is quantised to 90°, the transformed pixels stay axis-aligned, so the whole
 * gesture is described by a destination rectangle (position + non-uniform scale) plus a rotation
 * quadrant, and everything renders with nearest-neighbour sampling to stay crisp for pixel art.
 */
class TransformTool : Tool {

    override val id = "transform"
    override val displayName = "Transform"
    override val description = "Stretch the selection with its handles; drag outside a corner to rotate 90°"
    override val icon: Icon = ToolIcons.TRANSFORM
    override val cursor: Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    private enum class Mode { NONE, MOVE, RESIZE, ROTATE }

    /** The lifted pixels in their original orientation, or null when nothing is staged. */
    private var floating: BufferedImage? = null

    /** Destination box in image space: where (and at what size) the transformed pixels land. */
    private var dest = Rectangle()

    /** Number of clockwise 90° turns applied to [floating]; 0..3. */
    private var quadrant = 0

    /** Original region, kept so Escape can drop the pixels back untouched. */
    private var origRegion = Rectangle()

    // Gesture bookkeeping captured on press.
    private var mode = Mode.NONE
    private var handleX = 0 // -1 left, 0 none, 1 right
    private var handleY = 0 // -1 top, 0 none, 1 bottom
    private var pressImage = Point()
    private var origDest = Rectangle()
    private var baseQuadrant = 0
    private var centerX = 0.0
    private var centerY = 0.0

    // Cache of [floating] rotated to the current quadrant, so we don't rotate every repaint.
    private var rotatedCache: BufferedImage? = null
    private var cachedQuadrant = -1

    override fun onActivated(document: ImageDocument) {
        // Selecting the tool arms a transform box over the current selection (or the whole layer),
        // so "select, then transform" flows straight through on the same region — Photoshop's Ctrl+T.
        clearState()
        arm(document)
    }

    /** Stage a transform box over the shared selection (or whole layer) without lifting pixels yet. */
    private fun arm(document: ImageDocument) {
        val region = (document.selection ?: Rectangle(0, 0, document.width, document.height))
            .intersection(Rectangle(0, 0, document.width, document.height))
        if (region.isEmpty) return
        origRegion = Rectangle(region)
        dest = Rectangle(region)
        quadrant = 0
    }

    override fun onPress(ctx: ToolContext) {
        if (dest.isEmpty) arm(ctx.document) // in case the tool was the startup tool and never activated
        if (dest.isEmpty) return
        val m = resolveMode(ctx)
        if (m == Mode.NONE) return // pressed away from the box: leave it armed, do nothing
        if (floating == null) {
            // First real manipulation: lift the pixels now (destructive, so snapshot for undo first).
            ctx.document.pushUndo()
            floating = ctx.document.liftRegion(origRegion)
            ctx.document.selection = null
        }
        mode = m
        pressImage = ctx.imagePoint
        origDest = Rectangle(dest)
        baseQuadrant = quadrant
        centerX = dest.x + dest.width / 2.0
        centerY = dest.y + dest.height / 2.0
    }

    override fun onDrag(ctx: ToolContext) {
        if (floating == null) return
        when (mode) {
            Mode.MOVE -> moveBy(ctx)
            Mode.RESIZE -> resizeBy(ctx)
            Mode.ROTATE -> rotateBy(ctx)
            Mode.NONE -> {}
        }
    }

    override fun onRelease(ctx: ToolContext) {
        mode = Mode.NONE
    }

    override fun onCommit(document: ImageDocument): Boolean {
        val f = floating ?: run {
            // Only an armed box (nothing lifted or changed): just dismiss it, keeping the selection.
            if (dest.isEmpty) return false
            clearState()
            return true
        }
        val rotated = rotated(f)
        val w = dest.width.coerceAtLeast(1)
        val h = dest.height.coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            drawImage(rotated, 0, 0, w, h, null)
            dispose()
        }
        document.stamp(out, dest.x, dest.y)
        document.selection = Rectangle(dest.x, dest.y, w, h)
            .intersection(Rectangle(0, 0, document.width, document.height))
            .takeIf { it.width > 0 && it.height > 0 }
        clearState()
        return true
    }

    override fun onCancel(document: ImageDocument): Boolean {
        val f = floating
        if (f != null) {
            // Put the untouched pixels back exactly where they came from, and restore the selection.
            document.stamp(f, origRegion.x, origRegion.y)
            document.selection = Rectangle(origRegion).takeIf { it.width > 0 && it.height > 0 }
            clearState()
            return true
        }
        // Only an armed box: drop it (the selection stays, since we never cleared it).
        if (dest.isEmpty) return false
        clearState()
        return true
    }

    override fun onDeactivated(document: ImageDocument) {
        // Leaving the tool with lifted pixels bakes them; an armed-only box just disappears.
        if (floating != null) onCommit(document) else clearState()
    }

    // ---- gesture helpers ------------------------------------------------------------------------

    private fun moveBy(ctx: ToolContext) {
        val dx = ctx.imagePoint.x - pressImage.x
        val dy = ctx.imagePoint.y - pressImage.y
        dest = Rectangle(origDest.x + dx, origDest.y + dy, origDest.width, origDest.height)
    }

    private fun resizeBy(ctx: ToolContext) {
        var left = origDest.x
        var top = origDest.y
        var right = origDest.x + origDest.width
        var bottom = origDest.y + origDest.height
        val p = ctx.imagePoint
        if (handleX < 0) left = p.x else if (handleX > 0) right = p.x
        if (handleY < 0) top = p.y else if (handleY > 0) bottom = p.y
        dest = Rectangle(
            min(left, right),
            min(top, bottom),
            abs(right - left).coerceAtLeast(1),
            abs(bottom - top).coerceAtLeast(1),
        )
    }

    private fun rotateBy(ctx: ToolContext) {
        val a0 = atan2(pressImage.y - centerY, pressImage.x - centerX)
        val a1 = atan2(ctx.imagePoint.y - centerY, ctx.imagePoint.x - centerX)
        val steps = Math.round(Math.toDegrees(a1 - a0) / 90.0).toInt()
        quadrant = ((baseQuadrant + steps) % 4 + 4) % 4
        // Every odd quarter-turn swaps the box's width and height about its fixed centre.
        val swap = steps % 2 != 0
        val w = if (swap) origDest.height else origDest.width
        val h = if (swap) origDest.width else origDest.height
        dest = Rectangle((centerX - w / 2.0).roundToInt(), (centerY - h / 2.0).roundToInt(), w, h)
    }

    /** Which gesture a press at [ctx] starts, given an already-staged box. */
    private fun resolveMode(ctx: ToolContext): Mode {
        val c = ctx.viewport.toComponent(dest)
        handleAt(c, ctx)?.let { (hx, hy) ->
            // Dragging a corner while holding a modifier rotates (snapped to 90°); Photoshop also
            // rotates from the ring just outside the box, handled below.
            if (hx != 0 && hy != 0 && (ctx.ctrlDown || ctx.shiftDown)) return Mode.ROTATE
            handleX = hx
            handleY = hy
            return Mode.RESIZE
        }
        if (c.contains(ctx.componentPoint)) return Mode.MOVE
        if (distanceToRect(c, ctx.componentPoint) <= JBUI.scale(ROTATE_RING)) return Mode.ROTATE
        return Mode.NONE
    }

    /** Which handle of [c] (component space) sits under the cursor, as (x,y) in -1/0/1, or null. */
    private fun handleAt(c: Rectangle, ctx: ToolContext): Pair<Int, Int>? {
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

    private fun distanceToRect(c: Rectangle, p: Point): Double {
        val dx = max(max(c.x - p.x, p.x - (c.x + c.width)), 0)
        val dy = max(max(c.y - p.y, p.y - (c.y + c.height)), 0)
        return hypot(dx.toDouble(), dy.toDouble())
    }

    /** [floating] rotated to the current [quadrant], cached until the quadrant changes. */
    private fun rotated(f: BufferedImage): BufferedImage {
        if (rotatedCache == null || cachedQuadrant != quadrant) {
            rotatedCache = rotate90(f, quadrant)
            cachedQuadrant = quadrant
        }
        return rotatedCache!!
    }

    private fun clearState() {
        floating = null
        dest = Rectangle()
        quadrant = 0
        mode = Mode.NONE
        rotatedCache = null
        cachedQuadrant = -1
    }

    // ---- painting -------------------------------------------------------------------------------

    override fun paintOverlay(g: Graphics2D, viewport: Viewport) {
        if (dest.isEmpty) return
        val c = viewport.toComponent(dest)
        // Once pixels are lifted, draw the transformed floating buffer; while merely armed, the box
        // sits over the still-present image, so only the outline and handles are drawn.
        floating?.let {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(rotated(it), c.x, c.y, c.width, c.height, null)
        }
        CanvasRender.selectionOutline(g, c)
        drawHandles(g, c)
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

    companion object {
        // Handle hit radius and drawn size, in unscaled px (run through JBUI.scale at use).
        private const val HANDLE_HIT = 8
        private const val HANDLE_SIZE = 7

        // How far (unscaled px) outside the box a drag still counts as a rotation gesture.
        private const val ROTATE_RING = 32

        /** Lossless 90°·[quadrant] clockwise rotation of [src] (nearest-neighbour, exact pixel remap). */
        private fun rotate90(src: BufferedImage, quadrant: Int): BufferedImage {
            val q = (quadrant % 4 + 4) % 4
            if (q == 0) return src
            val w = src.width
            val h = src.height
            val swap = q % 2 != 0
            val out = BufferedImage(if (swap) h else w, if (swap) w else h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val rgb = src.getRGB(x, y)
                    when (q) {
                        1 -> out.setRGB(h - 1 - y, x, rgb)          // 90° CW
                        2 -> out.setRGB(w - 1 - x, h - 1 - y, rgb)  // 180°
                        else -> out.setRGB(y, w - 1 - x, rgb)       // 270° CW
                    }
                }
            }
            return out
        }
    }
}
