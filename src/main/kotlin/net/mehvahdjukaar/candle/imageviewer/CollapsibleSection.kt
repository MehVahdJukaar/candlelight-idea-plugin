package net.mehvahdjukaar.candle.imageviewer

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.CollapsibleSection.Companion.body
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A titled dock section that can be collapsed/expanded. Sized for [BoxLayout] docks: [maximumSize]
 * tracks [preferredSize] whenever the collapsed state changes so no blank gap is left behind.
 */
class CollapsibleSection(
    title: String,
    collapsedInitially: Boolean = false,
    body: JComponent,
) : JPanel(BorderLayout()) {

    private val toggle = JButton(title).apply {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusable = false
        horizontalAlignment = JButton.LEFT
        icon = if (collapsedInitially) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(2, 1)
    }

    private val content = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(body, BorderLayout.CENTER)
        isVisible = !collapsedInitially
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private var collapsed = collapsedInitially

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(JBUI.scale(4))
        add(toggle, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)

        toggle.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = setCollapsed(!collapsed)
        })
    }

    fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        content.isVisible = !collapsed
        toggle.icon = if (collapsed) AllIcons.General.ArrowRight else AllIcons.General.ArrowDown
        revalidateDock()
    }

    /** [BoxLayout] reads maximum height from each child — keep it in sync with collapsed state. */
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    private fun revalidateDock() {
        revalidate()
        repaint()
        var ancestor: Container? = parent
        while (ancestor != null) {
            ancestor.revalidate()
            ancestor.repaint()
            ancestor = ancestor.parent
        }
    }

    companion object {
        /** Wraps [body] for left alignment inside a collapsible section. */
        fun body(body: JComponent): JComponent = DockBodyPanel(body)

        /** Like [body] but stretches [body] to the full section width (e.g. for a list). */
        fun fullWidthBody(body: JComponent): JComponent = FullWidthBodyPanel(body)

        fun verticalGap(): Component = Box.createVerticalStrut(JBUI.scale(4))
    }

    /** Left-anchored wrapper whose max height follows its content (for nested BoxLayout children). */
    private class DockBodyPanel(body: JComponent) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(body, BorderLayout.WEST)
        }

        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /** Wrapper that stretches its content to the full section width. */
    private class FullWidthBodyPanel(body: JComponent) : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(body, BorderLayout.CENTER)
        }

        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}
