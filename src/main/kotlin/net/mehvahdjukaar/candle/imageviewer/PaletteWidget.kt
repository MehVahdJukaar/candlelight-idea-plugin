package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.max

/**
 * Shows the distinct colors currently present in the image as small clickable swatches that fill
 * each row left-to-right and wrap to the next, scrolling vertically when there are many. Clicking a
 * swatch makes it the active color.
 */
class PaletteWidget : JPanel(BorderLayout()) {

    var onColorPicked: ((Color) -> Unit)? = null

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
            toolTipText = String.format("#%06X", color.rgb and 0xFFFFFF)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    onColorPicked?.invoke(color)
                }
            })
        }

        override fun getPreferredSize() = Dimension(JBUI.scale(SWATCH), JBUI.scale(SWATCH))

        override fun paintComponent(g: Graphics) {
            g.color = color
            g.fillRect(0, 0, width, height)
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    companion object {
        private const val SWATCH = 16
        private const val GAP = 2
        private const val MAX_ROWS = 2
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
