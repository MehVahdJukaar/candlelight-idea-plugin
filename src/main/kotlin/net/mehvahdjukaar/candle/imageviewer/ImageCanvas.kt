package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.mehvahdjukaar.candle.imageviewer.tools.CropTool
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
import java.awt.Rectangle
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
import javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
import javax.swing.JComponent.WHEN_FOCUSED
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The editing surface: a thin coordinator that owns the [ImageDocument] (model), a [Viewport] (view
 * transform) and the set of [Tool]s, and routes mouse/keyboard input to the active tool while
 * rendering the document. Editing logic lives in the tools; pixel logic lives in the document.
 */
class ImageCanvas(
    source: java.awt.image.BufferedImage,
    initialFrames: Int = 1,
    initialDurationTicks: Int = Animation.DEFAULT_DURATION_TICKS,
) : JComponent() {

    val document = ImageDocument(source)
    private val viewport = Viewport()

    /** Frame-slicing/playback state; drives the dock's Animation section. */
    val animation = Animation().apply {
        reset(document.width, document.height, initialFrames)
        frameDurationTicks = initialDurationTicks
    }

    /** Running while the animation plays; null when paused. */
    private var playTimer: Timer? = null

    /** Advances the selection marching-ants while a region is selected; null otherwise. */
    private var selectionAnimTimer: Timer? = null
    private var selectionDashPhase = 0f

    /** Notifies the UI after the current frame or frame count changes: (currentFrame, frameCount). */
    var onAnimationChanged: ((Int, Int) -> Unit)? = null

    /** Notifies the UI when playback starts/stops, so the play/pause button can update. */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    val tools: List<Tool> = listOf(
        EyedropperTool(), SelectTool(), MoveTool(), PencilTool(erase = false), PencilTool(erase = true),
        RecolorTool(), CropTool(), ZoomTool(), HandTool(),
    )

    // Seeded from the session-shared tool so a tool picked in one image is active in the next too.
    var activeTool: Tool = tools.first { it.id == SharedEditorState.lastToolId }
        set(value) {
            // A pending paste rides the Move tool; leaving it merges the paste into the active layer.
            if (field.id == "move" && value.id != "move") commitPastePlacement()
            field = value
            value.onActivated(document)
            refreshCursor()
            // Don't echo a change we're applying from another editor's broadcast, or it would loop.
            if (!applyingSharedTool) SharedEditorState.setTool(value.id)
            onActiveToolChanged?.invoke(value)
        }

    /** True while applying a tool change broadcast by another editor, so we don't re-broadcast it. */
    private var applyingSharedTool = false

    /** Adopts a tool change broadcast by another editor (selected by id; no re-broadcast). */
    private val sharedToolListener: (String) -> Unit = { id ->
        if (id != activeTool.id) {
            tools.firstOrNull { it.id == id }?.let { tool ->
                applyingSharedTool = true
                try { activeTool = tool } finally { applyingSharedTool = false }
            }
        }
    }

    /** Adopts a color change broadcast by another editor (updates the picker; no re-broadcast). */
    private val sharedColorListener: (Color) -> Unit = { color ->
        if (color.rgb != currentColor.rgb) {
            currentColor = color
            colorListener?.invoke(color)
        }
    }

    private val eyedropper: Tool get() = tools.first { it.id == "pick" }

    /**
     * Invoked whenever [activeTool] changes (e.g. via a keyboard shortcut), after the cursor has
     * been updated, so external UI such as the toolbar can reflect the newly active tool.
     */
    var onActiveToolChanged: ((Tool) -> Unit)? = null

    // Seeded from the session-shared color so a color picked in one image carries into the next.
    var currentColor: Color = SharedEditorState.lastColor
        private set

    /** Square brush side length, in image pixels, applied to the pencil and eraser. */
    var brushSize: Int = 1
        set(value) {
            field = value.coerceIn(1, MAX_BRUSH)
            tools.filterIsInstance<PencilTool>().forEach { it.brushSize = field }
            onBrushSizeChanged?.invoke(field)
            repaint()
        }

    /** Notifies the UI (slider/readout) when the brush size changes via keyboard or Alt+wheel. */
    var onBrushSizeChanged: ((Int) -> Unit)? = null

    var colorListener: ((Color) -> Unit)? = null
    var editListener: (() -> Unit)? = null

    /** Notifies the UI when the selection changes, so copy/cut buttons can enable/disable. */
    var selectionListener: (() -> Unit)? = null

    /** Invoked from the canvas context menu's "Resize…" item; the panel wires this to its dialog. */
    var onResizeRequested: (() -> Unit)? = null

    private var panLast: Point? = null

    /** Mouse position (component space) while hovering, for the active tool's hover preview. */
    private var hoverPoint: Point? = null

    /** True while the space bar is held: any tool temporarily pans, Photoshop-style. */
    private var spacePanning = false

    /** True while Alt is held: paint tools temporarily sample color (the eyedropper). */
    private var altSampling = false

    /** The tool locked in at mouse-press, so a mid-stroke modifier flip can't switch tools. */
    private var strokeTool: Tool? = null

    /** Active while Alt + right-drag is resizing the brush, Photoshop-style; null otherwise. */
    private var brushResize: BrushResize? = null

    /** Clipboard pixels waiting to be placed; the image underneath stays untouched until confirmed. */
    private var pasteMoveGrab: Point? = null
    private var pasteMoveOrigin: Point? = null

    private class BrushResize(val anchor: Point, val startSize: Int)

    init {
        isOpaque = true
        isFocusable = true
        // Route through refreshCursor() so the initial tool (a brush) hides the OS cursor too,
        // instead of showing its glyph cursor until the next tool switch.
        refreshCursor()

        document.onContentChanged = {
            editListener?.invoke()
            repaint()
        }
        document.onSelectionChanged = {
            selectionListener?.invoke()
            updateSelectionAnimation()
        }
        document.onOverlayChanged = { repaint() }
        // A crop/resize (or undo/redo of one) changes the pixel dimensions: the frame slicing no
        // longer applies, so collapse to a single frame and re-fit the newly sized image in view.
        document.onDimensionsChanged = {
            pause()
            animation.reset(document.width, document.height, 1)
            notifyAnimation()
            viewport.fit(width, height, document.width, document.height)
        }

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                // Right-click opens the crop/resize context menu (Alt+right resizes the brush; Ctrl+right pans).
                if (e.isPopupTrigger && !e.isAltDown && !e.isControlDown) { showContextMenu(e); return }
                if (tryStartBrushResize(e)) return
                // Double-click commits a staged gesture (the crop box) without needing the keyboard.
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e) && activeTool.onCommit(document)) {
                    repaint()
                    return
                }
                onPress(e)
            }

            override fun mouseDragged(e: MouseEvent) {
                if (brushResize != null) { updateBrushResize(e); return }
                hoverPoint = e.point
                onDrag(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (brushResize != null) { brushResize = null; repaint(); return }
                // Some platforms fire the popup trigger on release rather than press.
                if (e.isPopupTrigger && !e.isAltDown && !e.isControlDown) { showContextMenu(e); return }
                onRelease(e)
            }

            override fun mouseMoved(e: MouseEvent) {
                hoverPoint = e.point
                if (shouldHideCursor()) refreshCursor()
                repaint()
            }

            override fun mouseEntered(e: MouseEvent) {
                refreshCursor()
            }

            override fun mouseExited(e: MouseEvent) {
                hoverPoint = null
                refreshCursor()
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)

        // Hold space to pan with any tool; hold Alt to temporarily sample color with a paint tool.
        addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_SPACE -> if (!spacePanning) { spacePanning = true; refreshCursor() }
                    KeyEvent.VK_ALT -> if (!altSampling) { altSampling = true; refreshCursor(); repaint() }
                }
            }

            override fun keyReleased(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_SPACE -> { spacePanning = false; refreshCursor() }
                    KeyEvent.VK_ALT -> { altSampling = false; refreshCursor(); repaint() }
                }
            }
        })
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) {
                // A key-release can be missed when focus leaves mid-gesture; reset so we don't stick.
                if (spacePanning || altSampling) {
                    spacePanning = false
                    altSampling = false
                    refreshCursor()
                    repaint()
                }
            }
        })

        addMouseWheelListener { e ->
            if (e.isControlDown) {
                // Ctrl + wheel zooms toward the pointer. Scale the step by the precise rotation so
                // trackpads (many small events) move proportionally less per tick.
                val factor = WHEEL_ZOOM_STEP.pow(-e.preciseWheelRotation)
                viewport.zoomAt(e.x.toDouble(), e.y.toDouble(), factor)
            } else if (e.isShiftDown) {
                // Shift + wheel pans horizontally, matching most image editors.
                viewport.pan(-e.preciseWheelRotation * WHEEL_PAN_STEP, 0.0)
            } else {
                // A plain wheel pans vertically; scrolling up moves the view up.
                viewport.pan(0.0, -e.preciseWheelRotation * WHEEL_PAN_STEP)
            }
            clampView()
            repaint()
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (viewport.userInteracted) repaint() else initialView()
            }
        })

        bindKeybindings()
    }

    /**
     * Registers Photoshop-style keyboard shortcuts. Tool, zoom, brush-size and deselect shortcuts are
     * scoped to [WHEN_ANCESTOR_OF_FOCUSED_COMPONENT] so they fire whenever focus is inside this image
     * editor but never leak to other editor tabs that merely share the window (a plain `m` typed in a
     * code tab must not switch this editor's tool). Undo/redo/save use real IDE shortcuts and are
     * registered separately by [ImageEditorPanel] so they take precedence over the platform.
     */
    private fun bindKeybindings() {
        // ---- tool selection -----------------------------------------------------------------
        bindKey(KeyEvent.VK_I, 0, "tool.pick", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("pick") }
        bindKey(KeyEvent.VK_M, 0, "tool.select", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("select") }
        bindKey(KeyEvent.VK_V, 0, "tool.move", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("move") }
        bindKey(KeyEvent.VK_B, 0, "tool.pencil", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("pencil") }
        bindKey(KeyEvent.VK_E, 0, "tool.eraser", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("eraser") }
        bindKey(KeyEvent.VK_G, 0, "tool.recolor", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("recolor") }
        bindKey(KeyEvent.VK_C, 0, "tool.crop", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("crop") }
        bindKey(KeyEvent.VK_Z, 0, "tool.zoom", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("zoom") }
        bindKey(KeyEvent.VK_H, 0, "tool.hand", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { selectTool("hand") }

        // ---- brush size ---------------------------------------------------------------------
        bindKey(KeyEvent.VK_OPEN_BRACKET, 0, "brush.smaller", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { brushSize-- }
        bindKey(KeyEvent.VK_CLOSE_BRACKET, 0, "brush.larger", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { brushSize++ }

        // ---- zoom ---------------------------------------------------------------------------
        bindKey(KeyEvent.VK_0, 0, "fit", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { fitToWindow() }
        bindKey(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK, "fit.ctrl", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { fitToWindow() }
        bindKey(KeyEvent.VK_1, 0, "actualSize", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { actualSize() }
        bindKey(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK, "actualSize.ctrl", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { actualSize() }
        bindKey(KeyEvent.VK_PLUS, 0, "zoomIn", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_EQUALS, 0, "zoomIn2", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { zoomAtCenter(ZOOM_STEP) }
        bindKey(KeyEvent.VK_MINUS, 0, "zoomOut", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { zoomAtCenter(1.0 / ZOOM_STEP) }

        // ---- pan & recenter -----------------------------------------------------------------
        val step = JBUI.scale(PAN_STEP)
        bindKey(KeyEvent.VK_LEFT, 0, "pan.left") { pan(step, 0) }
        bindKey(KeyEvent.VK_RIGHT, 0, "pan.right") { pan(-step, 0) }
        bindKey(KeyEvent.VK_UP, 0, "pan.up") { pan(0, step) }
        bindKey(KeyEvent.VK_DOWN, 0, "pan.down") { pan(0, -step) }
        bindKey(KeyEvent.VK_HOME, 0, "recenter", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { recenter() }

        // ---- selection & commit -------------------------------------------------------------
        bindKey(KeyEvent.VK_ESCAPE, 0, "deselect", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
            if (document.hasPendingPaste) {
                cancelPastePlacement()
                repaint()
                return@bindKey
            }
            // Let the active tool discard its pending gesture (e.g. the crop box) first; if it had
            // nothing staged, fall back to clearing the selection.
            if (!activeTool.onCancel(document)) document.selection = null
            repaint()
        }
        bindKey(KeyEvent.VK_ENTER, 0, "commit", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
            when {
                commitPastePlacement() -> repaint()
                activeTool.onCommit(document) -> repaint()
            }
        }
        bindKey(KeyEvent.VK_DELETE, 0, "deleteSelection", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { deleteSelection() }
        bindKey(KeyEvent.VK_BACK_SPACE, 0, "deleteSelection.backspace", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
            deleteSelection()
        }
        bindKey(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, "paste", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) { paste(newLayer = false) }
        bindKey(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, "paste.newLayer", WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
            paste(newLayer = true)
        }
    }

    private fun selectTool(id: String) {
        activeTool = tools.first { it.id == id }
    }

    /** The tool that should act right now: Alt over a paint tool temporarily samples color. */
    private fun toolFor(alt: Boolean): Tool =
        if (alt && activeTool.altPicksColor) eyedropper else activeTool

    /** Sets the cursor for the current gesture: space/Ctrl+right pans, Alt samples, otherwise the tool's own. */
    private fun refreshCursor() {
        cursor = when {
            spacePanning || panLast != null -> ToolCursors.hand()
            // Alt over a paint tool samples color: show the eyedropper, even though the paint tool
            // would otherwise hide the cursor to draw its own brush outline.
            altSampling && activeTool.altPicksColor && strokeTool == null -> eyedropper.cursor
            shouldHideCursor() -> ToolCursors.blank()
            else -> activeTool.cursor
        }
    }

    /** True while the OS pointer should be hidden (brush/eraser, select drag). */
    private fun shouldHideCursor(): Boolean {
        // A floating paste is dragged with the Move tool, which keeps its move cursor visible.
        strokeTool?.let { if (it.hidesCursor || it.id == "select") return true }
        return activeTool.hidesCursor
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
        if (!viewport.userInteracted) initialView()
        // Adopt anything the shared state changed to since construction, then listen for live changes.
        sharedColorListener(SharedEditorState.lastColor)
        sharedToolListener(SharedEditorState.lastToolId)
        SharedEditorState.addColorListener(sharedColorListener)
        SharedEditorState.addToolListener(sharedToolListener)
        updateSelectionAnimation()
    }

    override fun removeNotify() {
        pause()
        stopSelectionAnimation()
        SharedEditorState.removeColorListener(sharedColorListener)
        SharedEditorState.removeToolListener(sharedToolListener)
        super.removeNotify()
    }

    /** The default view when the user hasn't panned/zoomed: one frame if animated, else the whole image. */
    private fun initialView() {
        if (animation.isAnimated) focusCurrentFrame(fit = true) else fitToWindow()
    }

    fun setCurrentColor(color: Color) {
        currentColor = color
        // Broadcast to other open editors. The echo back to us is ignored (rgb already matches).
        SharedEditorState.setColor(color)
        colorListener?.invoke(color)
    }

    // ---- clipboard ------------------------------------------------------------------------------

    /** True while a non-empty region is selected (including a floating paste preview). */
    val hasSelection: Boolean
        get() = document.selection?.let { it.width > 0 && it.height > 0 } == true

    /** True while the shared clipboard holds an image that can be pasted. */
    val canPaste: Boolean get() = SharedEditorState.clipboard != null

    /** Copies the selected region into the shared clipboard (includes floating overlay pixels). */
    fun copySelection() {
        val sel = document.selection?.takeIf { it.width > 0 && it.height > 0 } ?: return
        SharedEditorState.clipboard = document.copyRegion(sel)
        selectionListener?.invoke()
    }

    /** Copies the selected region into the shared clipboard and clears it from the image. */
    fun cutSelection() {
        commitPastePlacement()
        val sel = document.selection?.takeIf { it.width > 0 && it.height > 0 } ?: return
        document.pushUndo()
        SharedEditorState.clipboard = document.liftRegion(sel)
        document.selection = null
        repaint()
    }

    /** Erases the selected region to transparency, leaving the selection in place. */
    fun deleteSelection() {
        if (document.hasPendingPaste) return
        val sel = document.selection?.takeIf { it.width > 0 && it.height > 0 } ?: return
        document.pushUndo()
        document.clearRegion(sel)
        repaint()
    }

    /**
     * Floats the clipboard for placement. Enter (or leaving the select tool) merges into the active
     * layer; Esc cancels. With [newLayer] true (Shift+paste), commits immediately to a new layer.
     */
    fun paste(newLayer: Boolean = false) {
        val img = SharedEditorState.clipboard ?: return
        if (document.hasPendingPaste) commitPastePlacement()
        val center = viewport.toImage(width / 2, height / 2)
        val x = (center.x - img.width / 2).coerceIn(0, (document.width - img.width).coerceAtLeast(0))
        val y = (center.y - img.height / 2).coerceIn(0, (document.height - img.height).coerceAtLeast(0))
        val bounds = Rectangle(x, y, img.width, img.height)
            .intersection(Rectangle(0, 0, document.width, document.height))
            .takeIf { it.width > 0 && it.height > 0 }

        if (newLayer) {
            document.pushUndo()
            document.pasteAsNewLayer(img, x, y)
            document.selection = bounds
            selectionListener?.invoke()
            repaint()
            return
        }

        // Float the paste over an untouched image and hand it to the Move tool so it can be dragged
        // into place; Enter (or switching tools / clicking away) merges it into the active layer.
        document.beginFloatingPaste(img, x, y)
        document.selection = bounds
        activeTool = tools.first { it.id == "move" }
        selectionListener?.invoke()
        repaint()
    }

    /** Merges the floating paste into the active layer. */
    private fun commitPastePlacement(): Boolean {
        val f = document.layerStack.floating ?: return false
        val bounds = Rectangle(f.x, f.y, f.pixels.width, f.pixels.height)
        document.commitFloatingToActiveLayer()
        document.selection = bounds.intersection(Rectangle(0, 0, document.width, document.height))
            .takeIf { it.width > 0 && it.height > 0 }
        pasteMoveGrab = null
        pasteMoveOrigin = null
        selectionListener?.invoke()
        return true
    }

    private fun cancelPastePlacement() {
        document.cancelFloating()
        pasteMoveGrab = null
        pasteMoveOrigin = null
        document.selection = null
    }

    fun fitToWindow() {
        viewport.fit(width, height, document.width, document.height)
        repaint()
    }

    fun zoomIn() = zoomAtCenter(ZOOM_STEP)
    fun zoomOut() = zoomAtCenter(1.0 / ZOOM_STEP)

    /** Re-centers the image at the current zoom without changing the zoom level. */
    fun centerView() = recenter()

    // ---- animation ------------------------------------------------------------------------------

    val isPlaying: Boolean get() = playTimer?.isRunning == true

    /** Largest frame count the image can be sliced into (one frame per pixel along the strip axis). */
    val maxFrames: Int get() = animation.maxFrames

    /** Slices the image into [count] equal frames and frames the first one. */
    fun setFrameCount(count: Int) {
        pause()
        animation.setFrameCount(count)
        focusCurrentFrame(fit = true)
        notifyAnimation()
    }

    /** Picks a frame count assuming square frames (the Minecraft sprite-strip convention). */
    fun autoDetectFrames() {
        pause()
        animation.autoDetectSquareFrames()
        focusCurrentFrame(fit = true)
        notifyAnimation()
    }

    /** Shows frame [index] (wrapping), keeping the current zoom. */
    fun goToFrame(index: Int) {
        animation.goTo(index)
        focusCurrentFrame(fit = false)
        notifyAnimation()
    }

    fun setFrameDuration(ticks: Int) {
        animation.frameDurationTicks = ticks
        if (isPlaying) playTimer?.delay = playbackDelayMs()
    }

    fun togglePlay() = if (isPlaying) pause() else play()

    fun play() {
        if (!animation.isAnimated || isPlaying) return
        focusCurrentFrame(fit = false)
        playTimer = Timer(playbackDelayMs()) {
            animation.next()
            focusCurrentFrame(fit = false)
            notifyAnimation()
        }.apply { isCoalesce = true; start() }
        onPlayStateChanged?.invoke(true)
    }

    fun pause() {
        if (playTimer == null) return
        playTimer?.stop()
        playTimer = null
        onPlayStateChanged?.invoke(false)
    }

    private fun playbackDelayMs(): Int = (animation.frameDurationTicks * MS_PER_TICK).coerceAtLeast(1)

    /** Frames the current frame: [fit] zooms it to fill the view, otherwise just re-centers on it. */
    private fun focusCurrentFrame(fit: Boolean) {
        if (!animation.isAnimated) {
            repaint()
            return
        }
        val r = animation.frameRect()
        if (fit) viewport.fitRegion(width, height, r, FRAME_FIT_SCALE)
        else viewport.focusOn(width, height, r.x + r.width / 2.0, r.y + r.height / 2.0)
        repaint()
    }

    private fun notifyAnimation() = onAnimationChanged?.invoke(animation.currentFrame, animation.frameCount)

    // ---- input ----------------------------------------------------------------------------------

    private fun toolContext(e: MouseEvent) =
        ToolContext(document, viewport, viewport.toImage(e.x, e.y), e.point, e.isAltDown, e.isShiftDown, currentColor, ::setCurrentColor)

    /** Middle-click, Space+left-drag, or Ctrl+right-drag — all pan the view without editing. */
    private fun isPanGesture(e: MouseEvent): Boolean =
        SwingUtilities.isMiddleMouseButton(e)
            || (spacePanning && SwingUtilities.isLeftMouseButton(e))
            || (SwingUtilities.isRightMouseButton(e) && e.isControlDown && !e.isAltDown)

    /**
     * Starts the Photoshop-style brush resize gesture: Alt + right-drag over a paint tool. Dragging
     * right grows the brush, left shrinks it; the outline previews at the anchor while resizing.
     */
    private fun tryStartBrushResize(e: MouseEvent): Boolean {
        if (!SwingUtilities.isRightMouseButton(e) || !e.isAltDown || activeTool !is PencilTool) return false
        brushResize = BrushResize(e.point, brushSize)
        hoverPoint = e.point
        repaint()
        return true
    }

    private fun updateBrushResize(e: MouseEvent) {
        val gesture = brushResize ?: return
        val steps = ((e.x - gesture.anchor.x).toFloat() / JBUI.scale(BRUSH_DRAG_PX)).roundToInt()
        brushSize = gesture.startSize + steps
    }

    private fun onPress(e: MouseEvent) {
        if (isPanGesture(e)) {
            panLast = e.point
            refreshCursor()
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        if (tryStartPendingPasteMove(e)) return
        val tool = toolFor(e.isAltDown)
        strokeTool = tool
        tool.onPress(toolContext(e))
        refreshCursor()
        clampView()
        repaint()
    }

    private fun tryStartPendingPasteMove(e: MouseEvent): Boolean {
        val floating = document.layerStack.floating ?: return false
        if (activeTool.id != "move") return false
        val sel = document.selection
        val imgPt = viewport.toImage(e.x, e.y)
        if (sel != null && sel.contains(imgPt)) {
            pasteMoveGrab = imgPt
            pasteMoveOrigin = Point(floating.x, floating.y)
            strokeTool = activeTool
            refreshCursor()
            return true
        }
        // Clicking outside the pasted region drops it into the active layer.
        commitPastePlacement()
        repaint()
        return true
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
        if (pasteMoveGrab != null) {
            val floating = document.layerStack.floating ?: return
            val grab = pasteMoveGrab ?: return
            val origin = pasteMoveOrigin ?: return
            val imgPt = viewport.toImage(e.x, e.y)
            val x = (origin.x + (imgPt.x - grab.x)).coerceIn(0, (document.width - floating.pixels.width).coerceAtLeast(0))
            val y = (origin.y + (imgPt.y - grab.y)).coerceIn(0, (document.height - floating.pixels.height).coerceAtLeast(0))
            document.moveFloating(x, y)
            document.selection = Rectangle(x, y, floating.pixels.width, floating.pixels.height)
            clampView()
            repaint()
            return
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return
        (strokeTool ?: toolFor(e.isAltDown)).onDrag(toolContext(e))
        if (shouldHideCursor()) refreshCursor()
        clampView()
        repaint()
    }

    private fun onRelease(e: MouseEvent) {
        if (panLast != null) {
            panLast = null
            refreshCursor()
            return
        }
        if (pasteMoveGrab != null) {
            pasteMoveGrab = null
            pasteMoveOrigin = null
            strokeTool = null
            refreshCursor()
            repaint()
            return
        }
        (strokeTool ?: activeTool).onRelease(toolContext(e))
        strokeTool = null
        refreshCursor()
        repaint()
    }

    /** Right-click menu: crop, promote the selection to its own layer, or resize. */
    private fun showContextMenu(e: MouseEvent) {
        val menu = JPopupMenu()
        val selection = document.selection?.takeIf { it.width > 0 && it.height > 0 }
        menu.add(JMenuItem("Crop").apply { addActionListener { contextCrop() } })
        menu.add(JMenuItem("Copy to New Layer").apply {
            isEnabled = selection != null
            addActionListener { selection?.let { layerFromSelection(it, cut = false) } }
        })
        menu.add(JMenuItem("Cut to New Layer").apply {
            isEnabled = selection != null
            addActionListener { selection?.let { layerFromSelection(it, cut = true) } }
        })
        menu.add(JMenuItem("Resize…").apply {
            isEnabled = onResizeRequested != null
            addActionListener { onResizeRequested?.invoke() }
        })
        menu.show(this, e.x, e.y)
    }

    /** Copies (or cuts) the current selection into a new top layer, left active for moving. */
    private fun layerFromSelection(region: Rectangle, cut: Boolean) {
        commitPastePlacement()
        if (document.layerFromSelection(region, cut)) repaint()
    }

    private fun contextCrop() {
        val sel = document.selection
        when {
            sel != null -> document.crop(sel)          // crop straight to a select-tool selection
            activeTool.onCommit(document) -> {}         // or commit the crop tool's staged box
            else -> activeTool = tools.first { it.id == "crop" } // otherwise arm the crop tool to drag one
        }
        repaint()
    }

    // ---- rendering ------------------------------------------------------------------------------

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = CanvasRender.CANVAS_BACKGROUND
            g2.fillRect(0, 0, width, height)

            // When animated we draw the whole strip but show only the current frame: the viewport is
            // centered on that frame and the draw is clipped to it, so neighbouring frames stay hidden.
            val imageRect = viewport.imageRect(document.width, document.height)
            val animated = animation.isAnimated
            val frameRect = if (animated) viewport.toComponent(animation.frameRect()) else imageRect
            CanvasRender.checkerboard(g2, frameRect)

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            val savedClip = g2.clip
            if (animated) g2.clipRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height)
            g2.drawImage(document.image, imageRect.x, imageRect.y, imageRect.width, imageRect.height, this)
            g2.clip = savedClip

            g2.color = JBColor.border()
            g2.drawRect(frameRect.x, frameRect.y, frameRect.width, frameRect.height)

            // While resizing show the active paint tool's outline, not the Alt-sampling eyedropper.
            val previewTool = if (brushResize != null) activeTool else toolFor(altSampling)
            previewTool.paintOverlay(g2, viewport)
            // The brush-outline hover preview is meaningless while panning.
            val showHover = !spacePanning && panLast == null &&
                strokeTool?.let { !it.hidesCursor && it.id != "select" } != false
            if (showHover) hoverPoint?.let { previewTool.paintHover(g2, viewport, viewport.toImage(it.x, it.y)) }
            document.layerStack.floating?.let { floating ->
                val r = viewport.toComponent(Rectangle(floating.x, floating.y, floating.pixels.width, floating.pixels.height))
                g2.drawImage(floating.pixels, r.x, r.y, r.width, r.height, this)
            }
            document.selection?.takeIf { it.width > 0 && it.height > 0 }?.let { sel ->
                val viewBounds = g2.clipBounds ?: Rectangle(0, 0, width, height)
                val viewSel = viewport.toComponent(sel)
                if (document.hasPendingPaste) {
                    CanvasRender.selectionOutline(g2, viewSel, selectionDashPhase)
                } else {
                    CanvasRender.selectionHighlight(g2, viewBounds, viewSel, selectionDashPhase)
                }
            }

            paintInfo(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun updateSelectionAnimation() {
        val active = document.selection?.let { it.width > 0 && it.height > 0 } == true && isShowing
        if (active && selectionAnimTimer == null) {
            selectionAnimTimer = Timer(SELECTION_ANIM_MS) {
                val period = JBUI.scale(SELECTION_DASH_PERIOD).toFloat()
                selectionDashPhase = (selectionDashPhase + 1f) % period
                repaint()
            }.apply { start() }
        } else if (!active) {
            stopSelectionAnimation()
        }
    }

    private fun stopSelectionAnimation() {
        selectionAnimTimer?.stop()
        selectionAnimTimer = null
        selectionDashPhase = 0f
    }

    private fun paintInfo(g2: Graphics2D) {
        val frameInfo = if (animation.isAnimated) "   frame ${animation.currentFrame + 1}/${animation.frameCount}" else ""
        val selInfo = document.selection?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { "   ${it.width}×${it.height}" } ?: ""
        val label = "${document.width}×${document.height}   ${(viewport.zoom * 100).roundToInt()}%   ${toolFor(altSampling).displayName.lowercase()}$selInfo$frameInfo"
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
     * [WHEN_ANCESTOR_OF_FOCUSED_COMPONENT] to fire whenever focus is inside the editor (without
     * leaking to other tabs), or the default [WHEN_FOCUSED] to require canvas focus specifically.
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

        // Pixels of Alt+right-drag travel per one step of brush-size change.
        private const val BRUSH_DRAG_PX = 6

        // Milliseconds per Minecraft tick, for converting frame durations to playback timer delays.
        private const val MS_PER_TICK = 50

        // Leaves a small margin around a framed animation frame so its border stays visible.
        private const val FRAME_FIT_SCALE = 0.92

        private const val SELECTION_ANIM_MS = 50
        private const val SELECTION_DASH_PERIOD = 8
    }
}
