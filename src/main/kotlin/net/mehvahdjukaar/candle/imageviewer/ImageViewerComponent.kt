package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ImageObserver
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A minimal image canvas that draws an image with nearest-neighbor scaling (crisp pixel art),
 * supports mouse-wheel zoom, drag-to-pan and fit-to-window, and animates GIFs.
 *
 * The image must already be fully loaded (e.g. via [javax.swing.ImageIcon]) so that [imgW]/[imgH]
 * are known up front.
 */
class ImageViewerComponent(
    private val image: Image,
    private val imgW: Int,
    private val imgH: Int,
) : JComponent() {

    private var zoom = 1.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var userInteracted = false
    private var dragPoint: Point? = null

    init {
        isOpaque = true
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

        addMouseWheelListener { e ->
            val factor = if (e.preciseWheelRotation < 0) ZOOM_STEP else 1.0 / ZOOM_STEP
            zoomAt(e.x.toDouble(), e.y.toDouble(), factor)
        }

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                dragPoint = e.point
            }

            override fun mouseDragged(e: MouseEvent) {
                val p = dragPoint ?: return
                offsetX += (e.x - p.x).toDouble()
                offsetY += (e.y - p.y).toDouble()
                dragPoint = e.point
                userInteracted = true
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                dragPoint = null
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (userInteracted) repaint() else fit()
            }
        })

        bindKey(KeyEvent.VK_0, "fit") { fit() }
        bindKey(KeyEvent.VK_1, "actualSize") { setZoom(1.0) }
        bindKey(KeyEvent.VK_EQUALS, "zoomIn") { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_PLUS, "zoomIn2") { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_MINUS, "zoomOut") { zoomAtCenter(1.0 / ZOOM_STEP) }
    }

    override fun addNotify() {
        super.addNotify()
        // Size is known once attached; fit unless the user already moved things.
        if (!userInteracted) fit()
    }

    /** Scales the image to fit the window (centered) and resets the "user touched it" flag. */
    fun fit() {
        val cw = width.coerceAtLeast(1)
        val ch = height.coerceAtLeast(1)
        zoom = min(cw.toDouble() / imgW, ch.toDouble() / imgH)
        offsetX = (cw - imgW * zoom) / 2.0
        offsetY = (ch - imgH * zoom) / 2.0
        userInteracted = false
        repaint()
    }

    private fun setZoom(target: Double) = zoomAtCenter(target / zoom)

    private fun zoomAtCenter(factor: Double) = zoomAt(width / 2.0, height / 2.0, factor)

    /** Multiplies the zoom by [factor], keeping the image point under ([px], [py]) fixed. */
    private fun zoomAt(px: Double, py: Double, factor: Double) {
        val clamped = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = clamped / zoom
        offsetX = px - (px - offsetX) * applied
        offsetY = py - (py - offsetY) * applied
        zoom = clamped
        userInteracted = true
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            paintCheckerboard(g2)

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            val w = (imgW * zoom).roundToInt().coerceAtLeast(1)
            val h = (imgH * zoom).roundToInt().coerceAtLeast(1)
            // Passing `this` as the observer drives GIF animation (see imageUpdate).
            g2.drawImage(image, offsetX.roundToInt(), offsetY.roundToInt(), w, h, this)

            paintInfo(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintCheckerboard(g2: Graphics2D) {
        val cell = JBUI.scale(8)
        g2.color = CHECKER_A
        g2.fillRect(0, 0, width, height)
        g2.color = CHECKER_B
        var y = 0
        var row = 0
        while (y < height) {
            var x = if (row % 2 == 0) 0 else cell
            while (x < width) {
                g2.fillRect(x, y, cell, cell)
                x += cell * 2
            }
            y += cell
            row++
        }
    }

    private fun paintInfo(g2: Graphics2D) {
        val label = "$imgW×$imgH   ${(zoom * 100).roundToInt()}%"
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.font = UIUtil.getLabelFont()
        val fm = g2.fontMetrics
        val pad = JBUI.scale(6)
        val boxW = fm.stringWidth(label) + pad * 2
        val boxH = fm.height + pad
        val x = JBUI.scale(8)
        val y = height - boxH - JBUI.scale(8)
        g2.color = Color(0, 0, 0, 140)
        g2.fillRoundRect(x, y, boxW, boxH, JBUI.scale(6), JBUI.scale(6))
        g2.color = Color.WHITE
        g2.drawString(label, x + pad, y + pad / 2 + fm.ascent)
    }

    /** Repaints on each animation frame so GIFs play. Returning [isShowing] stops work when hidden. */
    override fun imageUpdate(img: Image?, infoflags: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (infoflags and (ImageObserver.FRAMEBITS or ImageObserver.ALLBITS) != 0) {
            repaint()
        }
        return isShowing
    }

    private fun bindKey(keyCode: Int, name: String, action: () -> Unit) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name)
        actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = action()
        })
    }

    companion object {
        private const val ZOOM_STEP = 1.2
        private const val MIN_ZOOM = 0.02
        private const val MAX_ZOOM = 64.0

        private val CHECKER_A = JBColor(Color(0xCB, 0xCB, 0xCB), Color(0x3C, 0x3C, 0x3C))
        private val CHECKER_B = JBColor(Color(0xF0, 0xF0, 0xF0), Color(0x4A, 0x4A, 0x4A))
    }
}
