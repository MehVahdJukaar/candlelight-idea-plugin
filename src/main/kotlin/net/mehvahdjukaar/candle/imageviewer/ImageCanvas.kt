package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.mehvahdjukaar.candle.imageviewer.tools.EyedropperTool
import net.mehvahdjukaar.candle.imageviewer.tools.MoveTool
import net.mehvahdjukaar.candle.imageviewer.tools.PencilTool
import net.mehvahdjukaar.candle.imageviewer.tools.SelectTool
import net.mehvahdjukaar.candle.imageviewer.tools.Tool
import net.mehvahdjukaar.candle.imageviewer.tools.ToolContext
import net.mehvahdjukaar.candle.imageviewer.tools.ZoomTool
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * The editing surface: a thin coordinator that owns the [ImageDocument] (model), a [Viewport] (view
 * transform) and the set of [Tool]s, and routes mouse/keyboard input to the active tool while
 * rendering the document. Editing logic lives in the tools; pixel logic lives in the document.
 */
class ImageCanvas(source: java.awt.image.BufferedImage) : JComponent() {

    val document = ImageDocument(source)
    private val viewport = Viewport()

    val tools: List<Tool> = listOf(
        EyedropperTool(), SelectTool(), MoveTool(), PencilTool(erase = false), PencilTool(erase = true), ZoomTool(),
    )

    var activeTool: Tool = tools.first { it.id == "pencil" }
        set(value) {
            field = value
            cursor = value.cursor
            onActiveToolChanged?.invoke(value)
        }

    /**
     * Invoked whenever [activeTool] changes (e.g. via a keyboard shortcut), after the cursor has
     * been updated, so external UI such as the toolbar can reflect the newly active tool.
     */
    var onActiveToolChanged: ((Tool) -> Unit)? = null

    var currentColor: Color = JBColor.black
        private set

    var colorListener: ((Color) -> Unit)? = null
    var editListener: (() -> Unit)? = null

    private var panLast: Point? = null

    init {
        isOpaque = true
        isFocusable = true
        cursor = activeTool.cursor

        document.onContentChanged = {
            editListener?.invoke()
            repaint()
        }

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                onPress(e)
            }

            override fun mouseDragged(e: MouseEvent) = onDrag(e)
            override fun mouseReleased(e: MouseEvent) = onRelease(e)
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)

        addMouseWheelListener { e ->
            val factor = if (e.preciseWheelRotation < 0) ZOOM_STEP else 1.0 / ZOOM_STEP
            viewport.zoomAt(e.x.toDouble(), e.y.toDouble(), factor)
            repaint()
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (viewport.userInteracted) repaint() else fitToWindow()
            }
        })

        bindKeybindings()
    }

    /**
     * Registers Photoshop-style keyboard shortcuts. Tool, zoom, undo/redo and deselect shortcuts are
     * bound editor-wide ([WHEN_IN_FOCUSED_WINDOW]) so they fire even when the canvas does not hold
     * keyboard focus (e.g. while the toolbar is focused).
     */
    private fun bindKeybindings() {
        // ---- tool selection -----------------------------------------------------------------
        bindKey(KeyEvent.VK_I, 0, "tool.pick", WHEN_IN_FOCUSED_WINDOW) { selectTool("pick") }
        bindKey(KeyEvent.VK_M, 0, "tool.select", WHEN_IN_FOCUSED_WINDOW) { selectTool("select") }
        bindKey(KeyEvent.VK_V, 0, "tool.move", WHEN_IN_FOCUSED_WINDOW) { selectTool("move") }
        bindKey(KeyEvent.VK_B, 0, "tool.pencil", WHEN_IN_FOCUSED_WINDOW) { selectTool("pencil") }
        bindKey(KeyEvent.VK_E, 0, "tool.eraser", WHEN_IN_FOCUSED_WINDOW) { selectTool("eraser") }
        bindKey(KeyEvent.VK_Z, 0, "tool.zoom", WHEN_IN_FOCUSED_WINDOW) { selectTool("zoom") }

        // ---- zoom ---------------------------------------------------------------------------
        bindKey(KeyEvent.VK_0, 0, "fit", WHEN_IN_FOCUSED_WINDOW) { fitToWindow() }
        bindKey(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "fit.ctrl", WHEN_IN_FOCUSED_WINDOW) { fitToWindow() }
        bindKey(KeyEvent.VK_1, 0, "actualSize", WHEN_IN_FOCUSED_WINDOW) { actualSize() }
        bindKey(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "actualSize.ctrl", WHEN_IN_FOCUSED_WINDOW) { actualSize() }
        bindKey(KeyEvent.VK_PLUS, 0, "zoomIn", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_EQUALS, 0, "zoomIn2", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_MINUS, 0, "zoomOut", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(1.0 / ZOOM_STEP) }

        // ---- history & selection ------------------------------------------------------------
        bindKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, "undo", WHEN_IN_FOCUSED_WINDOW) { document.undo() }
        bindKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, "redo", WHEN_IN_FOCUSED_WINDOW) { document.redo() }
        bindKey(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "redo2", WHEN_IN_FOCUSED_WINDOW) { document.redo() }
        bindKey(KeyEvent.VK_ESCAPE, 0, "deselect", WHEN_IN_FOCUSED_WINDOW) {
            document.selection = null
            repaint()
        }
    }

    private fun selectTool(id: String) {
        activeTool = tools.first { it.id == id }
    }

    private fun actualSize() {
        viewport.setZoom(1.0, width / 2.0, height / 2.0)
        repaint()
    }

    private fun zoomAtCenter(factor: Double) {
        viewport.zoomAt(width / 2.0, height / 2.0, factor)
        repaint()
    }

    override fun addNotify() {
        super.addNotify()
        if (!viewport.userInteracted) fitToWindow()
    }

    fun setCurrentColor(color: Color) {
        currentColor = color
        colorListener?.invoke(color)
    }

    fun fitToWindow() {
        viewport.fit(width, height, document.width, document.height)
        repaint()
    }

    // ---- input ----------------------------------------------------------------------------------

    private fun toolContext(e: MouseEvent) =
        ToolContext(document, viewport, viewport.toImage(e.x, e.y), e.point, e.isAltDown, currentColor, ::setCurrentColor)

    private fun onPress(e: MouseEvent) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
            panLast = e.point
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        activeTool.onPress(toolContext(e))
        repaint()
    }

    private fun onDrag(e: MouseEvent) {
        val pan = panLast
        if (pan != null) {
            viewport.pan((e.x - pan.x).toDouble(), (e.y - pan.y).toDouble())
            panLast = e.point
            repaint()
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        activeTool.onDrag(toolContext(e))
        repaint()
    }

    private fun onRelease(e: MouseEvent) {
        if (panLast != null) {
            panLast = null
            return
        }
        activeTool.onRelease(toolContext(e))
        repaint()
    }

    // ---- rendering ------------------------------------------------------------------------------

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = CanvasRender.CANVAS_BACKGROUND
            g2.fillRect(0, 0, width, height)

            val imageRect = viewport.imageRect(document.width, document.height)
            CanvasRender.checkerboard(g2, imageRect)

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g2.drawImage(document.image, imageRect.x, imageRect.y, imageRect.width, imageRect.height, this)

            g2.color = JBColor.border()
            g2.drawRect(imageRect.x, imageRect.y, imageRect.width, imageRect.height)

            activeTool.paintOverlay(g2, viewport)
            document.selection?.let { CanvasRender.selectionOutline(g2, viewport.toComponent(it)) }

            paintInfo(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintInfo(g2: Graphics2D) {
        val label = "${document.width}×${document.height}   ${(viewport.zoom * 100).roundToInt()}%   ${activeTool.displayName.lowercase()}"
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

    /**
     * Binds [keyCode] + [modifiers] to [action]. [condition] selects the input map: pass
     * [WHEN_IN_FOCUSED_WINDOW] to make the shortcut fire anywhere in the editor window, or the
     * default [WHEN_FOCUSED] to require canvas focus.
     */
    private fun bindKey(keyCode: Int, modifiers: Int, name: String, condition: Int = WHEN_FOCUSED, action: () -> Unit) {
        getInputMap(condition).put(KeyStroke.getKeyStroke(keyCode, modifiers), name)
        actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = action()
        })
    }

    companion object {
        private const val ZOOM_STEP = 1.2
    }
}
