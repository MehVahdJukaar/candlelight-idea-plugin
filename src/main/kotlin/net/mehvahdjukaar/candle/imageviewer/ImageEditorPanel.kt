package net.mehvahdjukaar.candle.imageviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.tools.Tool
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke

/**
 * A basic image editor: a Photoshop-style tool palette on the left, the editable [ImageCanvas] in
 * the center, and a status bar showing the saved/unsaved state.
 */
class ImageEditorPanel(
    private val file: VirtualFile,
    image: BufferedImage,
    private val onModifiedChanged: (Boolean) -> Unit,
) : JPanel(BorderLayout()) {

    private val canvas = ImageCanvas(image)

    private val toolButtons = mutableMapOf<Tool, JToggleButton>()
    private lateinit var undoButton: JButton
    private lateinit var redoButton: JButton
    private lateinit var saveButton: JButton

    private val statusDot = StatusDot()
    private val statusLabel = JBLabel()

    private var swatchColor: Color = canvas.currentColor
    private val swatch = ColorSwatch()

    private var dirty = false

    val preferredFocus: JComponent get() = canvas

    init {
        canvas.colorListener = { updateSwatch(it) }
        canvas.editListener = { onContentEdited() }
        canvas.onActiveToolChanged = { tool -> toolButtons[tool]?.isSelected = true }

        add(buildPalette(), BorderLayout.WEST)
        add(canvas, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)

        getInputMap(WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveImage")
        actionMap.put("saveImage", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = save()
        })

        refreshHistoryButtons()
        updateSaveState(clean = true)
    }

    // ---- palette --------------------------------------------------------------------------------

    private fun buildPalette(): JComponent {
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 5)
        }

        column.add(sectionLabel("Color"))
        column.add(swatch)
        column.add(Box.createVerticalStrut(JBUI.scale(10)))

        column.add(sectionLabel("Tools"))
        val group = ButtonGroup()
        canvas.tools.forEach { tool ->
            val button = toolButton(tool, group)
            toolButtons[tool] = button
            column.add(button)
            column.add(Box.createVerticalStrut(JBUI.scale(2)))
        }
        column.add(Box.createVerticalStrut(JBUI.scale(8)))

        column.add(sectionLabel("Edit"))
        undoButton = actionButton(AllIcons.Actions.Undo, "Undo", "Ctrl+Z") { canvas.document.undo() }
        redoButton = actionButton(AllIcons.Actions.Redo, "Redo", "Ctrl+Shift+Z") { canvas.document.redo() }
        saveButton = actionButton(AllIcons.Actions.MenuSaveall, "Save", "Ctrl+S") { save() }
        column.add(undoButton)
        column.add(Box.createVerticalStrut(JBUI.scale(2)))
        column.add(redoButton)
        column.add(Box.createVerticalStrut(JBUI.scale(2)))
        column.add(saveButton)

        // Anchor everything to the top and add a divider against the canvas.
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1)
            add(column, BorderLayout.NORTH)
        }
    }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(2, 1)
    }

    private fun toolButton(tool: Tool, group: ButtonGroup): JToggleButton =
        JToggleButton(tool.icon).apply {
            toolTipText = tooltip(tool.displayName, SHORTCUTS[tool.id], tool.description)
            isSelected = tool == canvas.activeTool
            squareIconButton(this)
            addActionListener { canvas.activeTool = tool }
            group.add(this)
        }

    private fun actionButton(icon: Icon, name: String, shortcut: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip(name, shortcut, null)
            squareIconButton(this)
            addActionListener { action() }
        }

    private fun squareIconButton(button: javax.swing.AbstractButton) {
        val size = JBUI.size(BUTTON_SIZE, BUTTON_SIZE)
        button.preferredSize = size
        button.minimumSize = size
        button.maximumSize = size
        button.alignmentX = Component.LEFT_ALIGNMENT
        button.margin = JBUI.emptyInsets()
        button.isFocusable = false
    }

    private fun tooltip(name: String, shortcut: String?, description: String?): String = buildString {
        append("<html><b>").append(name).append("</b>")
        if (shortcut != null) append(" &nbsp;<font color='#888888'>").append(shortcut).append("</font>")
        if (description != null) append("<br>").append(description)
        append("</html>")
    }

    // ---- status bar -----------------------------------------------------------------------------

    private fun buildStatusBar(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(3))).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(1, 6),
        )
        add(statusDot)
        add(statusLabel)
    }

    // ---- color ----------------------------------------------------------------------------------

    private fun chooseColor() {
        val chosen = JColorChooser.showDialog(this, "Foreground Color", canvas.currentColor) ?: return
        canvas.setCurrentColor(chosen)
        updateSwatch(chosen)
    }

    private fun updateSwatch(color: Color) {
        swatchColor = color
        swatch.repaint()
    }

    // ---- save / dirty state ---------------------------------------------------------------------

    private fun onContentEdited() {
        updateSaveState(clean = false)
        refreshHistoryButtons()
    }

    private fun refreshHistoryButtons() {
        undoButton.isEnabled = canvas.document.canUndo
        redoButton.isEnabled = canvas.document.canRedo
    }

    private fun updateSaveState(clean: Boolean) {
        val newDirty = !clean
        if (newDirty != dirty) {
            dirty = newDirty
            onModifiedChanged(dirty)
        }
        saveButton.isEnabled = dirty
        statusDot.color = if (dirty) UNSAVED_COLOR else SAVED_COLOR
        statusDot.repaint()
        statusLabel.text = if (dirty) "Unsaved changes" else "Saved"
        statusLabel.foreground = if (dirty) UNSAVED_COLOR else JBColor.GRAY
    }

    private fun save() {
        try {
            val ext = file.extension?.lowercase()
            val format = when (ext) {
                "jpg", "jpeg" -> "jpg"
                "bmp" -> "bmp"
                "wbmp" -> "wbmp"
                else -> "png"
            }
            // Formats without an alpha channel must be flattened first or the encoder fails.
            val out = if (format == "png") canvas.document.image else flatten(canvas.document.image)
            val bytes = ByteArrayOutputStream().use { stream ->
                if (!ImageIO.write(out, format, stream)) {
                    Messages.showErrorDialog(this, "No image encoder for .$ext files.", "Save Image")
                    return
                }
                stream.toByteArray()
            }
            WriteAction.run<IOException> { file.setBinaryContent(bytes) }
            updateSaveState(clean = true)
        } catch (t: Throwable) {
            Messages.showErrorDialog(this, t.message ?: "Failed to save image.", "Save Image")
        }
    }

    private fun flatten(img: BufferedImage): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return out
    }

    /** The foreground-color well; click to open the color chooser. */
    private inner class ColorSwatch : JPanel() {
        init {
            alignmentX = Component.LEFT_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = tooltip("Foreground color", null, "Click to change")
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = chooseColor()
            })
        }

        override fun getPreferredSize() = JBUI.size(BUTTON_SIZE, BUTTON_SIZE)
        override fun getMinimumSize() = preferredSize
        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            // Checkerboard behind the swatch so a transparent color reads as transparent.
            CanvasRender.checkerboard(g as java.awt.Graphics2D, java.awt.Rectangle(0, 0, width, height))
            g.color = swatchColor
            g.fillRect(0, 0, width, height)
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    /** A small filled circle used as the saved/unsaved indicator. */
    private class StatusDot : JComponent() {
        var color: Color = JBColor.GRAY

        override fun getPreferredSize() = JBUI.size(10, 10)

        override fun paintComponent(g: Graphics) {
            val g2 = g as java.awt.Graphics2D
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            val d = JBUI.scale(8)
            g2.color = color
            g2.fillOval((width - d) / 2, (height - d) / 2, d, d)
        }
    }

    companion object {
        private const val BUTTON_SIZE = 30

        private val SHORTCUTS = mapOf("pick" to "I", "select" to "M", "move" to "V", "pencil" to "B", "eraser" to "E")

        private val SAVED_COLOR = JBColor(Color(0x3C, 0x8B, 0x3C), Color(0x59, 0xA8, 0x69))
        private val UNSAVED_COLOR = JBColor(Color(0xB8, 0x6A, 0x00), Color(0xD8, 0x95, 0x16))
    }
}
