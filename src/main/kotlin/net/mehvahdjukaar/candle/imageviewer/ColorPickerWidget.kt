package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import kotlin.math.roundToInt

/**
 * A compact, self-contained color picker: a 2D saturation/brightness square you can click and drag,
 * a hue strip beneath it, an RGB/HSB mode toggle and three plain numeric channel sliders. No dialog.
 *
 * Hue/saturation/brightness are the canonical state (so the hue is preserved even at zero saturation
 * or brightness); RGB edits are converted into it.
 */
class ColorPickerWidget(initial: Color) : JPanel() {

    /** Fired when the user changes the color (not when it is set programmatically). */
    var onColorChanged: ((Color) -> Unit)? = null

    val color: Color
        get() {
            val c = Color.getHSBColor(hue, sat, bri)
            return Color(c.red, c.green, c.blue, alpha)
        }

    private enum class Mode { RGB, HSB }

    private var hue = 0f
    private var sat = 0f
    private var bri = 0f
    private var alpha = 255
    private var mode = Mode.RGB
    private var updating = false

    private val square = SaturationBrightnessSquare()
    private val hueSlider = ChannelSlider()
    private val rgbToggle = JToggleButton("RGB", true)
    private val hsbToggle = JToggleButton("HSB", false)
    private val preview = PreviewSwatch()
    private val channelLabels = List(3) { smallLabel() }
    private val valueFields = List(3) { i -> valueField { onChannelTyped(i, it) } }
    private val channelSliders = List(3) { ChannelSlider() }

    // Alpha is independent of the RGB/HSB mode, so it gets its own permanent row below the channels.
    private val alphaLabel = smallLabel()
    private val alphaField = valueField { onAlphaTyped(it) }
    private val alphaSlider = ChannelSlider()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT

        hueSlider.trackGradient = { f -> Color.getHSBColor(f.toFloat(), 1f, 1f) }
        hueSlider.onChange = {
            if (!updating) {
                hue = hueSlider.fraction.toFloat()
                refresh()
                emit()
            }
        }
        channelSliders.forEachIndexed { i, slider -> slider.onChange = { if (!updating) onChannelMoved(i) } }
        rgbToggle.addActionListener { setMode(Mode.RGB) }
        hsbToggle.addActionListener { setMode(Mode.HSB) }

        alphaLabel.text = "A"
        alphaSlider.checkerboard = true
        // The track fades the current color from transparent to opaque, over a checkerboard.
        alphaSlider.trackGradient = { f ->
            val c = Color.getHSBColor(hue, sat, bri)
            Color(c.red, c.green, c.blue, (f * 255).roundToInt())
        }
        alphaSlider.onChange = {
            if (!updating) {
                alpha = (alphaSlider.fraction * 255).roundToInt()
                refresh()
                emit()
            }
        }

        add(square)
        add(Box.createVerticalStrut(JBUI.scale(4)))
        add(hueSlider)
        add(Box.createVerticalStrut(JBUI.scale(6)))
        add(buildModeRow())
        add(Box.createVerticalStrut(JBUI.scale(4)))
        channelSliders.indices.forEach { i ->
            add(buildChannelRow(i))
            add(Box.createVerticalStrut(JBUI.scale(3)))
        }
        add(buildAlphaRow())

        val hsb = Color.RGBtoHSB(initial.red, initial.green, initial.blue, null)
        hue = hsb[0]; sat = hsb[1]; bri = hsb[2]; alpha = initial.alpha
        refresh()
    }

    // Stretch to the dock width but allow it to be narrowed.
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
    override fun getMinimumSize() = Dimension(JBUI.scale(70), preferredSize.height)

    /** Sets the color from outside (eyedropper, palette) without firing [onColorChanged]. */
    fun setColorExternally(c: Color) {
        if (c.rgb == color.rgb) return // ignore our own echo so dragging the square doesn't jump
        val hsb = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        hue = hsb[0]; sat = hsb[1]; bri = hsb[2]; alpha = c.alpha
        refresh()
    }

    private fun buildModeRow(): JComponent {
        val group = ButtonGroup()
        listOf(rgbToggle, hsbToggle).forEach { b ->
            group.add(b)
            b.isFocusable = false
            b.margin = JBUI.insets(2, 8)
            b.font = JBUI.Fonts.miniFont()
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            add(preview)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(rgbToggle)
            add(hsbToggle)
        }
    }

    private fun buildChannelRow(index: Int): JComponent =
        JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(CHANNEL_ROW_H))
            add(channelLabels[index], BorderLayout.WEST)
            add(channelSliders[index], BorderLayout.CENTER)
            add(valueFields[index], BorderLayout.EAST)
        }

    private fun buildAlphaRow(): JComponent =
        JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(CHANNEL_ROW_H))
            add(alphaLabel, BorderLayout.WEST)
            add(alphaSlider, BorderLayout.CENTER)
            add(alphaField, BorderLayout.EAST)
        }

    /** A small right-aligned numeric entry that commits on Enter or focus loss via [commit]. */
    private fun valueField(commit: (Int) -> Unit): JBTextField = JBTextField().apply {
        font = JBUI.Fonts.miniFont()
        horizontalAlignment = SwingConstants.RIGHT
        // Pin the height: a text field's tall default would otherwise inflate every channel row.
        preferredSize = Dimension(JBUI.scale(42), JBUI.scale(20))
        minimumSize = preferredSize
        maximumSize = preferredSize
        val push = { text.trim().toIntOrNull()?.let(commit) }
        addActionListener { push() }
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) { push() }
        })
    }

    /** Applies a typed channel value (0..255 for RGB, 0..360/0..100 for HSB) to the picker state. */
    private fun onChannelTyped(index: Int, raw: Int) {
        val frac = when {
            mode == Mode.RGB -> raw.coerceIn(0, 255) / 255.0
            index == 0 -> raw.coerceIn(0, 360) / 360.0
            else -> raw.coerceIn(0, 100) / 100.0
        }
        channelSliders[index].fraction = frac
        onChannelMoved(index)
    }

    private fun onAlphaTyped(raw: Int) {
        alpha = raw.coerceIn(0, 255)
        refresh()
        emit()
    }

    private fun setMode(newMode: Mode) {
        if (newMode == mode) return
        mode = newMode
        refresh()
    }

    private fun onChannelMoved(index: Int) {
        if (mode == Mode.HSB) {
            val f = channelSliders[index].fraction.toFloat()
            when (index) {
                0 -> hue = f
                1 -> sat = f
                else -> bri = f
            }
        } else {
            val r = (channelSliders[0].fraction * 255).roundToInt()
            val g = (channelSliders[1].fraction * 255).roundToInt()
            val b = (channelSliders[2].fraction * 255).roundToInt()
            val hsb = Color.RGBtoHSB(r, g, b, null)
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2]
        }
        refresh()
        emit()
    }

    /** Repaints the square, hue strip, channel sliders, value labels and preview from the state. */
    private fun refresh() {
        updating = true
        hueSlider.fraction = hue.toDouble()
        square.repaint()
        preview.repaint()

        alphaSlider.fraction = alpha / 255.0
        alphaSlider.repaint() // the track gradient tracks the current color, so refresh it too
        setFieldText(alphaField, alpha)

        val letters = if (mode == Mode.RGB) RGB_LETTERS else HSB_LETTERS
        val rgb = color
        for (i in 0..2) {
            channelLabels[i].text = letters[i]
            if (mode == Mode.RGB) {
                val v = when (i) { 0 -> rgb.red; 1 -> rgb.green; else -> rgb.blue }
                channelSliders[i].fraction = v / 255.0
                setFieldText(valueFields[i], v)
            } else {
                val values = floatArrayOf(hue, sat, bri)
                channelSliders[i].fraction = values[i].toDouble()
                setFieldText(valueFields[i], (values[i] * if (i == 0) 360 else 100).roundToInt())
            }
        }
        updating = false
    }

    private fun emit() = onColorChanged?.invoke(color)

    /** Updates a value field's text, but not while it has focus, so it can't clobber active typing. */
    private fun setFieldText(field: JBTextField, value: Int) {
        if (!field.isFocusOwner) field.text = value.toString()
    }

    private fun smallLabel() = JLabel().apply {
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
    }

    /** The 2D saturation (x) / brightness (y) field for the current hue; click or drag to pick. */
    private inner class SaturationBrightnessSquare : JComponent() {
        init {
            cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
            val mouse = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = pick(e)
                override fun mouseDragged(e: MouseEvent) = pick(e)
            }
            addMouseListener(mouse)
            addMouseMotionListener(mouse)
        }

        override fun getPreferredSize() = Dimension(JBUI.scale(120), JBUI.scale(108))
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(108))
        override fun getMinimumSize() = Dimension(JBUI.scale(40), JBUI.scale(108))

        private fun pick(e: MouseEvent) {
            if (updating) return
            sat = (e.x.toFloat() / width).coerceIn(0f, 1f)
            bri = (1f - e.y.toFloat() / height).coerceIn(0f, 1f)
            refresh()
            emit()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                val w = width
                val h = height
                g2.paint = GradientPaint(0f, 0f, Color.WHITE, w.toFloat(), 0f, Color.getHSBColor(hue, 1f, 1f))
                g2.fillRect(0, 0, w, h)
                g2.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 0), 0f, h.toFloat(), Color(0, 0, 0, 255))
                g2.fillRect(0, 0, w, h)
                g2.color = JBColor.border()
                g2.drawRect(0, 0, w - 1, h - 1)

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val mx = (sat * w).roundToInt()
                val my = ((1f - bri) * h).roundToInt()
                val r = JBUI.scale(4)
                g2.color = Color.BLACK
                g2.drawOval(mx - r, my - r, r * 2, r * 2)
                g2.color = Color.WHITE
                g2.drawOval(mx - r + 1, my - r + 1, r * 2 - 2, r * 2 - 2)
            } finally {
                g2.dispose()
            }
        }
    }

    /** Small preview of the current color. */
    private inner class PreviewSwatch : JComponent() {
        override fun getPreferredSize() = Dimension(JBUI.scale(18), JBUI.scale(18))
        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            CanvasRender.checkerboard(g2, Rectangle(0, 0, width, height))
            g2.color = color
            g2.fillRect(0, 0, width, height)
            g2.color = JBColor.border()
            g2.drawRect(0, 0, width - 1, height - 1)
        }
    }

    /**
     * A horizontal slider. With [trackGradient] set it paints that gradient (used for the hue strip);
     * otherwise it is a plain neutral slider (used for the numeric channels).
     */
    private class ChannelSlider : JComponent() {

        var fraction: Double = 0.0
            set(value) {
                field = value.coerceIn(0.0, 1.0)
                repaint()
            }

        var trackGradient: ((Double) -> Color)? = null
        var checkerboard = false
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

        override fun getPreferredSize() = Dimension(JBUI.scale(80), JBUI.scale(15))
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(15))
        override fun getMinimumSize() = Dimension(JBUI.scale(20), JBUI.scale(15))

        private fun setFromMouse(e: MouseEvent) {
            val thumb = JBUI.scale(THUMB)
            val travel = (width - thumb).coerceAtLeast(1)
            fraction = (e.x - thumb / 2.0) / travel
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

                val gradient = trackGradient
                if (gradient != null) {
                    if (checkerboard) CanvasRender.checkerboard(g2, Rectangle(trackX, trackY, trackW, trackH))
                    for (i in 0 until trackW) {
                        g2.color = gradient(i.toDouble() / trackW)
                        g2.fillRect(trackX + i, trackY, 1, trackH)
                    }
                } else {
                    g2.color = TRACK_BG
                    g2.fillRect(trackX, trackY, trackW, trackH)
                    g2.color = TRACK_FILL
                    g2.fillRect(trackX, trackY, (fraction * trackW).roundToInt().coerceIn(0, trackW), trackH)
                }
                g2.color = JBColor.border()
                g2.drawRect(trackX, trackY, trackW - 1, trackH - 1)

                val tx = trackX + (fraction * trackW).roundToInt()
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
            private val TRACK_BG = JBColor(Color(0xCD, 0xCD, 0xCD), Color(0x51, 0x51, 0x51))
            private val TRACK_FILL = JBColor(Color(0x9A, 0x9A, 0x9A), Color(0x6E, 0x6E, 0x6E))
        }
    }

    companion object {
        private const val CHANNEL_ROW_H = 22
        private val RGB_LETTERS = listOf("R", "G", "B")
        private val HSB_LETTERS = listOf("H", "S", "B")
    }
}
