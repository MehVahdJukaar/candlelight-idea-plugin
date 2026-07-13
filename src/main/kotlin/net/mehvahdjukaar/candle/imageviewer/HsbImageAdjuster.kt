package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A Photoshop-style Hue / Saturation / Brightness adjuster for the whole active layer (or the active
 * selection). Each slider is bipolar — centered at zero — and dragging any of them re-derives the
 * layer from a snapshot taken when the popup opened, so the three shifts always compose from the
 * original rather than stacking. Committing collapses the whole gesture into one undo step; Cancel
 * (or Esc) restores the snapshot untouched.
 */
internal object HsbImageAdjuster {

    fun show(anchor: JComponent, x: Int, y: Int, document: ImageDocument, repaint: () -> Unit) {
        val base = document.snapshotActiveLayer()
        var hue = 0f   // -1..1  → -180°..+180° of hue rotation (i.e. ±0.5 turns)
        var sat = 0f   // -1..1  → fully desaturated .. fully saturated
        var bri = 0f   // -1..1  → black .. white
        var cancelled = false

        val hexHint = JLabel().apply {
            font = JBUI.Fonts.miniFont()
            foreground = JBColor.GRAY
        }

        // A mid reference color for the saturation track, so it reads as "grayer ← → more vivid".
        val hueRef = Color.getHSBColor(0.58f, 0.7f, 0.9f)
        val hueSlider = BipolarSlider { Color.getHSBColor(it.toFloat(), 1f, 1f) }
        val satSlider = BipolarSlider { f ->
            val h = Color.RGBtoHSB(hueRef.red, hueRef.green, hueRef.blue, null)
            Color.getHSBColor(h[0], f.toFloat(), 0.9f)
        }
        val briSlider = BipolarSlider { Color.getHSBColor(0f, 0f, it.toFloat()) }

        val hueValue = valueLabel()
        val satValue = valueLabel()
        val briValue = valueLabel()

        fun apply() {
            document.applyHsbAdjustment(base, hue * 0.5f, sat, bri)
            hueValue.text = "${(hue * 180).roundToInt()}°"
            satValue.text = signed((sat * 100).roundToInt())
            briValue.text = signed((bri * 100).roundToInt())
            repaint()
        }

        hueSlider.onChange = { hue = hueSlider.value.toFloat(); apply() }
        satSlider.onChange = { sat = satSlider.value.toFloat(); apply() }
        briSlider.onChange = { bri = briSlider.value.toFloat(); apply() }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            add(row("Hue", hueSlider, hueValue))
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(row("Saturation", satSlider, satValue))
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(row("Brightness", briSlider, briValue))
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, hueSlider)
            .setTitle("Adjust Hue / Saturation / Brightness")
            .setResizable(false)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        val reset = JButton("Reset").apply {
            isFocusable = false
            margin = JBUI.insets(2, 10)
            font = JBUI.Fonts.miniFont()
            addActionListener {
                hue = 0f; sat = 0f; bri = 0f
                hueSlider.value = 0.0; satSlider.value = 0.0; briSlider.value = 0.0
                apply()
            }
        }
        val cancel = JButton("Cancel").apply {
            isFocusable = false
            margin = JBUI.insets(2, 10)
            font = JBUI.Fonts.miniFont()
            addActionListener { cancelled = true; popup.cancel() }
        }
        val ok = JButton("Apply").apply {
            isFocusable = false
            margin = JBUI.insets(2, 10)
            font = JBUI.Fonts.miniFont()
            addActionListener { popup.cancel() }
        }
        panel.add(Box.createVerticalStrut(JBUI.scale(8)))
        panel.add(JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            add(reset)
            add(cancel)
            add(ok)
        })

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (cancelled || (hue == 0f && sat == 0f && bri == 0f)) {
                    document.restoreActiveLayer(base) // exact revert, no undo entry
                    repaint()
                    return
                }
                // Rewind to the pre-adjust pixels, snapshot them for a single undo step, then re-apply.
                document.restoreActiveLayer(base)
                document.pushUndo()
                document.applyHsbAdjustment(base, hue * 0.5f, sat, bri)
                repaint()
            }
        })

        popup.show(com.intellij.ui.awt.RelativePoint(anchor, java.awt.Point(x, y)))
    }

    private fun signed(v: Int) = if (v > 0) "+$v" else v.toString()

    private fun valueLabel() = JLabel().apply {
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        horizontalAlignment = SwingConstants.RIGHT
        preferredSize = Dimension(JBUI.scale(38), preferredSize.height)
    }

    private fun row(name: String, slider: BipolarSlider, value: JLabel): JComponent =
        JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(30))
            add(JLabel(name).apply {
                font = JBUI.Fonts.miniFont()
                foreground = JBColor.GRAY
                preferredSize = Dimension(JBUI.scale(64), preferredSize.height)
            }, BorderLayout.WEST)
            add(slider, BorderLayout.CENTER)
            add(value, BorderLayout.EAST)
        }

    /**
     * A horizontal slider whose value runs -1..1 with a center detent at 0. The track paints
     * [gradient] (fraction 0..1) and a tick marks the zero point.
     */
    private class BipolarSlider(private val gradient: (Double) -> Color) : JComponent() {

        var value: Double = 0.0
            set(v) {
                field = v.coerceIn(-1.0, 1.0)
                repaint()
            }

        var onChange: () -> Unit = {}

        init {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val mouse = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = setFromMouse(e)
                override fun mouseDragged(e: MouseEvent) = setFromMouse(e)
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
        }

        override fun getPreferredSize() = Dimension(JBUI.scale(150), JBUI.scale(14))
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(14))
        override fun getMinimumSize() = Dimension(JBUI.scale(60), JBUI.scale(14))

        private fun setFromMouse(e: MouseEvent) {
            val thumb = JBUI.scale(THUMB)
            val travel = (width - thumb).coerceAtLeast(1)
            val frac = (e.x - thumb / 2.0) / travel
            val v = (frac * 2.0 - 1.0)
            value = if (abs(v) < SNAP) 0.0 else v // snap to the center detent
            onChange()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val thumb = JBUI.scale(THUMB)
                val trackX = thumb / 2
                val trackW = (width - thumb).coerceAtLeast(1)
                val trackY = JBUI.scale(1)
                val trackH = height - JBUI.scale(2)

                for (i in 0 until trackW) {
                    g2.color = gradient(i.toDouble() / trackW)
                    g2.fillRect(trackX + i, trackY, 1, trackH)
                }
                g2.color = JBColor.border()
                g2.drawRect(trackX, trackY, trackW - 1, trackH - 1)

                // Zero tick.
                val zx = trackX + trackW / 2
                g2.color = JBColor.foreground()
                g2.drawLine(zx, trackY - JBUI.scale(1), zx, trackY + trackH)

                val frac = (value + 1.0) / 2.0
                val tx = trackX + (frac * trackW).roundToInt()
                g2.color = JBColor.background()
                g2.fillRoundRect(tx - thumb / 2, 0, thumb, height - 1, JBUI.scale(3), JBUI.scale(3))
                g2.color = JBColor.foreground()
                g2.drawRoundRect(tx - thumb / 2, 0, thumb - 1, height - 2, JBUI.scale(3), JBUI.scale(3))
            } finally {
                g2.dispose()
            }
        }

        companion object {
            private const val THUMB = 8
            private const val SNAP = 0.035
        }
    }
}
