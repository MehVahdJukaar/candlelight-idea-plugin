package net.mehvahdjukaar.candle.imageviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.tools.Tool
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A compact image editor. The left dock stacks a palette of the image's colors, an embedded color
 * picker, a 3-column tool palette and the edit actions; its width is draggable against the canvas.
 * A status bar along the bottom shows the saved/unsaved state.
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

    private val paletteWidget = PaletteWidget()
    private val colorPicker = ColorPickerWidget(canvas.currentColor)

    // Rescanning the image on every painted pixel would be wasteful, so coalesce edits.
    private val paletteTimer = Timer(250) { refreshPalette() }.apply { isRepeats = false }

    private var dirty = false

    val preferredFocus: JComponent get() = canvas

    init {
        canvas.colorListener = { colorPicker.setColorExternally(it) }
        canvas.editListener = { onContentEdited() }
        canvas.onActiveToolChanged = { tool -> toolButtons[tool]?.isSelected = true }

        colorPicker.onColorChanged = { canvas.setCurrentColor(it) }
        paletteWidget.onColorPicked = { canvas.setCurrentColor(it) }

        val root = JBSplitter(false, 0.24f).apply {
            firstComponent = buildDock()
            secondComponent = canvas
        }
        add(root, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)

        getInputMap(WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveImage")
        actionMap.put("saveImage", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = save()
        })

        refreshHistoryButtons()
        updateSaveState(clean = true)
        refreshPalette()
    }

    // ---- dock -----------------------------------------------------------------------------------

    private fun buildDock(): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 5)
        }

        content.add(sectionLabel("Palette"))
        content.add(leftAligned(paletteWidget))
        content.add(Box.createVerticalStrut(JBUI.scale(8)))

        content.add(sectionLabel("Color"))
        content.add(leftAligned(colorPicker))
        content.add(Box.createVerticalStrut(JBUI.scale(8)))

        content.add(sectionLabel("Tools"))
        val group = ButtonGroup()
        val toolBtns = canvas.tools.map { tool -> toolButton(tool, group).also { toolButtons[tool] = it } }
        content.add(grid(toolBtns, COLUMNS))
        content.add(Box.createVerticalStrut(JBUI.scale(8)))

        content.add(sectionLabel("Edit"))
        undoButton = actionButton(AllIcons.Actions.Undo, "Undo", "Ctrl+Z") { canvas.document.undo() }
        redoButton = actionButton(AllIcons.Actions.Redo, "Redo", "Ctrl+Shift+Z") { canvas.document.redo() }
        saveButton = actionButton(AllIcons.Actions.MenuSaveall, "Save", "Ctrl+S") { save() }
        content.add(grid(listOf(undoButton, redoButton, saveButton), COLUMNS))

        val holder = JPanel(BorderLayout()).apply { add(content, BorderLayout.NORTH) }
        return JBScrollPane(holder).apply { border = JBUI.Borders.empty() }
    }

    /** Wraps [c] so it stays left-aligned and full-width inside the vertical [BoxLayout] dock. */
    private fun leftAligned(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }

    /** Lays [components] out left-to-right, [cols] per row, keeping their natural size. */
    private fun grid(components: List<JComponent>, cols: Int): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        components.chunked(cols).forEach { add(row(it)) }
    }

    private fun row(components: List<JComponent>): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(2))).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            components.forEach { add(it) }
        }

    private fun sectionLabel(text: String): JComponent = JLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(2, 1)
    }

    private fun toolButton(tool: Tool, group: ButtonGroup): JToggleButton =
        ToolToggleButton(tool.icon).apply {
            toolTipText = tooltip(tool.displayName, SHORTCUTS[tool.id], tool.description)
            isSelected = tool == canvas.activeTool
            squareIconButton(this)
            addActionListener { canvas.activeTool = tool }
            group.add(this)
        }

    private fun actionButton(icon: Icon, name: String, shortcut: String, action: () -> Unit): JButton =
        FlatActionButton(icon).apply {
            toolTipText = tooltip(name, shortcut, null)
            squareIconButton(this)
            addActionListener { action() }
        }

    private fun squareIconButton(button: AbstractButton) {
        val size = JBUI.size(BUTTON_SIZE, BUTTON_SIZE)
        button.preferredSize = size
        button.minimumSize = size
        button.maximumSize = size
        button.margin = JBUI.emptyInsets()
        button.isFocusable = false
    }

    private fun tooltip(name: String, shortcut: String?, description: String?): String = buildString {
        append("<html><b>").append(name).append("</b>")
        if (shortcut != null) append(" &nbsp;<font color='#888888'>").append(shortcut).append("</font>")
        if (description != null) append("<br>").append(description)
        append("</html>")
    }

    // ---- palette --------------------------------------------------------------------------------

    private fun refreshPalette() = paletteWidget.setColors(extractPalette(canvas.document.image))

    /** Distinct opaque colors in the image, most frequent first then capped, ordered by hue. */
    private fun extractPalette(img: BufferedImage): List<Color> {
        val counts = HashMap<Int, Int>()
        val total = img.width.toLong() * img.height
        val step = if (total > MAX_SCAN) ceil(sqrt(total.toDouble() / MAX_SCAN)).toInt() else 1
        var y = 0
        while (y < img.height) {
            var x = 0
            while (x < img.width) {
                val argb = img.getRGB(x, y)
                if ((argb ushr 24) and 0xFF >= 128) {
                    val rgb = argb or (0xFF shl 24)
                    counts[rgb] = (counts[rgb] ?: 0) + 1
                }
                x += step
            }
            y += step
        }
        return counts.entries.asSequence()
            .sortedByDescending { it.value }
            .take(MAX_PALETTE)
            .map { Color(it.key) }
            .sortedWith(compareBy({ hue(it) }, { brightness(it) }))
            .toList()
    }

    private fun hue(c: Color) = Color.RGBtoHSB(c.red, c.green, c.blue, null)[0]
    private fun brightness(c: Color) = Color.RGBtoHSB(c.red, c.green, c.blue, null)[2]

    // ---- status bar -----------------------------------------------------------------------------

    private fun buildStatusBar(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(3))).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(1, 6),
        )
        add(statusDot)
        add(statusLabel)
    }

    // ---- save / dirty state ---------------------------------------------------------------------

    private fun onContentEdited() {
        updateSaveState(clean = false)
        refreshHistoryButtons()
        paletteTimer.restart()
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

    /** Paints the native IntelliJ flat-toolbar background (hover / pressed) behind an icon button. */
    private fun paintFlatBackground(g: Graphics, button: AbstractButton, selected: Boolean) {
        val model = button.model
        val active = selected || model.isPressed || model.isArmed
        val hovered = model.isRollover
        if (!active && !hovered) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(6)
            g2.color = if (active) JBUI.CurrentTheme.ActionButton.pressedBackground()
            else JBUI.CurrentTheme.ActionButton.hoverBackground()
            g2.fillRoundRect(0, 0, button.width, button.height, arc, arc)
            if (selected) {
                g2.color = JBUI.CurrentTheme.ActionButton.pressedBorder()
                g2.drawRoundRect(0, 0, button.width - 1, button.height - 1, arc, arc)
            }
        } finally {
            g2.dispose()
        }
    }

    /** Flat icon toggle button whose selected state is clearly highlighted (the active tool). */
    private inner class ToolToggleButton(icon: Icon) : JToggleButton(icon) {
        init {
            isContentAreaFilled = false
            isBorderPainted = false
            isRolloverEnabled = true
        }

        override fun paintComponent(g: Graphics) {
            paintFlatBackground(g, this, isSelected)
            super.paintComponent(g)
        }
    }

    /** Flat icon action button matching the tool buttons' look (hover only, no selected state). */
    private inner class FlatActionButton(icon: Icon) : JButton(icon) {
        init {
            isContentAreaFilled = false
            isBorderPainted = false
            isRolloverEnabled = true
        }

        override fun paintComponent(g: Graphics) {
            paintFlatBackground(g, this, selected = false)
            super.paintComponent(g)
        }
    }

    /** A small filled circle used as the saved/unsaved indicator. */
    private class StatusDot : JComponent() {
        var color: Color = JBColor.GRAY

        override fun getPreferredSize() = JBUI.size(10, 10)

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val d = JBUI.scale(8)
            g2.color = color
            g2.fillOval((width - d) / 2, (height - d) / 2, d, d)
        }
    }

    companion object {
        private const val BUTTON_SIZE = 30
        private const val COLUMNS = 3
        private const val MAX_PALETTE = 48
        private const val MAX_SCAN = 200_000L

        private val SHORTCUTS = mapOf("pick" to "I", "select" to "M", "move" to "V", "pencil" to "B", "eraser" to "E")

        private val SAVED_COLOR = JBColor(Color(0x3C, 0x8B, 0x3C), Color(0x59, 0xA8, 0x69))
        private val UNSAVED_COLOR = JBColor(Color(0xB8, 0x6A, 0x00), Color(0xD8, 0x95, 0x16))
    }
}
