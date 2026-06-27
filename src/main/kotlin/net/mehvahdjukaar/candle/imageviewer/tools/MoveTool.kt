package net.mehvahdjukaar.candle.imageviewer.tools

import net.mehvahdjukaar.candle.imageviewer.CanvasRender
import net.mehvahdjukaar.candle.imageviewer.Viewport
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * Lifts the selected region (or the whole image if nothing is selected) and moves it. The pixels
 * float during the drag and are stamped back into the document on release, leaving transparency
 * behind their original location.
 */
class MoveTool : Tool {

    override val id = "move"
    override val displayName = "Move"
    override val description = "Move the selection, or the whole image if none"
    override val icon: Icon = ToolIcons.MOVE
    override val cursor: Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    private var floating: BufferedImage? = null
    private var origin = Point()
    private var grabbedAt = Point()
    private var position = Point()

    override fun onPress(ctx: ToolContext) {
        val doc = ctx.document
        val region = (doc.selection ?: Rectangle(0, 0, doc.width, doc.height))
            .intersection(Rectangle(0, 0, doc.width, doc.height))
        if (region.isEmpty) return
        doc.pushUndo()
        floating = doc.liftRegion(region)
        origin = Point(region.x, region.y)
        position = Point(region.x, region.y)
        grabbedAt = ctx.imagePoint
        doc.selection = null
    }

    override fun onDrag(ctx: ToolContext) {
        if (floating == null) return
        position = Point(origin.x + (ctx.imagePoint.x - grabbedAt.x), origin.y + (ctx.imagePoint.y - grabbedAt.y))
    }

    override fun onRelease(ctx: ToolContext) {
        val f = floating ?: return
        val doc = ctx.document
        doc.stamp(f, position.x, position.y)
        doc.selection = Rectangle(position.x, position.y, f.width, f.height)
            .intersection(Rectangle(0, 0, doc.width, doc.height))
            .takeIf { it.width > 0 && it.height > 0 }
        floating = null
    }

    override fun paintOverlay(g: Graphics2D, viewport: Viewport) {
        val f = floating ?: return
        val r = viewport.toComponent(Rectangle(position.x, position.y, f.width, f.height))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(f, r.x, r.y, r.width, r.height, null)
        CanvasRender.selectionOutline(g, r)
    }
}
