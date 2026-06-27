package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Point
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.min

/** Drags out a rectangular selection (in image-pixel coordinates). */
class SelectTool : Tool {

    override val id = "select"
    override val displayName = "Select"
    override val description = "Rectangular selection (Esc clears it)"

    private var start: Point? = null

    override fun onPress(ctx: ToolContext) {
        start = ctx.imagePoint
        ctx.document.selection = Rectangle(ctx.imagePoint.x, ctx.imagePoint.y, 0, 0)
    }

    override fun onDrag(ctx: ToolContext) {
        val s = start ?: return
        val c = ctx.imagePoint
        ctx.document.selection = Rectangle(min(s.x, c.x), min(s.y, c.y), abs(c.x - s.x), abs(c.y - s.y))
    }

    override fun onRelease(ctx: ToolContext) {
        onDrag(ctx)
        val bounds = Rectangle(0, 0, ctx.document.width, ctx.document.height)
        ctx.document.selection = ctx.document.selection?.intersection(bounds)?.takeIf { it.width > 0 && it.height > 0 }
        start = null
    }
}
