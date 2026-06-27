package net.mehvahdjukaar.candle.imageviewer.tools

import javax.swing.Icon

/**
 * Zooms the view: click to zoom in around the cursor, Alt-click to zoom out. The canvas repaints
 * after the press, so no document state is touched.
 */
class ZoomTool : Tool {

    override val id = "zoom"
    override val displayName = "Zoom"
    override val description = "Zoom in — Alt-click to zoom out"
    override val icon: Icon = ToolIcons.ZOOM
    override val cursor = ToolCursors.zoom()

    override fun onPress(ctx: ToolContext) {
        val factor = if (ctx.altDown) 1.0 / ZOOM_STEP else ZOOM_STEP
        ctx.viewport.zoomAt(ctx.componentPoint.x.toDouble(), ctx.componentPoint.y.toDouble(), factor)
    }

    private companion object {
        private const val ZOOM_STEP = 1.5
    }
}
