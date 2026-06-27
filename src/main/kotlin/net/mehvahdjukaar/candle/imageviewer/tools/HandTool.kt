package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Cursor
import java.awt.Point
import javax.swing.Icon

/**
 * Pans the view by dragging, without touching the document. State is the last drag point, from which
 * each drag computes a delta to offset the [net.mehvahdjukaar.candle.imageviewer.Viewport].
 *
 * The same panning is also available temporarily from any tool by holding the space bar (handled in
 * the canvas), mirroring Photoshop's Hand tool.
 */
class HandTool : Tool {

    override val id = "hand"
    override val displayName = "Hand"
    override val description = "Pan the view — drag, or hold Space with any tool"
    override val icon: Icon = ToolIcons.HAND
    override val cursor: Cursor = ToolCursors.hand()

    private var last: Point? = null

    override fun onPress(ctx: ToolContext) {
        last = ctx.componentPoint
    }

    override fun onDrag(ctx: ToolContext) {
        val from = last ?: return
        val p = ctx.componentPoint
        ctx.viewport.pan((p.x - from.x).toDouble(), (p.y - from.y).toDouble())
        last = p
    }

    override fun onRelease(ctx: ToolContext) {
        last = null
    }
}
