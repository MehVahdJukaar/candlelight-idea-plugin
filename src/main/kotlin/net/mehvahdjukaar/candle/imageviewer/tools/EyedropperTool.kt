package net.mehvahdjukaar.candle.imageviewer.tools

/** Picks the color of the clicked pixel and makes it the active foreground color. */
class EyedropperTool : Tool {

    override val id = "pick"
    override val displayName = "Pick"
    override val description = "Eyedropper — pick a color from the image"

    override fun onPress(ctx: ToolContext) {
        val p = ctx.imagePoint
        ctx.document.colorAt(p.x, p.y)?.let(ctx.setColor)
    }
}
