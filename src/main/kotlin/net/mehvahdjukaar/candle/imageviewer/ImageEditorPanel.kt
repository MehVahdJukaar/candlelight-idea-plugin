package net.mehvahdjukaar.candle.imageviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CompositeShortcutSet
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.tools.Tool
import net.mehvahdjukaar.candle.imageviewer.tools.ToolIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SpinnerNumberModel
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
    initialFrames: Int = 1,
    initialDurationTicks: Int = Animation.DEFAULT_DURATION_TICKS,
    // True for GIF-sourced images: they open as an editable strip for animation preview, but the
    // frame layout is fixed and changes can't be written back, so saving/frame-count are disabled.
    private val readOnly: Boolean = false,
    private val onModifiedChanged: (Boolean) -> Unit,
) : JPanel(BorderLayout()) {

    private val canvas = ImageCanvas(image, initialFrames, initialDurationTicks)

    private val toolButtons = mutableMapOf<Tool, JToggleButton>()
    private lateinit var undoButton: JButton
    private lateinit var redoButton: JButton
    private lateinit var saveButton: JButton
    private lateinit var copyButton: JButton
    private lateinit var cutButton: JButton
    private lateinit var pasteButton: JButton

    private lateinit var playButton: FlatActionButton
    private lateinit var frameSlider: JSlider
    private lateinit var frameReadout: JLabel
    private lateinit var frameCountSpinner: JSpinner
    private lateinit var durationSpinner: JSpinner

    // Guards against feedback loops while pushing canvas state back into the animation widgets.
    private var updatingAnim = false

    private val statusDot = StatusDot()
    private val statusLabel = JBLabel()

    private val paletteWidget = PaletteWidget()
    private val colorPicker = ColorPickerWidget(canvas.currentColor)
    private lateinit var layersPanel: LayersPanel

    // Rescanning the image on every painted pixel would be wasteful, so coalesce edits.
    private val paletteTimer = Timer(250) { refreshPalette() }.apply { isRepeats = false }

    private var dirty = false

    // Pixels of the last-saved state, so we can tell when edits (or undos) return the image to it.
    private var savedSnapshot: IntArray = snapshotOf(canvas.document.image)

    val preferredFocus: JComponent get() = canvas

    init {
        canvas.colorListener = { colorPicker.setColorExternally(it) }
        canvas.editListener = { onContentEdited() }
        canvas.selectionListener = { refreshEditButtons() }
        canvas.onActiveToolChanged = { tool -> toolButtons[tool]?.isSelected = true }
        canvas.onResizeRequested = { showResizeDialog() }
        canvas.onAnimationChanged = { current, count -> syncAnimationControls(current, count) }
        canvas.onPlayStateChanged = { playing -> updatePlayButton(playing) }

        colorPicker.onColorChanged = { canvas.setCurrentColor(it) }
        paletteWidget.onColorPicked = { canvas.setCurrentColor(it) }

        val root = JBSplitter(false, 0.24f).apply {
            firstComponent = buildDock()
            secondComponent = canvas
        }
        add(root, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)

        registerIdeActions()

        refreshEditButtons()
        updateSaveState(clean = true)
        refreshPalette()
    }

    // ---- IDE shortcuts --------------------------------------------------------------------------

    /**
     * Re-binds the built-in Undo/Redo/Save shortcuts to this editor. Registering the actions on this
     * component makes them win over the global keymap while the image editor has focus, so e.g.
     * Ctrl+Z undoes the last pixel edit instead of being swallowed by the platform's editor undo.
     */
    private fun registerIdeActions() {
        ActionManager.getInstance().getAction(IdeActions.ACTION_UNDO)?.shortcutSet?.let {
            bindShortcut(it, { canvas.document.canUndo }) { canvas.document.undo() }
        }
        ActionManager.getInstance().getAction(IdeActions.ACTION_REDO)?.shortcutSet?.let {
            bindShortcut(it, { canvas.document.canRedo }) { canvas.document.redo() }
        }
        ActionManager.getInstance().getAction(IdeActions.ACTION_COPY)?.shortcutSet?.let {
            bindShortcut(it, { canvas.hasSelection }) { canvas.copySelection() }
        }
        ActionManager.getInstance().getAction(IdeActions.ACTION_CUT)?.shortcutSet?.let {
            bindShortcut(it, { canvas.hasSelection }) { canvas.cutSelection() }
        }
        ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE)?.shortcutSet?.let {
            bindShortcut(it, { canvas.canPaste }) { canvas.paste(newLayer = false) }
        }
        // Bind Ctrl+S explicitly: the IDE's "Save All" action has no default keymap shortcut
        // (autosave makes it unnecessary), so relying on its shortcutSet would bind nothing.
        // Merge in whatever the user may have assigned to Save All, just in case.
        val ctrlS = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK))
        val saveShortcuts = ActionManager.getInstance().getAction("SaveAll")?.shortcutSet
        val merged = if (saveShortcuts != null) CompositeShortcutSet(ctrlS, saveShortcuts) else ctrlS
        bindShortcut(merged, { dirty }) { save() }
    }

    private fun bindShortcut(shortcuts: ShortcutSet, enabled: () -> Boolean, run: () -> Unit) {
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = run()
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = enabled()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        }.registerCustomShortcutSet(shortcuts, this)
    }

    // ---- dock -----------------------------------------------------------------------------------

    private fun buildDock(): JComponent {
        val content = ScrollableColumn().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 5)
        }

        content.add(CollapsibleSection("Palette", body = CollapsibleSection.body(paletteWidget)))
        content.add(CollapsibleSection.verticalGap())

        content.add(CollapsibleSection("Color", body = CollapsibleSection.body(colorPicker)))
        content.add(CollapsibleSection.verticalGap())

        layersPanel = LayersPanel().also { panel ->
            panel.bind(canvas.document)
            panel.onLayerActivated = { canvas.repaint() }
            canvas.document.onLayersChanged = { panel.refresh() }
            canvas.document.onOverlayChanged = { canvas.repaint() }
        }
        content.add(CollapsibleSection("Layers", body = CollapsibleSection.fullWidthBody(layersPanel)))
        content.add(CollapsibleSection.verticalGap())

        val group = ButtonGroup()
        val toolBtns = canvas.tools.map { tool -> toolButton(tool, group).also { toolButtons[tool] = it } }
        content.add(CollapsibleSection("Tools", body = CollapsibleSection.body(grid(toolBtns, COLUMNS))))
        content.add(CollapsibleSection.verticalGap())

        content.add(CollapsibleSection("Brush size", body = CollapsibleSection.body(buildBrushSlider())))
        content.add(CollapsibleSection.verticalGap())

        content.add(CollapsibleSection("Animation", collapsedInitially = true, body = CollapsibleSection.body(buildAnimationSection())))
        content.add(CollapsibleSection.verticalGap())

        undoButton = actionButton(AllIcons.Actions.Undo, "Undo", "Ctrl+Z") { canvas.document.undo() }
        redoButton = actionButton(AllIcons.Actions.Redo, "Redo", "Ctrl+Shift+Z") { canvas.document.redo() }
        saveButton = actionButton(AllIcons.Actions.MenuSaveall, "Save", "Ctrl+S") { save() }
        copyButton = actionButton(AllIcons.Actions.Copy, "Copy", "Ctrl+C") { canvas.copySelection() }
        cutButton = actionButton(AllIcons.Actions.MenuCut, "Cut", "Ctrl+X") { canvas.cutSelection() }
        pasteButton = actionButton(AllIcons.Actions.MenuPaste, "Paste", "Ctrl+V · Shift+Ctrl+V new layer") { canvas.paste(newLayer = false) }
        val resizeButton = actionButton(ToolIcons.RESIZE, "Resize…", "") { showResizeDialog() }
        content.add(CollapsibleSection(
            "Edit",
            body = CollapsibleSection.body(grid(listOf(undoButton, redoButton, saveButton, copyButton, cutButton, pasteButton, resizeButton), COLUMNS)),
        ))
        content.add(CollapsibleSection.verticalGap())

        val zoomOutBtn = actionButton(AllIcons.General.ZoomOut, "Zoom Out", "−") { canvas.zoomOut() }
        val zoomInBtn = actionButton(AllIcons.General.ZoomIn, "Zoom In", "+") { canvas.zoomIn() }
        val fitBtn = actionButton(AllIcons.General.FitContent, "Fit & Center", "0") { canvas.fitToWindow() }
        content.add(CollapsibleSection(
            "View",
            body = CollapsibleSection.body(grid(listOf(zoomOutBtn, zoomInBtn, fitBtn), COLUMNS)),
        ))

        return JBScrollPane(
            content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply { border = JBUI.Borders.empty() }
    }

    /** Dock content that tracks the viewport width so its widgets shrink/stretch with the dock. */
    private inner class ScrollableColumn : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = JBUI.scale(16)
        override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = r.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    /** Wraps [c] so it stays left-aligned and full-width inside the vertical [BoxLayout] dock. */
    private fun leftAligned(c: JComponent): JComponent = c.apply { alignmentX = Component.LEFT_ALIGNMENT }

    /** Slider (1..max) that drives the pencil/eraser brush size, with a live "N px" readout. */
    private fun buildBrushSlider(): JComponent {
        val slider = JSlider(1, ImageCanvas.MAX_BRUSH, canvas.brushSize)
        slider.toolTipText = "Brush size  ([ and ], or Alt+right-drag)"
        val value = JLabel("${canvas.brushSize} px").apply {
            font = JBUI.Fonts.miniFont()
            foreground = JBColor.GRAY
        }
        slider.addChangeListener {
            canvas.brushSize = slider.value
            value.text = "${slider.value} px"
        }
        // Mirror keyboard ([ ]) / Alt+wheel brush-size changes from the canvas back into the slider.
        canvas.onBrushSizeChanged = { size ->
            slider.value = size
            value.text = "$size px"
        }
        return JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(slider, BorderLayout.CENTER)
            add(value, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    // ---- animation section ----------------------------------------------------------------------

    /**
     * The Animation controls: a play/step transport, a frame scrubber, a frame-count picker with an
     * auto-detect (assume-square) button, and a per-frame duration. Works for both GIFs (frame layout
     * fixed, count controls disabled) and sprite strips (slice the image into N frames).
     */
    private fun buildAnimationSection(): JComponent {
        val anim = canvas.animation

        val prev = actionButton(AllIcons.Actions.Play_back, "Previous frame", "") {
            canvas.goToFrame(canvas.animation.currentFrame - 1)
        }
        playButton = FlatActionButton(AllIcons.Actions.Execute).apply {
            squareIconButton(this)
            addActionListener { canvas.togglePlay() }
        }
        val next = actionButton(AllIcons.Actions.Play_forward, "Next frame", "") {
            canvas.goToFrame(canvas.animation.currentFrame + 1)
        }
        frameReadout = JLabel().apply { font = JBUI.Fonts.miniFont(); foreground = JBColor.GRAY }
        // Transport buttons on the left, frame counter trailing them on the same line.
        val transport = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(prev); add(playButton); add(next)
            add(Box.createHorizontalStrut(JBUI.scale(6)))
            add(frameReadout)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        frameSlider = JSlider(1, anim.frameCount.coerceAtLeast(1), anim.currentFrame + 1).apply {
            toolTipText = "Current frame"
            isOpaque = false
            addChangeListener { if (!updatingAnim) canvas.goToFrame(value - 1) }
        }
        val sliderRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(frameSlider, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, frameSlider.preferredSize.height)
        }

        frameCountSpinner = compactSpinner(SpinnerNumberModel(anim.frameCount, 1, canvas.maxFrames, 1)).apply {
            toolTipText = "Number of frames the image is split into"
            isEnabled = !readOnly
            addChangeListener { if (!updatingAnim) canvas.setFrameCount(value as Int) }
        }
        // Auto-detect (assume square frames) shares the frames row as an icon: it sets the frame count.
        val autoButton = actionButton(AllIcons.Actions.Lightning, "Auto-detect frames", "") {
            canvas.autoDetectFrames()
        }.apply { isEnabled = !readOnly }
        val framesRow = animRow(frameCountSpinner, autoButton, animLabel("Frames"))

        durationSpinner = compactSpinner(SpinnerNumberModel(anim.frameDurationTicks, 1, Animation.MAX_DURATION_TICKS, 1)).apply {
            toolTipText = "Ticks each frame is shown (20 ticks = 1 second)"
            addChangeListener { if (!updatingAnim) canvas.setFrameDuration(value as Int) }
        }
        val durationRow = animRow(durationSpinner, animLabel("Speed"))

        updatePlayButton(playing = false)
        syncAnimationControls(anim.currentFrame, anim.frameCount)

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(transport)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(sliderRow)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(framesRow)
            add(Box.createVerticalStrut(JBUI.scale(3)))
            add(durationRow)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    /** A compact `field  trailing…` row; the trailing components (incl. the name label) sit to the right. */
    private fun animRow(field: JComponent, vararg trailing: JComponent): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(field)
            trailing.forEach { add(it) }
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    /** The grey name label shown to the right of an animation control. */
    private fun animLabel(text: String): JLabel =
        JLabel(text).apply { font = JBUI.Fonts.miniFont(); foreground = JBColor.GRAY }

    /** A narrow spinner so the small frame-count/speed numbers don't take the whole dock width. */
    private fun compactSpinner(model: SpinnerNumberModel): JSpinner = JSpinner(model).apply {
        val h = preferredSize.height
        preferredSize = Dimension(JBUI.scale(64), h)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    /** Pushes the canvas's current frame/count back into the widgets without re-triggering listeners. */
    private fun syncAnimationControls(current: Int, count: Int) {
        updatingAnim = true
        try {
            frameSlider.maximum = count.coerceAtLeast(1)
            frameSlider.value = (current + 1).coerceIn(1, frameSlider.maximum)
            frameReadout.text = "${current + 1} / $count"
            if (frameCountSpinner.value as Int != count) frameCountSpinner.value = count
            val animated = count > 1
            frameSlider.isEnabled = animated
            playButton.isEnabled = animated
        } finally {
            updatingAnim = false
        }
    }

    private fun updatePlayButton(playing: Boolean) {
        playButton.icon = if (playing) AllIcons.Actions.Pause else AllIcons.Actions.Execute
        playButton.toolTipText = tooltip(if (playing) "Pause" else "Play", null, "Cycle through the frames")
    }

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
        if (!shortcut.isNullOrBlank()) append(" &nbsp;<font color='#888888'>").append(shortcut).append("</font>")
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
        // An edit can also bring the image back to the saved state (e.g. undoing every change),
        // so derive dirtiness by comparing against the saved snapshot rather than latching it true.
        updateSaveState(clean = matchesSaved())
        refreshEditButtons()
        paletteTimer.restart()
        // Keep the layer thumbnails in sync with the pixels the edit just changed.
        if (::layersPanel.isInitialized) layersPanel.repaint()
    }

    private fun snapshotOf(img: BufferedImage): IntArray =
        img.getRGB(0, 0, img.width, img.height, null, 0, img.width)

    private fun matchesSaved(): Boolean {
        val img = canvas.document.image
        if (img.width * img.height != savedSnapshot.size) return false
        return snapshotOf(img).contentEquals(savedSnapshot)
    }

    private fun refreshEditButtons() {
        undoButton.isEnabled = canvas.document.canUndo
        redoButton.isEnabled = canvas.document.canRedo
        copyButton.isEnabled = canvas.hasSelection
        cutButton.isEnabled = canvas.hasSelection
        pasteButton.isEnabled = canvas.canPaste
    }

    private fun updateSaveState(clean: Boolean) {
        if (readOnly) {
            // GIF-sourced previews can't be written back; never report unsaved changes.
            saveButton.isEnabled = false
            statusDot.color = JBColor.GRAY
            statusDot.repaint()
            statusLabel.text = "Animation preview (read-only)"
            statusLabel.foreground = JBColor.GRAY
            return
        }
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

    /** Whether there are unsaved pixel edits. */
    val hasUnsavedChanges: Boolean get() = !readOnly && dirty

    /** Saves the current image to disk. Returns true on success. Safe to call from outside the panel. */
    fun saveNow(): Boolean = save()

    private fun save(): Boolean {
        if (readOnly) return false
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
                    return false
                }
                stream.toByteArray()
            }
            WriteAction.run<IOException> { file.setBinaryContent(bytes) }
            savedSnapshot = snapshotOf(canvas.document.image)
            updateSaveState(clean = true)
            return true
        } catch (t: Throwable) {
            Messages.showErrorDialog(this, t.message ?: "Failed to save image.", "Save Image")
            return false
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

    // ---- resize ---------------------------------------------------------------------------------

    private fun showResizeDialog() {
        if (readOnly) return
        val dialog = ResizeDialog(canvas.document.width, canvas.document.height)
        if (dialog.showAndGet()) {
            canvas.document.resizeTo(dialog.newWidth, dialog.newHeight, dialog.smooth)
        }
    }

    /** Scales the whole image to a new width/height, optionally locking the aspect ratio. */
    private inner class ResizeDialog(private val origW: Int, private val origH: Int) :
        DialogWrapper(this@ImageEditorPanel, true) {

        private val widthSpinner = JSpinner(SpinnerNumberModel(origW, 1, MAX_DIMENSION, 1))
        private val heightSpinner = JSpinner(SpinnerNumberModel(origH, 1, MAX_DIMENSION, 1))
        private val lockAspect = JCheckBox("Keep aspect ratio", true)
        private val smoothBox = JCheckBox("Smooth (bilinear)", false)

        // Suppresses the aspect-linking listeners while they update the other field, to avoid a loop.
        private var syncing = false

        val newWidth: Int get() = widthSpinner.value as Int
        val newHeight: Int get() = heightSpinner.value as Int
        val smooth: Boolean get() = smoothBox.isSelected

        init {
            title = "Resize Image"
            widthSpinner.addChangeListener { if (lockAspect.isSelected) syncFrom(widthSpinner) }
            heightSpinner.addChangeListener { if (lockAspect.isSelected) syncFrom(heightSpinner) }
            smoothBox.toolTipText = "Off keeps hard pixel edges (nearest-neighbour); on blends when scaling."
            init()
        }

        private fun syncFrom(source: JSpinner) {
            if (syncing) return
            syncing = true
            try {
                if (source === widthSpinner) {
                    heightSpinner.value = ((newWidth.toLong() * origH) / origW).toInt().coerceIn(1, MAX_DIMENSION)
                } else {
                    widthSpinner.value = ((newHeight.toLong() * origW) / origH).toInt().coerceIn(1, MAX_DIMENSION)
                }
            } finally {
                syncing = false
            }
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            val c = GridBagConstraints().apply {
                insets = JBUI.insets(4)
                anchor = GridBagConstraints.WEST
            }
            c.gridx = 0; c.gridy = 0
            panel.add(JLabel("Width:"), c)
            c.gridx = 1
            panel.add(widthSpinner, c)
            c.gridx = 2
            panel.add(JLabel("px"), c)

            c.gridx = 0; c.gridy = 1
            panel.add(JLabel("Height:"), c)
            c.gridx = 1
            panel.add(heightSpinner, c)
            c.gridx = 2
            panel.add(JLabel("px"), c)

            c.gridx = 0; c.gridy = 2; c.gridwidth = 3
            panel.add(lockAspect, c)
            c.gridy = 3
            panel.add(smoothBox, c)
            return panel
        }
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
        private const val COLUMNS = 4
        private const val MAX_PALETTE = 48
        private const val MAX_SCAN = 200_000L

        private val SHORTCUTS = mapOf(
            "pick" to "I", "select" to "M", "move" to "V", "pencil" to "B", "eraser" to "E",
            "recolor" to "G", "crop" to "C", "zoom" to "Z", "hand" to "H",
        )

        // Upper bound for the resize dialog's width/height fields.
        private const val MAX_DIMENSION = 8192

        private val SAVED_COLOR = JBColor(Color(0x3C, 0x8B, 0x3C), Color(0x59, 0xA8, 0x69))
        private val UNSAVED_COLOR = JBColor(Color(0xB8, 0x6A, 0x00), Color(0xD8, 0x95, 0x16))
    }
}
