package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

enum class EditorTool { PICK, SELECT, MOVE, PENCIL, ERASER }

/**
 * Editable image canvas: nearest-neighbor rendering plus a small set of basic editing tools
 * (eyedropper, rectangular select, move, pencil, eraser) with undo/redo.
 *
 * Left mouse = active tool, middle-drag = pan, wheel = zoom.
 */
class ImageCanvas(source: BufferedImage) : JComponent() {

    var image: BufferedImage = toArgb(source)
        private set

    var tool: EditorTool = EditorTool.PENCIL
        set(value) {
            field = value
            updateCursor()
        }

    var currentColor: Color = JBColor.black
        private set

    /** Notified when the active color changes (e.g. the eyedropper picked one), to sync the swatch. */
    var colorListener: ((Color) -> Unit)? = null

    /** Notified whenever the image is mutated, so the host can flag the file as modified. */
    var editListener: (() -> Unit)? = null

    private var zoom = 1.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var userInteracted = false

    private val undoStack = ArrayDeque<BufferedImage>()
    private val redoStack = ArrayDeque<BufferedImage>()

    private var selection: Rectangle? = null
    private var selStart: Point? = null

    private var panStart: Point? = null
    private var panOffX = 0.0
    private var panOffY = 0.0

    private var lastPix: Point? = null

    private var floating: BufferedImage? = null
    private var floatPos = Point()
    private var floatStartComp: Point? = null
    private var floatOrigin = Point()

    init {
        isOpaque = true
        isFocusable = true

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
            zoomAt(e.x.toDouble(), e.y.toDouble(), factor)
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (userInteracted) repaint() else fit()
            }
        })

        bindKey(KeyEvent.VK_0, 0, "fit") { fit() }
        bindKey(KeyEvent.VK_1, 0, "actualSize") { setZoom(1.0) }
        bindKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, "undo") { undo() }
        bindKey(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, "redo") { redo() }
        bindKey(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "redo2") { redo() }
        bindKey(KeyEvent.VK_ESCAPE, 0, "deselect") { selection = null; repaint() }

        updateCursor()
    }

    override fun addNotify() {
        super.addNotify()
        if (!userInteracted) fit()
    }

    fun setCurrentColor(color: Color) {
        currentColor = color
        colorListener?.invoke(color)
    }

    // ---- input dispatch -------------------------------------------------------------------------

    private fun onPress(e: MouseEvent) {
        if (SwingUtilities.isMiddleMouseButton(e)) {
            panStart = e.point
            panOffX = offsetX
            panOffY = offsetY
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        when (tool) {
            EditorTool.PICK -> pickColor(e)
            EditorTool.SELECT -> {
                selStart = imagePoint(e)
                selection = Rectangle(selStart!!.x, selStart!!.y, 0, 0)
                repaint()
            }
            EditorTool.MOVE -> beginMove(e)
            EditorTool.PENCIL, EditorTool.ERASER -> {
                pushUndo()
                lastPix = null
                stroke(e)
            }
        }
    }

    private fun onDrag(e: MouseEvent) {
        if (panStart != null) {
            val s = panStart!!
            offsetX = panOffX + (e.x - s.x)
            offsetY = panOffY + (e.y - s.y)
            userInteracted = true
            repaint()
            return
        }
        when (tool) {
            EditorTool.PENCIL, EditorTool.ERASER -> stroke(e)
            EditorTool.SELECT -> updateSelection(e)
            EditorTool.MOVE -> dragMove(e)
            EditorTool.PICK -> {}
        }
    }

    private fun onRelease(e: MouseEvent) {
        if (panStart != null) {
            panStart = null
            return
        }
        when (tool) {
            EditorTool.SELECT -> finishSelection(e)
            EditorTool.MOVE -> commitMove()
            EditorTool.PENCIL, EditorTool.ERASER -> lastPix = null
            EditorTool.PICK -> {}
        }
    }

    // ---- tools ----------------------------------------------------------------------------------

    private fun stroke(e: MouseEvent) {
        val p = imagePoint(e)
        val argb = if (tool == EditorTool.ERASER) 0 else currentColor.rgb
        val from = lastPix
        if (from == null) putPixel(p.x, p.y, argb) else drawLine(from, p, argb)
        lastPix = p
        markEdit()
        repaint()
    }

    private fun putPixel(x: Int, y: Int, argb: Int) {
        if (x < 0 || y < 0 || x >= image.width || y >= image.height) return
        val sel = selection
        if (sel != null && !sel.contains(x, y)) return
        image.setRGB(x, y, argb)
    }

    private fun drawLine(a: Point, b: Point, argb: Int) {
        var x0 = a.x
        var y0 = a.y
        val dx = abs(b.x - x0)
        val dy = -abs(b.y - y0)
        val sx = if (x0 < b.x) 1 else -1
        val sy = if (y0 < b.y) 1 else -1
        var err = dx + dy
        while (true) {
            putPixel(x0, y0, argb)
            if (x0 == b.x && y0 == b.y) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x0 += sx
            }
            if (e2 <= dx) {
                err += dx
                y0 += sy
            }
        }
    }

    private fun pickColor(e: MouseEvent) {
        val p = imagePoint(e)
        if (p.x < 0 || p.y < 0 || p.x >= image.width || p.y >= image.height) return
        setCurrentColor(Color(image.getRGB(p.x, p.y)))
    }

    private fun updateSelection(e: MouseEvent) {
        val s = selStart ?: return
        val c = imagePoint(e)
        selection = Rectangle(min(s.x, c.x), min(s.y, c.y), abs(c.x - s.x), abs(c.y - s.y))
        repaint()
    }

    private fun finishSelection(e: MouseEvent) {
        updateSelection(e)
        val bounds = Rectangle(0, 0, image.width, image.height)
        selection = selection?.intersection(bounds)?.takeIf { it.width > 0 && it.height > 0 }
        selStart = null
        repaint()
    }

    private fun beginMove(e: MouseEvent) {
        val region = (selection ?: Rectangle(0, 0, image.width, image.height))
            .intersection(Rectangle(0, 0, image.width, image.height))
        if (region.isEmpty) return
        pushUndo()
        floating = copyRegion(image, region)
        clearRegion(image, region)
        floatPos = Point(region.x, region.y)
        floatOrigin = Point(region.x, region.y)
        floatStartComp = e.point
        markEdit()
        repaint()
    }

    private fun dragMove(e: MouseEvent) {
        val start = floatStartComp ?: return
        val dx = ((e.x - start.x) / zoom).roundToInt()
        val dy = ((e.y - start.y) / zoom).roundToInt()
        floatPos = Point(floatOrigin.x + dx, floatOrigin.y + dy)
        repaint()
    }

    private fun commitMove() {
        val f = floating ?: return
        val g = image.createGraphics()
        g.drawImage(f, floatPos.x, floatPos.y, null)
        g.dispose()
        selection = Rectangle(floatPos.x, floatPos.y, f.width, f.height)
            .intersection(Rectangle(0, 0, image.width, image.height))
            .takeIf { it.width > 0 && it.height > 0 }
        floating = null
        floatStartComp = null
        markEdit()
        repaint()
    }

    // ---- undo / redo ----------------------------------------------------------------------------

    private fun pushUndo() {
        undoStack.addLast(cloneImage(image))
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(cloneImage(image))
        image = undoStack.removeLast()
        floating = null
        markEdit()
        repaint()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(cloneImage(image))
        image = redoStack.removeLast()
        floating = null
        markEdit()
        repaint()
    }

    // ---- view -----------------------------------------------------------------------------------

    fun fit() {
        val cw = width.coerceAtLeast(1)
        val ch = height.coerceAtLeast(1)
        zoom = min(cw.toDouble() / image.width, ch.toDouble() / image.height)
        offsetX = (cw - image.width * zoom) / 2.0
        offsetY = (ch - image.height * zoom) / 2.0
        userInteracted = false
        repaint()
    }

    private fun setZoom(target: Double) = zoomAt(width / 2.0, height / 2.0, target / zoom)

    private fun zoomAt(px: Double, py: Double, factor: Double) {
        val clamped = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = clamped / zoom
        offsetX = px - (px - offsetX) * applied
        offsetY = py - (py - offsetY) * applied
        zoom = clamped
        userInteracted = true
        repaint()
    }

    private fun imagePoint(e: MouseEvent): Point =
        Point(floor((e.x - offsetX) / zoom).toInt(), floor((e.y - offsetY) / zoom).toInt())

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            paintCheckerboard(g2)

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g2.drawImage(image, offsetX.roundToInt(), offsetY.roundToInt(),
                (image.width * zoom).roundToInt().coerceAtLeast(1),
                (image.height * zoom).roundToInt().coerceAtLeast(1), this)

            floating?.let { f ->
                g2.drawImage(f, (offsetX + floatPos.x * zoom).roundToInt(), (offsetY + floatPos.y * zoom).roundToInt(),
                    (f.width * zoom).roundToInt().coerceAtLeast(1),
                    (f.height * zoom).roundToInt().coerceAtLeast(1), this)
            }

            paintSelection(g2)
            paintInfo(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintCheckerboard(g2: Graphics2D) {
        val cell = JBUI.scale(8)
        g2.color = CHECKER_A
        g2.fillRect(0, 0, width, height)
        g2.color = CHECKER_B
        var y = 0
        var row = 0
        while (y < height) {
            var x = if (row % 2 == 0) 0 else cell
            while (x < width) {
                g2.fillRect(x, y, cell, cell)
                x += cell * 2
            }
            y += cell
            row++
        }
    }

    private fun paintSelection(g2: Graphics2D) {
        val r = floating?.let { Rectangle(floatPos.x, floatPos.y, it.width, it.height) } ?: selection ?: return
        val x = (offsetX + r.x * zoom).roundToInt()
        val y = (offsetY + r.y * zoom).roundToInt()
        val w = (r.width * zoom).roundToInt()
        val h = (r.height * zoom).roundToInt()
        g2.color = JBColor.black
        g2.stroke = BasicStroke(1f)
        g2.drawRect(x, y, w, h)
        val dash = JBUI.scale(4).toFloat()
        g2.color = JBColor.white
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(dash, dash), 0f)
        g2.drawRect(x, y, w, h)
    }

    private fun paintInfo(g2: Graphics2D) {
        val label = "${image.width}×${image.height}   ${(zoom * 100).roundToInt()}%   ${tool.name.lowercase()}"
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

    override fun imageUpdate(img: java.awt.Image?, infoflags: Int, x: Int, y: Int, w: Int, h: Int): Boolean = false

    private fun updateCursor() {
        cursor = when (tool) {
            EditorTool.MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            else -> Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        }
    }

    private fun markEdit() {
        editListener?.invoke()
    }

    private fun bindKey(keyCode: Int, modifiers: Int, name: String, action: () -> Unit) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, modifiers), name)
        actionMap.put(name, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = action()
        })
    }

    companion object {
        private const val ZOOM_STEP = 1.2
        private const val MIN_ZOOM = 0.02
        private const val MAX_ZOOM = 64.0
        private const val UNDO_LIMIT = 40

        private val CHECKER_A = JBColor(Color(0xCB, 0xCB, 0xCB), Color(0x3C, 0x3C, 0x3C))
        private val CHECKER_B = JBColor(Color(0xF0, 0xF0, 0xF0), Color(0x4A, 0x4A, 0x4A))

        private fun toArgb(src: BufferedImage): BufferedImage {
            val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.drawImage(src, 0, 0, null)
            g.dispose()
            return out
        }

        private fun cloneImage(src: BufferedImage): BufferedImage = toArgb(src)

        private fun copyRegion(src: BufferedImage, r: Rectangle): BufferedImage {
            val out = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.drawImage(src, 0, 0, r.width, r.height, r.x, r.y, r.x + r.width, r.y + r.height, null)
            g.dispose()
            return out
        }

        private fun clearRegion(img: BufferedImage, r: Rectangle) {
            val g = img.createGraphics()
            g.composite = AlphaComposite.Clear
            g.fillRect(r.x, r.y, r.width, r.height)
            g.dispose()
        }
    }
}
