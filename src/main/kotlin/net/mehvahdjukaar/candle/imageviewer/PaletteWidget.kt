package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shows the distinct colors currently present in the image as small clickable swatches that fill
 * each row left-to-right and wrap to the next, scrolling vertically when there are many. Left-click a
 * swatch to make it the active color; right-click one to open a Photoshop-style Hue/Saturation/
 * Brightness adjuster that remaps that color everywhere in the image, live.
 */
class PaletteWidget : JPanel(BorderLayout()) {

    var onColorPicked: ((Color) -> Unit)? = null

    /** Called once when an HSB adjustment starts, so the host can snapshot for a single undo step. */
    var onAdjustBegin: (() -> Unit)? = null

    /** Remaps every pixel of [from] to [to] in the image. Called repeatedly while sliders are dragged. */
    var onRecolor: ((from: Color, to: Color) -> Unit)? = null

    private val content = WrapContent()
    private var colorCount = 0

    init {
        val scroll = JBScrollPane(
            content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply { border = JBUI.Borders.empty() }
        add(scroll, BorderLayout.CENTER)
    }

    // Sizes to its content (so it takes no extra vertical space) but never grows past MAX_ROWS rows;
    // beyond that it scrolls. Width stretches with the dock but can be narrowed.
    override fun getPreferredSize() = Dimension(JBUI.scale(80), rowsHeight())
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, rowsHeight())
    override fun getMinimumSize() = Dimension(JBUI.scale(40), rowsHeight())

    private fun rowsHeight(): Int {
        val rows = if (colorCount == 0) 1 else minOf(neededRows(), MAX_ROWS)
        return rows * cell() + JBUI.scale(4)
    }

    private fun neededRows(): Int {
        val available = (width.takeIf { it > 0 } ?: JBUI.scale(120)) - JBUI.scale(6)
        val cols = max(1, available / cell())
        return ceil(colorCount.toDouble() / cols).toInt()
    }

    private fun cell() = JBUI.scale(SWATCH + GAP)

    fun setColors(colors: List<Color>) {
        colorCount = colors.size
        content.removeAll()
        if (colors.isEmpty()) {
            content.add(JLabel("No colors").apply {
                font = JBUI.Fonts.miniFont()
                foreground = JBColor.GRAY
            })
        } else {
            colors.forEach { content.add(Swatch(it)) }
        }
        content.revalidate()
        content.repaint()
        revalidate()
    }

    /** Flow content that tracks the viewport width so [WrapLayout] can wrap rows to fit. */
    private inner class WrapContent : JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(GAP), JBUI.scale(GAP))), Scrollable {
        init {
            border = JBUI.Borders.empty(1)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(18)
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(54)
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    private inner class Swatch(private val color: Color) : JComponent() {
        init {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "<html>${String.format("#%06X", color.rgb and 0xFFFFFF)}" +
                "<br><font color='#888888'>Click to use · Right-click to adjust</font></html>"
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = maybePopup(e)
                override fun mouseReleased(e: MouseEvent) = maybePopup(e)
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) onColorPicked?.invoke(color)
                }
            })
        }

        private fun maybePopup(e: MouseEvent) {
            if (e.isPopupTrigger) showAdjustPopup(color, this)
        }

        override fun getPreferredSize() = Dimension(JBUI.scale(SWATCH), JBUI.scale(SWATCH))

        override fun paintComponent(g: Graphics) {
            g.color = color
            g.fillRect(0, 0, width, height)
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    /** Opens the floating HSB adjuster anchored under [anchor], seeded with [original]. */
    private fun showAdjustPopup(original: Color, anchor: JComponent) {
        val panel = ColorAdjustPanel(original)
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.firstSlider)
            .setTitle("Adjust ${String.format("#%06X", original.rgb and 0xFFFFFF)}")
            .setResizable(false)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
        popup.showUnderneathOf(anchor)
    }

    /**
     * A small Hue / Saturation / Brightness editor for a single palette color. Adjusting a slider
     * remaps every pixel of that color in the image to the new one, chaining from the previously
     * applied color so the whole gesture collapses into a single undo step.
     */
    private inner class ColorAdjustPanel(private val original: Color) : JPanel() {

        private var hue: Float
        private var sat: Float
        private var bri: Float

        // The color currently painted into the image, so each edit remaps from it (not the original).
        private var applied = original
        private var begun = false

        private val hueSlider = HsbSlider { Color.getHSBColor(it.toFloat(), 1f, 1f) }
        private val satSlider = HsbSlider { Color.getHSBColor(hue, it.toFloat(), bri) }
        private val briSlider = HsbSlider { Color.getHSBColor(hue, sat, it.toFloat()) }
        private val before = MiniSwatch { original }
        private val after = MiniSwatch { current }
        private val hexLabel = miniLabel()
        private val hueValue = miniLabel()
        private val satValue = miniLabel()
        private val briValue = miniLabel()

        val firstSlider: JComponent get() = hueSlider

        private val current: Color get() = Color.getHSBColor(hue, sat, bri)

        init {
            val hsb = Color.RGBtoHSB(original.red, original.green, original.blue, null)
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2]

            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)

            hueSlider.onChange = { hue = hueSlider.fraction.toFloat(); onChange() }
            satSlider.onChange = { sat = satSlider.fraction.toFloat(); onChange() }
            briSlider.onChange = { bri = briSlider.fraction.toFloat(); onChange() }

            add(buildHeader())
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(buildRow("H", hueSlider, hueValue))
            add(Box.createVerticalStrut(JBUI.scale(5)))
            add(buildRow("S", satSlider, satValue))
            add(Box.createVerticalStrut(JBUI.scale(5)))
            add(buildRow("B", briSlider, briValue))
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(buildReset())

            refresh()
        }

        override fun getPreferredSize() = Dimension(JBUI.scale(220), super.getPreferredSize().height)

        private fun buildHeader(): JComponent =
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                add(before)
                add(JLabel("→").apply { foreground = JBColor.GRAY })
                add(after)
                add(Box.createHorizontalStrut(JBUI.scale(4)))
                add(hexLabel)
            }

        private fun buildRow(letter: String, slider: HsbSlider, value: JLabel): JComponent =
            JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(18))
                add(JLabel(letter).apply {
                    foreground = JBColor.GRAY
                    preferredSize = Dimension(JBUI.scale(10), preferredSize.height)
                }, BorderLayout.WEST)
                add(slider, BorderLayout.CENTER)
                add(value, BorderLayout.EAST)
            }

        private fun buildReset(): JComponent =
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                add(JButton("Reset").apply {
                    isFocusable = false
                    margin = JBUI.insets(2, 8)
                    font = JBUI.Fonts.miniFont()
                    addActionListener {
                        val hsb = Color.RGBtoHSB(original.red, original.green, original.blue, null)
                        hue = hsb[0]; sat = hsb[1]; bri = hsb[2]
                        onChange()
                    }
                })
            }

        private fun onChange() {
            val c = current
            if (c.rgb != applied.rgb) {
                if (!begun) { begun = true; onAdjustBegin?.invoke() }
                onRecolor?.invoke(applied, c)
                applied = c
            }
            refresh()
        }

        private fun refresh() {
            hueSlider.fraction = hue.toDouble()
            satSlider.fraction = sat.toDouble()
            briSlider.fraction = bri.toDouble()
            // Sat/bri tracks depend on the other channels, so repaint them as those change.
            satSlider.repaint()
            briSlider.repaint()
            before.repaint()
            after.repaint()
            hexLabel.text = String.format("#%06X", current.rgb and 0xFFFFFF)
            hueValue.text = "${(hue * 360).roundToInt()}°"
            satValue.text = "${(sat * 100).roundToInt()}%"
            briValue.text = "${(bri * 100).roundToInt()}%"
        }

        private fun miniLabel() = JLabel().apply {
            font = JBUI.Fonts.miniFont()
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.RIGHT
            preferredSize = Dimension(JBUI.scale(34), preferredSize.height)
        }
    }

    /** A tiny fixed swatch that paints whatever [supplier] returns; used for the before/after preview. */
    private class MiniSwatch(private val supplier: () -> Color) : JComponent() {
        override fun getPreferredSize() = Dimension(JBUI.scale(16), JBUI.scale(16))
        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            g.color = supplier()
            g.fillRect(0, 0, width, height)
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    /** A horizontal slider whose track paints [gradient] across it; click or drag to set [fraction]. */
    private class HsbSlider(private val gradient: (Double) -> Color) : JComponent() {

        var fraction: Double = 0.0
            set(value) {
                field = value.coerceIn(0.0, 1.0)
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

        override fun getPreferredSize() = Dimension(JBUI.scale(120), JBUI.scale(14))
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(14))
        override fun getMinimumSize() = Dimension(JBUI.scale(40), JBUI.scale(14))

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

                for (i in 0 until trackW) {
                    g2.color = gradient(i.toDouble() / trackW)
                    g2.fillRect(trackX + i, trackY, 1, trackH)
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
        }
    }

    companion object {
        private const val SWATCH = 16
        private const val GAP = 2
        private const val MAX_ROWS = 3
    }
}

/**
 * A [FlowLayout] that wraps its rows to the target's current width and reports the resulting
 * multi-row height (plain FlowLayout always reports a single row), so it lays out correctly inside
 * a scroll pane. Adapted from the well-known WrapLayout pattern.
 */
internal class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

    override fun minimumLayoutSize(target: Container): Dimension =
        layoutSize(target, false).also { it.width -= hgap + 1 }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var targetWidth = target.size.width
            if (targetWidth == 0) targetWidth = Int.MAX_VALUE

            val insets = target.insets
            val horizontalInsets = insets.left + insets.right + hgap * 2
            val maxWidth = targetWidth - horizontalInsets

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0
            for (component in target.components) {
                if (!component.isVisible) continue
                val d = if (preferred) component.preferredSize else component.minimumSize
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight)
                    rowWidth = 0
                    rowHeight = 0
                }
                if (rowWidth != 0) rowWidth += hgap
                rowWidth += d.width
                rowHeight = max(rowHeight, d.height)
            }
            addRow(dim, rowWidth, rowHeight)

            dim.width += horizontalInsets
            dim.height += insets.top + insets.bottom + vgap * 2

            val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
            if (scrollPane != null && target.isValid) dim.width -= hgap + 1
            return dim
        }
    }

    private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
        dim.width = max(dim.width, rowWidth)
        if (dim.height > 0) dim.height += vgap
        dim.height += rowHeight
    }
}
