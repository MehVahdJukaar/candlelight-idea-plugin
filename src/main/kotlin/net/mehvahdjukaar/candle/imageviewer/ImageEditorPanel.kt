package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
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
import javax.swing.BoxLayout
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingConstants

/** A basic image editor: a tool palette on the left and the editable [ImageCanvas] in the center. */
class ImageEditorPanel(
    private val file: VirtualFile,
    image: BufferedImage,
    private val onModifiedChanged: (Boolean) -> Unit,
) : JPanel(BorderLayout()) {

    private val canvas = ImageCanvas(image)
    private var swatchColor: Color = canvas.currentColor

    private val swatch = object : JPanel() {
        override fun getPreferredSize() = Dimension(JBUI.scale(30), JBUI.scale(30))
        override fun getMaximumSize() = preferredSize

        override fun paintComponent(g: Graphics) {
            g.color = swatchColor
            g.fillRect(0, 0, width, height)
            g.color = JBColor.border()
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    val preferredFocus: JComponent get() = canvas

    init {
        canvas.colorListener = { updateSwatch(it) }
        canvas.editListener = { onModifiedChanged(true) }

        add(buildToolbar(), BorderLayout.WEST)
        add(canvas, BorderLayout.CENTER)

        getInputMap(WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveImage")
        actionMap.put("saveImage", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = save()
        })
    }

    private fun buildToolbar(): JComponent {
        val bar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6)
        }

        bar.add(sectionLabel("Color"))
        swatch.toolTipText = "Foreground color — click to change"
        swatch.alignmentX = Component.LEFT_ALIGNMENT
        swatch.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = chooseColor()
        })
        bar.add(swatch)
        bar.add(Box.createVerticalStrut(JBUI.scale(10)))

        bar.add(sectionLabel("Tools"))
        val group = ButtonGroup()
        bar.add(toolButton("Pick", EditorTool.PICK, group, "Eyedropper — pick a color from the image"))
        bar.add(toolButton("Select", EditorTool.SELECT, group, "Rectangular selection (Esc clears it)"))
        bar.add(toolButton("Move", EditorTool.MOVE, group, "Move the selection, or the whole image if none"))
        bar.add(toolButton("Pencil", EditorTool.PENCIL, group, "Draw single pixels with the foreground color"))
        bar.add(toolButton("Eraser", EditorTool.ERASER, group, "Erase pixels to transparent"))
        bar.add(Box.createVerticalStrut(JBUI.scale(10)))

        bar.add(sectionLabel("Edit"))
        bar.add(actionButton("Undo", "Undo (Ctrl+Z)") { canvas.undo() })
        bar.add(actionButton("Redo", "Redo (Ctrl+Y)") { canvas.redo() })
        bar.add(actionButton("Save", "Save to file (Ctrl+S)") { save() })

        // Keep everything anchored at the top.
        return JPanel(BorderLayout()).apply { add(bar, BorderLayout.NORTH) }
    }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        font = font.deriveFont(font.size2D - 1f)
        foreground = JBColor.GRAY
        border = JBUI.Borders.emptyBottom(2)
    }

    private fun toolButton(text: String, tool: EditorTool, group: ButtonGroup, tip: String): JToggleButton =
        JToggleButton(text).apply {
            toolTipText = tip
            horizontalAlignment = SwingConstants.LEFT
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            isSelected = tool == canvas.tool
            addActionListener { canvas.tool = tool }
            group.add(this)
        }

    private fun actionButton(text: String, tip: String, action: () -> Unit): JButton =
        JButton(text).apply {
            toolTipText = tip
            horizontalAlignment = SwingConstants.LEFT
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            addActionListener { action() }
        }

    private fun chooseColor() {
        val chosen = JColorChooser.showDialog(this, "Pick Color", canvas.currentColor) ?: return
        canvas.setCurrentColor(chosen)
        updateSwatch(chosen)
    }

    private fun updateSwatch(color: Color) {
        swatchColor = color
        swatch.repaint()
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
            val out = if (format == "png") canvas.image else flatten(canvas.image)
            val bytes = ByteArrayOutputStream().use { stream ->
                if (!ImageIO.write(out, format, stream)) {
                    Messages.showErrorDialog(this, "No image encoder for .$ext files.", "Save Image")
                    return
                }
                stream.toByteArray()
            }
            WriteAction.run<IOException> { file.setBinaryContent(bytes) }
            onModifiedChanged(false)
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
}
