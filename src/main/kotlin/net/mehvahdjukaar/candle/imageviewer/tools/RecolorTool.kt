package net.mehvahdjukaar.candle.imageviewer.tools

import java.awt.Cursor
import javax.swing.Icon

/**
 * Replaces every pixel sharing the clicked pixel's exact color with the foreground color across the
 * whole image (or within the active selection) — a non-contiguous, color-based fill.
 */
class RecolorTool : Tool {

    override val id = "recolor"
    override val displayName = "Recolor"
    override val description = "Replace all pixels of the clicked color with the foreground color"
    override val icon: Icon = ToolIcons.RECOLOR
    override val cursor: Cursor = ToolCursors.bucket()
    override val altPicksColor = true

    override fun onPress(ctx: ToolContext) {
        val p = ctx.imagePoint
        val target = ctx.document.colorAt(p.x, p.y)?.rgb ?: return
        val replacement = ctx.color.rgb
        if (target == replacement) return
        ctx.document.pushUndo()
        ctx.document.replaceColor(target, replacement)
    }
}
