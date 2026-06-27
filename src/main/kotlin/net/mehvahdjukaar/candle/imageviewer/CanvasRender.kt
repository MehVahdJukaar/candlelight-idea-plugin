package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

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

    /** Draws a marching-ants style rectangle outline (black underlay + white dashes). */
    fun selectionOutline(g: Graphics2D, r: Rectangle) {
        val gg = g.create() as Graphics2D
        try {
            gg.color = JBColor.black
            gg.stroke = BasicStroke(1f)
            gg.drawRect(r.x, r.y, r.width, r.height)
            val dash = JBUI.scale(4).toFloat()
            gg.color = JBColor.white
            gg.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(dash, dash), 0f)
            gg.drawRect(r.x, r.y, r.width, r.height)
        } finally {
            gg.dispose()
        }
    }
}
