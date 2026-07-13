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

    // Keeps the "move" id so the canvas's paste-placement integration (which keys off it) still works;
    // this single tool now moves, stretches and rotates — Move and Transform merged into one.
    override val id = "move"
    override val displayName = "Transform"
    override val description = "Move, stretch or rotate the selection or layer; modifier+corner rotates 90°"
    override val icon: Icon = ToolIcons.TRANSFORM
    override val cursor: Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    private enum class Mode { NONE, MOVE, RESIZE, ROTATE }

    /** The lifted pixels in their original orientation, or null when nothing is staged. */
    private var floating: BufferedImage? = null

    /** Destination box in image space: where (and at what size) the transformed pixels land. */
    private var dest = Rectangle()

    /** Number of clockwise 90° turns applied to [floating]; 0..3. */
    private var quadrant = 0

    /** Whether the transformed pixels are mirrored, in the final on-screen (dest) orientation. */
    private var flipH = false
    private var flipV = false

    /** Original region, kept so Escape can drop the pixels back untouched. */
    private var origRegion = Rectangle()

    // Gesture bookkeeping captured on press.
    private var mode = Mode.NONE
    private var handleX = 0 // -1 left, 0 none, 1 right
    private var handleY = 0 // -1 top, 0 none, 1 bottom
    private var pressImage = Point()
    private var origDest = Rectangle()
    private var baseQuadrant = 0
    private var baseFlipH = false
    private var baseFlipV = false
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
        // A floating paste is dragged by the canvas's own placement logic, not transformed; leave it be.
        if (document.hasPendingPaste) return
        val bounds = Rectangle(0, 0, document.width, document.height)
        // With no selection, hug the active layer's opaque pixels (its visible content) rather than the
        // whole canvas, so grabbing Move drops the box tight around what you'd actually drag. Fall back
        // to the full canvas for a fully-transparent layer.
        val region = (document.selection ?: document.activeLayerOpaqueBounds() ?: bounds)
            .intersection(bounds)
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
        ensureLifted(ctx.document)
        mode = m
        pressImage = ctx.imagePoint
        origDest = Rectangle(dest)
        baseQuadrant = quadrant
        baseFlipH = flipH
        baseFlipV = flipV
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
            // Negative dest span mirrors the draw, baking any horizontal/vertical flip into the pixels.
            val x1 = if (flipH) w else 0; val x2 = if (flipH) 0 else w
            val y1 = if (flipV) h else 0; val y2 = if (flipV) 0 else h
            drawImage(rotated, x1, y1, x2, y2, 0, 0, rotated.width, rotated.height, null)
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

    override fun onNudge(document: ImageDocument, dx: Int, dy: Int): Boolean {
        // Arrow keys move the box like a tiny drag: arm it if needed, lift on the first nudge (so the
        // move is undoable and the pixels detach), then shift the destination by whole image pixels.
        if (dest.isEmpty) arm(document)
        if (dest.isEmpty) return false
        ensureLifted(document)
        dest = Rectangle(dest.x + dx, dest.y + dy, dest.width, dest.height)
        return true
    }

    override fun onDeactivated(document: ImageDocument) {
        // Leaving the tool with lifted pixels bakes them; an armed-only box just disappears.
        if (floating != null) onCommit(document) else clearState()
    }

    // ---- gesture helpers ------------------------------------------------------------------------

    /** Detaches the pixels into [floating] on the first real manipulation (destructive → undo first). */
    private fun ensureLifted(document: ImageDocument) {
        if (floating != null) return
        document.pushUndo()
        floating = document.liftRegion(origRegion)
        document.selection = null
    }

    private fun moveBy(ctx: ToolContext) {
        val dx = ctx.imagePoint.x - pressImage.x
        val dy = ctx.imagePoint.y - pressImage.y
        dest = Rectangle(origDest.x + dx, origDest.y + dy, origDest.width, origDest.height)
    }

    /**
     * Scales [origDest] so the grabbed handle follows the pointer. Photoshop modifiers apply: Alt
     * scales symmetrically about the centre, and Shift constrains proportions (corners scale
     * uniformly; an edge scales the other axis to match). Implemented as a scale about a fixed anchor.
     */
    private fun resizeBy(ctx: ToolContext) {
        val base = origDest
        val p = ctx.imagePoint
        val cx = base.x + base.width / 2.0
        val cy = base.y + base.height / 2.0

        // Fixed point of the scale: the centre with Alt, else the edge/corner opposite the handle.
        val ax = when { ctx.altDown -> cx; handleX > 0 -> base.x.toDouble(); handleX < 0 -> (base.x + base.width).toDouble(); else -> cx }
        val ay = when { ctx.altDown -> cy; handleY > 0 -> base.y.toDouble(); handleY < 0 -> (base.y + base.height).toDouble(); else -> cy }
        // Base position of the grabbed edge (its distance from the anchor sets the scale reference).
        val gx = when { handleX > 0 -> (base.x + base.width).toDouble(); handleX < 0 -> base.x.toDouble(); else -> cx }
        val gy = when { handleY > 0 -> (base.y + base.height).toDouble(); handleY < 0 -> base.y.toDouble(); else -> cy }

        var sx = if (handleX != 0) (p.x - ax) / (gx - ax) else 1.0
        var sy = if (handleY != 0) (p.y - ay) / (gy - ay) else 1.0

        if (ctx.shiftDown) {
            when {
                handleX != 0 && handleY != 0 -> {
                    val s = maxOf(abs(sx), abs(sy))
                    sx = if (sx < 0) -s else s
                    sy = if (sy < 0) -s else s
                }
                handleX != 0 -> sy = abs(sx)
                handleY != 0 -> sx = abs(sy)
            }
        }

        // Dragging a handle past its anchor gives a negative scale: the box lands mirrored on the far
        // side, so record the flip (relative to where this gesture started) and mirror the pixels to
        // match — otherwise the box moves but the content isn't specular.
        flipH = baseFlipH != (sx < 0)
        flipV = baseFlipV != (sy < 0)

        val nl = ax + (base.x - ax) * sx
        val nr = ax + (base.x + base.width - ax) * sx
        val nt = ay + (base.y - ay) * sy
        val nb = ay + (base.y + base.height - ay) * sy
        val x0 = min(nl, nr).roundToInt()
        val x1 = max(nl, nr).roundToInt()
        val y0 = min(nt, nb).roundToInt()
        val y1 = max(nt, nb).roundToInt()
        dest = Rectangle(x0, y0, (x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1))
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

    override val hasDynamicCursor = true

    override fun cursorAt(viewport: Viewport, componentPoint: Point, shiftDown: Boolean, ctrlDown: Boolean): Cursor? {
        if (dest.isEmpty) return null
        val c = viewport.toComponent(dest)
        // Ctrl anywhere on/near the box means rotate, so preview the rotate cursor.
        if (ctrlDown && nearBox(c, componentPoint)) return ToolCursors.rotate()
        handleAt(c, componentPoint)?.let { (hx, hy) -> return ToolCursors.resize(hx, hy) }
        if (c.contains(componentPoint)) return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        if (distanceToRect(c, componentPoint) <= JBUI.scale(ROTATE_RING)) return ToolCursors.rotate()
        return null
    }

    /** Which gesture a press at [ctx] starts, given an already-staged box. */
    private fun resolveMode(ctx: ToolContext): Mode {
        val c = ctx.viewport.toComponent(dest)
        // Holding Ctrl rotates (snapped to 90°) from anywhere on or near the box, so it's reachable
        // even when the box fills the view and the outer ring is off-screen. Shift/Alt are reserved
        // for constrain-proportions / scale-from-centre while resizing.
        if (ctx.ctrlDown && nearBox(c, ctx.componentPoint)) return Mode.ROTATE
        handleAt(c, ctx.componentPoint)?.let { (hx, hy) ->
            handleX = hx
            handleY = hy
            return Mode.RESIZE
        }
        if (c.contains(ctx.componentPoint)) return Mode.MOVE
        if (distanceToRect(c, ctx.componentPoint) <= JBUI.scale(ROTATE_RING)) return Mode.ROTATE
        return Mode.NONE
    }

    /** True if [p] is inside the box or within the rotate ring just outside it. */
    private fun nearBox(c: Rectangle, p: Point): Boolean =
        c.contains(p) || distanceToRect(c, p) <= JBUI.scale(ROTATE_RING)

    /** Which handle of [c] (component space) sits under [p], as (x,y) in -1/0/1, or null. */
    private fun handleAt(c: Rectangle, p: Point): Pair<Int, Int>? {
        val tol = JBUI.scale(HANDLE_HIT)
        for (hy in -1..1) {
            for (hx in -1..1) {
                if (hx == 0 && hy == 0) continue
                val hp = handlePoint(c, hx, hy)
                if (abs(p.x - hp.x) <= tol && abs(p.y - hp.y) <= tol) {
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
        flipH = false
        flipV = false
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
            val img = rotated(it)
            // Mirror in dest space to preview a flip (swap the destination edges for each flipped axis).
            val x1 = if (flipH) c.x + c.width else c.x; val x2 = if (flipH) c.x else c.x + c.width
            val y1 = if (flipV) c.y + c.height else c.y; val y2 = if (flipV) c.y else c.y + c.height
            g.drawImage(img, x1, y1, x2, y2, 0, 0, img.width, img.height, null)
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
