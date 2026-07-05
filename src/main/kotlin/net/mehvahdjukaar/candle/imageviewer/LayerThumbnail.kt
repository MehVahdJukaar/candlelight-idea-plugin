package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.max

/**
 * A small square preview of a [Layer]'s pixels, drawn over a checkerboard so transparency reads
 * clearly. The layer image is fit (aspect-preserved) and nearest-neighbour scaled so pixel art
 * stays crisp. Sized in logical (unscaled) pixels; HiDPI scaling is applied when painting.
 */
class LayerThumbnail(private val image: BufferedImage, private val sizePx: Int = 24) : Icon {

    override fun getIconWidth(): Int = JBUI.scale(sizePx)
    override fun getIconHeight(): Int = JBUI.scale(sizePx)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            val w = iconWidth
            val h = iconHeight

            // Checkerboard so transparent areas are visible.
            val cell = JBUI.scale(4)
            for (cy in 0 until h step cell) {
                for (cx in 0 until w step cell) {
                    g2.color = if (((cx / cell) + (cy / cell)) % 2 == 0) LIGHT else DARK
                    g2.fillRect(x + cx, y + cy, cell, cell)
                }
            }

            // Fit the image into the square, preserving aspect, nearest-neighbour.
            val scale = minOf(w.toDouble() / image.width, h.toDouble() / image.height)
            val dw = max(1, (image.width * scale).toInt())
            val dh = max(1, (image.height * scale).toInt())
            val dx = x + (w - dw) / 2
            val dy = y + (h - dh) / 2
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.drawImage(image, dx, dy, dw, dh, null)

            g2.color = JBColor.border()
            g2.drawRect(x, y, w - 1, h - 1)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private val LIGHT = JBColor(Color(0xCF, 0xCF, 0xCF), Color(0x5A, 0x5A, 0x5A))
        private val DARK = JBColor(Color(0xAD, 0xAD, 0xAD), Color(0x45, 0x45, 0x45))
    }
}
