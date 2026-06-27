package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Point

/**
 * Draws single pixels along the drag path. With [erase] = true it writes transparency instead of
 * the foreground color, acting as the eraser. Drawing is clipped to the active selection.
 */
class PencilTool(private val erase: Boolean) : Tool {

    override val id = if (erase) "eraser" else "pencil"
    override val displayName = if (erase) "Eraser" else "Pencil"
    override val description =
        if (erase) "Erase pixels to transparent" else "Draw single pixels with the foreground color"

    private var last: Point? = null

    override fun onPress(ctx: ToolContext) {
        ctx.document.pushUndo()
        last = null
        paint(ctx)
    }

    override fun onDrag(ctx: ToolContext) = paint(ctx)

    override fun onRelease(ctx: ToolContext) {
        last = null
    }

    private fun paint(ctx: ToolContext) {
        val p = ctx.imagePoint
        val argb = if (erase) 0 else ctx.color.rgb
        val from = last
        if (from == null) ctx.document.setPixel(p.x, p.y, argb) else ctx.document.drawLine(from.x, from.y, p.x, p.y, argb)
        last = p
    }
}
