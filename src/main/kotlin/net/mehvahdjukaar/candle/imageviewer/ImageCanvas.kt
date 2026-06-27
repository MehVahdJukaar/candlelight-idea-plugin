package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.mehvahdjukaar.candle.imageviewer.tools.EyedropperTool
import net.mehvahdjukaar.candle.imageviewer.tools.HandTool
import net.mehvahdjukaar.candle.imageviewer.tools.MoveTool
import net.mehvahdjukaar.candle.imageviewer.tools.PencilTool
import net.mehvahdjukaar.candle.imageviewer.tools.RecolorTool
import net.mehvahdjukaar.candle.imageviewer.tools.SelectTool
import net.mehvahdjukaar.candle.imageviewer.tools.Tool
import net.mehvahdjukaar.candle.imageviewer.tools.ToolContext
import net.mehvahdjukaar.candle.imageviewer.tools.ToolCursors
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
import kotlin.math.pow
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
        EyedropperTool(), SelectTool(), MoveTool(), PencilTool(erase = false), PencilTool(erase = true),
        RecolorTool(), ZoomTool(), HandTool(),
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

    /** Square brush side length, in image pixels, applied to the pencil and eraser. */
    var brushSize: Int = 1
        set(value) {
            field = value.coerceIn(1, MAX_BRUSH)
            tools.filterIsInstance<PencilTool>().forEach { it.brushSize = field }
            repaint()
        }

    var colorListener: ((Color) -> Unit)? = null
    var editListener: (() -> Unit)? = null

    private var panLast: Point? = null

    /** Mouse position (component space) while hovering, for the active tool's hover preview. */
    private var hoverPoint: Point? = null

    /** True while the space bar is held: any tool temporarily pans, Photoshop-style. */
    private var spacePanning = false

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

            override fun mouseDragged(e: MouseEvent) {
                hoverPoint = e.point
                onDrag(e)
            }

            override fun mouseReleased(e: MouseEvent) = onRelease(e)

            override fun mouseMoved(e: MouseEvent) {
                hoverPoint = e.point
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                hoverPoint = null
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)

        // Hold space to temporarily pan with any tool; release restores the tool cursor.
        addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE && !spacePanning) {
                    spacePanning = true
                    cursor = ToolCursors.hand()
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE) {
                    spacePanning = false
                    cursor = activeTool.cursor
                }
            }
        })
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) {
                // A key-release can be missed when focus leaves mid-pan; reset so we don't get stuck.
                if (spacePanning) {
                    spacePanning = false
                    cursor = activeTool.cursor
                }
            }
        })

        addMouseWheelListener { e ->
            if (e.isShiftDown) {
                // Shift + wheel pans horizontally, matching most image editors.
                viewport.pan(-e.preciseWheelRotation * WHEEL_PAN_STEP, 0.0)
            } else {
                // Scale the step by the precise rotation so trackpads (many small events) move
                // proportionally less per tick rather than a full zoom step each time.
                val factor = WHEEL_ZOOM_STEP.pow(-e.preciseWheelRotation)
                viewport.zoomAt(e.x.toDouble(), e.y.toDouble(), factor)
            }
            clampView()
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
        bindKey(KeyEvent.VK_G, 0, "tool.recolor", WHEN_IN_FOCUSED_WINDOW) { selectTool("recolor") }
        bindKey(KeyEvent.VK_Z, 0, "tool.zoom", WHEN_IN_FOCUSED_WINDOW) { selectTool("zoom") }
        bindKey(KeyEvent.VK_H, 0, "tool.hand", WHEN_IN_FOCUSED_WINDOW) { selectTool("hand") }

        // ---- zoom ---------------------------------------------------------------------------
        bindKey(KeyEvent.VK_0, 0, "fit", WHEN_IN_FOCUSED_WINDOW) { fitToWindow() }
        bindKey(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "fit.ctrl", WHEN_IN_FOCUSED_WINDOW) { fitToWindow() }
        bindKey(KeyEvent.VK_1, 0, "actualSize", WHEN_IN_FOCUSED_WINDOW) { actualSize() }
        bindKey(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "actualSize.ctrl", WHEN_IN_FOCUSED_WINDOW) { actualSize() }
        bindKey(KeyEvent.VK_PLUS, 0, "zoomIn", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_EQUALS, 0, "zoomIn2", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_MINUS, 0, "zoomOut", WHEN_IN_FOCUSED_WINDOW) { zoomAtCenter(1.0 / ZOOM_STEP) }

        // ---- pan & recenter -----------------------------------------------------------------
        val step = JBUI.scale(PAN_STEP)
        bindKey(KeyEvent.VK_LEFT, 0, "pan.left") { pan(step, 0) }
        bindKey(KeyEvent.VK_RIGHT, 0, "pan.right") { pan(-step, 0) }
        bindKey(KeyEvent.VK_UP, 0, "pan.up") { pan(0, step) }
        bindKey(KeyEvent.VK_DOWN, 0, "pan.down") { pan(0, -step) }
        bindKey(KeyEvent.VK_HOME, 0, "recenter", WHEN_IN_FOCUSED_WINDOW) { recenter() }

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
        clampView()
        repaint()
    }

    private fun zoomAtCenter(factor: Double) {
        viewport.zoomAt(width / 2.0, height / 2.0, factor)
        clampView()
        repaint()
    }

    private fun pan(dx: Int, dy: Int) {
        viewport.pan(dx.toDouble(), dy.toDouble())
        clampView()
        repaint()
    }

    /** Re-centers the image at the current zoom without changing the zoom level. */
    private fun recenter() {
        viewport.center(width, height, document.width, document.height)
        repaint()
    }

    /** Keeps the image from being scrolled completely out of view. */
    private fun clampView() {
        viewport.clamp(width, height, document.width, document.height, JBUI.scale(KEEP_VISIBLE))
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

    fun zoomIn() = zoomAtCenter(ZOOM_STEP)
    fun zoomOut() = zoomAtCenter(1.0 / ZOOM_STEP)

    /** Re-centers the image at the current zoom without changing the zoom level. */
    fun centerView() = recenter()

    // ---- input ----------------------------------------------------------------------------------

    private fun toolContext(e: MouseEvent) =
        ToolContext(document, viewport, viewport.toImage(e.x, e.y), e.point, e.isAltDown, currentColor, ::setCurrentColor)

    private fun onPress(e: MouseEvent) {
        if (SwingUtilities.isMiddleMouseButton(e) || (spacePanning && SwingUtilities.isLeftMouseButton(e))) {
            panLast = e.point
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        activeTool.onPress(toolContext(e))
        clampView()
        repaint()
    }

    private fun onDrag(e: MouseEvent) {
        val pan = panLast
        if (pan != null) {
            viewport.pan((e.x - pan.x).toDouble(), (e.y - pan.y).toDouble())
            panLast = e.point
            clampView()
            repaint()
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        activeTool.onDrag(toolContext(e))
        clampView()
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
            // The brush-outline hover preview is meaningless while the space-pan grab is active.
            if (!spacePanning) hoverPoint?.let { activeTool.paintHover(g2, viewport, viewport.toImage(it.x, it.y)) }
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

        // Gentler per-notch factor for the wheel; raised to the precise rotation so a single notch
        // zooms ~10% rather than 20%, taming fast/high-resolution wheels and trackpads.
        private const val WHEEL_ZOOM_STEP = 1.1

        // Pixels panned per shift+wheel notch and per arrow-key press.
        private const val WHEEL_PAN_STEP = 40.0
        private const val PAN_STEP = 40

        // How many pixels of the image must always stay visible so it can never be lost off-screen.
        private const val KEEP_VISIBLE = 32

        // Largest square-brush side length the size slider allows.
        const val MAX_BRUSH = 32
    }
}
