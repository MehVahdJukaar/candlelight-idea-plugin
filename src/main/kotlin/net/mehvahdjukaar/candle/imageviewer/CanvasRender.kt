package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

/** Shared painting helpers and colors for the image canvases. */
object CanvasRender {

    /** Neutral backdrop filling the component around the image. */
    val CANVAS_BACKGROUND = JBColor(Color(0xF2, 0xF3, 0xF5), Color(0x2B, 0x2B, 0x2B))

    private val CHECKER_A = JBColor(Color(0xCB, 0xCB, 0xCB), Color(0x3C, 0x3C, 0x3C))
    private val CHECKER_B = JBColor(Color(0xF0, 0xF0, 0xF0), Color(0x4A, 0x4A, 0x4A))

    /** Paints a transparency checkerboard confined to [area] (the image bounds), not the whole view. */
    fun checkerboard(g: Graphics2D, area: Rectangle) {
        if (area.width <= 0 || area.height <= 0) return
        val gg = g.create() as Graphics2D
        try {
            gg.clip(area)
            val cell = JBUI.scale(8)
            gg.color = CHECKER_A
            gg.fill(area)
            gg.color = CHECKER_B
            var y = area.y
            var row = 0
            while (y < area.y + area.height) {
                var x = area.x + if (row % 2 == 0) 0 else cell
                while (x < area.x + area.width) {
                    gg.fillRect(x, y, cell, cell)
                    x += cell * 2
                }
                y += cell
                row++
            }
        } finally {
            gg.dispose()
        }
    }

    private val SELECTION_SHADE = Color(0, 0, 0, 110)

    /** Dims everything in [bounds] except the rectangular [hole]. */
    fun fillOutside(g: Graphics2D, bounds: Rectangle, hole: Rectangle, color: Color) {
        val x0 = bounds.x
        val y0 = bounds.y
        val x1 = bounds.x + bounds.width
        val y1 = bounds.y + bounds.height
        val cl = max(hole.x, x0)
        val cr = min(hole.x + hole.width, x1)
        val ct = max(hole.y, y0)
        val cb = min(hole.y + hole.height, y1)
        g.color = color
        g.fillRect(x0, y0, bounds.width, (ct - y0).coerceAtLeast(0))
        g.fillRect(x0, cb, bounds.width, (y1 - cb).coerceAtLeast(0))
        g.fillRect(x0, ct, (cl - x0).coerceAtLeast(0), (cb - ct).coerceAtLeast(0))
        g.fillRect(cr, ct, (x1 - cr).coerceAtLeast(0), (cb - ct).coerceAtLeast(0))
    }

    /** Shades outside [r] and draws an animated marching-ants outline in component space. */
    fun selectionHighlight(g: Graphics2D, viewBounds: Rectangle, r: Rectangle, dashPhase: Float) {
        if (r.width <= 0 || r.height <= 0) return
        val gg = g.create() as Graphics2D
        try {
            fillOutside(gg, viewBounds, r, SELECTION_SHADE)
            selectionOutline(gg, r, dashPhase)
        } finally {
            gg.dispose()
        }
    }

    /** Draws a marching-ants style rectangle outline; [dashPhase] animates the dashes when non-zero. */
    fun selectionOutline(g: Graphics2D, r: Rectangle, dashPhase: Float = 0f) {
        val gg = g.create() as Graphics2D
        try {
            val dash = JBUI.scale(4).toFloat()
            val pattern = floatArrayOf(dash, dash)
            gg.color = JBColor.black
            gg.stroke = BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, pattern, dashPhase)
            gg.drawRect(r.x, r.y, r.width, r.height)
            gg.color = JBColor.white
            gg.stroke = BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, pattern, dashPhase + dash)
            gg.drawRect(r.x, r.y, r.width, r.height)
        } finally {
            gg.dispose()
        }
    }
}
