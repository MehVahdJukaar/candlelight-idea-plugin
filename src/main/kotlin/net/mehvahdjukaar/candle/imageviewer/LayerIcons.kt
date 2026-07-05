package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import javax.swing.Icon

/**
 * Programmatically-drawn eye icons for the layer visibility toggle. Drawn in code (like the tool
 * cursor glyphs) so they track the theme foreground and never depend on a platform icon existing.
 */
class EyeIcon(private val open: Boolean, private val sizePx: Int = 14) : Icon {

    override fun getIconWidth(): Int = JBUI.scale(sizePx)
    override fun getIconHeight(): Int = JBUI.scale(sizePx)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.translate(x, y)
            val s = iconWidth.toDouble() / GRID
            g2.scale(s, s)
            g2.color = if (open) JBColor.foreground() else JBColor.gray
            g2.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

            // Almond-shaped eye outline centered in the 16-unit grid.
            val eye = Path2D.Float().apply {
                moveTo(2.0, 8.0)
                curveTo(5.0, 3.5, 11.0, 3.5, 14.0, 8.0)
                curveTo(11.0, 12.5, 5.0, 12.5, 2.0, 8.0)
                closePath()
            }
            g2.draw(eye)
            if (open) {
                g2.draw(Ellipse2D.Float(6.4f, 6.4f, 3.2f, 3.2f))
            } else {
                // A slash through the eye marks it hidden.
                g2.draw(Line2D.Float(3.0f, 3.0f, 13.0f, 13.0f))
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val GRID = 16.0
    }
}
