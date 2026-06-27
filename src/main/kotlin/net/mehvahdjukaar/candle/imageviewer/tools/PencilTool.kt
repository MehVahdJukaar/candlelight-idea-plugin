package net.mehvahdjukaar.candle.imageviewer.tools

import net.mehvahdjukaar.candle.imageviewer.Viewport
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon

/**
 * Draws along the drag path with a square brush of [brushSize] pixels. With [erase] = true it writes
 * transparency instead of the foreground color, acting as the eraser. Drawing is clipped to the
 * active selection. A pixel-aligned outline previews where the brush will land while hovering.
 */
class PencilTool(private val erase: Boolean) : Tool {

    override val id = if (erase) "eraser" else "pencil"
    override val displayName = if (erase) "Eraser" else "Pencil"
    override val description =
        if (erase) "Erase pixels to transparent" else "Draw pixels with the foreground color"
    override val icon: Icon = if (erase) ToolIcons.ERASER else ToolIcons.PENCIL
    override val cursor: Cursor = if (erase) ToolCursors.eraser() else ToolCursors.pencil()

    /** Side length, in image pixels, of the square brush. Shared by the canvas's brush-size slider. */
    var brushSize: Int = 1

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
        if (from == null) ctx.document.stampBrush(p.x, p.y, brushSize, argb)
        else ctx.document.drawBrushLine(from.x, from.y, p.x, p.y, brushSize, argb)
        last = p
    }

    override fun paintHover(g: Graphics2D, viewport: Viewport, hover: Point) {
        val half = (brushSize - 1) / 2
        val r = viewport.toComponent(Rectangle(hover.x - half, hover.y - half, brushSize, brushSize))
        // Black-over-white outline so it stays visible on any pixel color.
        g.stroke = BasicStroke(1f)
        g.color = Color.WHITE
        g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1)
        g.color = Color.BLACK
        g.drawRect(r.x, r.y, r.width, r.height)
    }
}
