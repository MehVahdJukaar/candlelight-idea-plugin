package net.mehvahdjukaar.candle.imageviewer

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * Layer list: select the active layer, toggle visibility (eye), reorder, add/remove and rename
 * layers. Each row shows a thumbnail preview next to the name; double-clicking the name renames it
 * inline. Top of the list is the front-most layer.
 */
class LayersPanel : JPanel(BorderLayout()) {

    private val rows = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private var document: ImageDocument? = null
    var onLayerActivated: (() -> Unit)? = null

    /** Index of the layer whose row currently hosts the inline rename editor, or -1. */
    private var editingIndex = -1

    init {
        border = JBUI.Borders.empty()
        add(rows, BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)
    }

    fun bind(doc: ImageDocument) {
        document = doc
        rebuild()
    }

    /** External refresh entry point — suppressed mid-rename so the editor keeps focus and caret. */
    fun refresh() {
        if (editingIndex >= 0) return
        rebuild()
    }

    private fun rebuild() {
        val doc = document ?: return
        rows.removeAll()
        val layers = doc.layers()
        for (index in layers.indices.reversed()) {
            rows.add(layerRow(doc, layers[index], index, index == doc.activeLayerIndex))
            rows.add(Box.createVerticalStrut(JBUI.scale(2)))
        }
        revalidate()
        repaint()
    }

    // Cap the height to the content so the dock's BoxLayout doesn't stretch this panel to fill the
    // remaining space, which would leave a large empty gap under the section above.
    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    // ---- footer ---------------------------------------------------------------------------------

    private fun buildFooter(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
        border = JBUI.Borders.emptyTop(4)
        add(footerButton(AllIcons.General.Add, "New layer") {
            document?.addEmptyLayer(); refresh()
        })
        add(footerButton(AllIcons.General.Remove, "Delete active layer") {
            val doc = document ?: return@footerButton
            if (doc.deleteActiveLayer()) refresh()
        })
        add(footerButton(AllIcons.General.ArrowUp, "Move layer up (toward front)") {
            val doc = document ?: return@footerButton
            doc.moveLayerUp(doc.activeLayerIndex); refresh()
        })
        add(footerButton(AllIcons.General.ArrowDown, "Move layer down (toward back)") {
            val doc = document ?: return@footerButton
            doc.moveLayerDown(doc.activeLayerIndex); refresh()
        })
    }

    private fun footerButton(icon: Icon, tip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tip
            preferredSize = Dimension(JBUI.scale(26), JBUI.scale(26))
            isFocusable = false
            addActionListener { action() }
        }

    // ---- rows -----------------------------------------------------------------------------------

    private fun layerRow(doc: ImageDocument, layer: Layer, index: Int, active: Boolean): JPanel {
        val row = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(ROW_HEIGHT))
            preferredSize = Dimension(JBUI.scale(10), JBUI.scale(ROW_HEIGHT))
            isOpaque = active
            if (active) background = ACTIVE_BG
            border = BorderFactory.createCompoundBorder(
                if (active) BorderFactory.createLineBorder(ACCENT, 1) else EmptyBorder(1, 1, 1, 1),
                JBUI.Borders.empty(1, 3),
            )
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        // Eye visibility toggle.
        row.add(JButton(EyeIcon(layer.visible)).apply {
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusable = false
            isRolloverEnabled = true
            preferredSize = Dimension(JBUI.scale(20), JBUI.scale(20))
            toolTipText = if (layer.visible) "Hide layer" else "Show layer"
            addActionListener { doc.setLayerVisible(index, !layer.visible); refresh() }
        }, BorderLayout.WEST)

        // Thumbnail + name (or the inline editor when this row is being renamed).
        val center = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(JLabel(LayerThumbnail(layer.pixels)), BorderLayout.WEST)
        }
        if (index == editingIndex) {
            center.add(nameEditor(doc, index, layer.name), BorderLayout.CENTER)
        } else {
            val name = JLabel(layer.name).apply {
                toolTipText = "${layer.name}  —  double-click to rename"
                border = JBUI.Borders.emptyLeft(4)
            }
            center.add(name, BorderLayout.CENTER)
            val click = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return
                    if (e.clickCount == 2) startRename(index) else selectLayer(doc, index)
                }
            }
            row.addMouseListener(click)
            center.addMouseListener(click)
            name.addMouseListener(click)
        }
        row.add(center, BorderLayout.CENTER)
        return row
    }

    // ---- inline rename --------------------------------------------------------------------------

    private fun startRename(index: Int) {
        if (editingIndex >= 0) return
        val doc = document ?: return
        editingIndex = index
        // Activate the layer being renamed; its resulting refresh() is suppressed by editingIndex.
        if (doc.activeLayerIndex != index) {
            doc.setActiveLayer(index)
            onLayerActivated?.invoke()
        }
        rebuild()
    }

    private fun nameEditor(doc: ImageDocument, index: Int, current: String): JTextField {
        val field = JTextField(current).apply {
            border = BorderFactory.createLineBorder(ACCENT, 1)
        }
        var finished = false
        fun finish(commit: Boolean) {
            if (finished) return
            finished = true
            editingIndex = -1
            if (commit) doc.renameLayer(index, field.text)
            rebuild()
        }
        field.addActionListener { finish(commit = true) }
        field.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) finish(commit = false)
            }
        })
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = finish(commit = true)
        })
        SwingUtilities.invokeLater { field.requestFocusInWindow(); field.selectAll() }
        return field
    }

    private fun selectLayer(doc: ImageDocument, index: Int) {
        if (doc.activeLayerIndex == index) return
        doc.setActiveLayer(index)
        onLayerActivated?.invoke()
        refresh()
    }

    companion object {
        private const val ROW_HEIGHT = 30
        private val ACCENT = JBColor.namedColor("Component.focusedBorderColor", JBColor.blue)
        private val ACTIVE_BG = JBColor(Color(0xE3, 0xEC, 0xFB), Color(0x2C, 0x39, 0x4D))
    }
}
