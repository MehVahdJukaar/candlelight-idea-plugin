package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColorChooserService
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared scaffolding for the right-click "adjust" procedures (Hue/Saturation/Brightness,
 * Brightness/Contrast, Gradient overlay, Outer glow). Each one snapshots the active layer, previews
 * live as its controls change, and collapses the whole gesture into a single undo step on Apply;
 * Cancel/Esc or a no-op restores the snapshot untouched.
 *
 * Subclasses supply their controls ([buildControls]), the pixel op ([applyTo]), a [reset] and an
 * [isNoOp] check, and call [preview] whenever a control changes.
 */
internal abstract class LayerAdjustProcedure(private val title: String) {

    private lateinit var doc: ImageDocument
    private lateinit var repaintCanvas: () -> Unit
    private var cancelled = false

    /** The pre-adjust pixels, taken when the popup opens; every preview re-derives from these. */
    protected lateinit var base: BufferedImage
        private set

    protected val document: ImageDocument get() = doc

    /** Whether clicking outside the popup dismisses it. Off for procedures that spawn a color dialog. */
    protected open val dismissOnClickOutside: Boolean = true

    fun show(anchor: JComponent, x: Int, y: Int, document: ImageDocument, repaint: () -> Unit) {
        doc = document
        repaintCanvas = repaint
        base = document.snapshotActiveLayer()
        cancelled = false

        val controls = buildControls()
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            add(controls)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, focusTarget(controls))
            .setTitle(title)
            .setResizable(false)
            .setMovable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(dismissOnClickOutside)
            .setCancelOnWindowDeactivation(dismissOnClickOutside)
            .createPopup()

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(miniButton("Reset") { reset(); preview() })
            add(miniButton("Cancel") { cancelled = true; popup.cancel() })
            add(miniButton("Apply") { popup.cancel() })
        }
        // Cap the height only after the buttons exist, or an empty row measures 0 tall and BoxLayout
        // collapses it (the buttons would vanish).
        buttons.maximumSize = Dimension(Int.MAX_VALUE, buttons.preferredSize.height)
        panel.add(Box.createVerticalStrut(JBUI.scale(8)))
        panel.add(buttons)

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (cancelled || isNoOp()) {
                    document.restoreActiveLayer(base) // exact revert, no undo entry
                    repaintCanvas()
                    return
                }
                // Rewind to the pre-adjust pixels, snapshot them for one undo step, then re-apply.
                document.restoreActiveLayer(base)
                document.pushUndo()
                applyTo(base)
                repaintCanvas()
            }
        })

        popup.show(RelativePoint(anchor, Point(x, y)))
    }

    /** Re-derives the active layer from [base] with the current settings and repaints the canvas. */
    protected fun preview() {
        applyTo(base)
        repaintCanvas()
    }

    /** The component the popup should focus initially; defaults to the controls root. */
    protected open fun focusTarget(controls: JComponent): JComponent = controls

    protected abstract fun buildControls(): JComponent
    protected abstract fun applyTo(base: BufferedImage)
    protected abstract fun reset()
    protected abstract fun isNoOp(): Boolean

    // ---- shared UI helpers ----------------------------------------------------------------------

    protected fun miniButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
        isFocusable = false
        margin = JBUI.insets(2, 10)
        font = JBUI.Fonts.miniFont()
        addActionListener { onClick() }
    }

    protected fun valueLabel(): JLabel = JLabel().apply {
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        horizontalAlignment = SwingConstants.RIGHT
        preferredSize = Dimension(JBUI.scale(40), preferredSize.height)
    }

    private fun caption(text: String): JLabel = JLabel(text).apply {
        font = JBUI.Fonts.miniFont()
        foreground = JBColor.GRAY
        preferredSize = Dimension(JBUI.scale(72), preferredSize.height)
    }

    /** A `[name | center | trailing]` row, sized for a slider in the middle. */
    protected fun controlRow(name: String, center: JComponent, trailing: JComponent? = null): JComponent =
        JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(30))
            add(caption(name), BorderLayout.WEST)
            add(center, BorderLayout.CENTER)
            if (trailing != null) add(trailing, BorderLayout.EAST)
        }

    protected fun spacer(): Component = Box.createVerticalStrut(JBUI.scale(6))

    companion object {
        fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()
    }
}

/**
 * A horizontal slider whose value runs -1..1 with a center detent at 0. The track paints [gradient]
 * (fraction 0..1) and a tick marks the zero point.
 */
internal class BipolarSlider(private val gradient: (Double) -> Color) : JComponent() {

    var value: Double = 0.0
        set(v) {
            field = v.coerceIn(-1.0, 1.0)
            repaint()
        }

    var onChange: () -> Unit = {}

    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = setFromMouse(e)
            override fun mouseDragged(e: MouseEvent) = setFromMouse(e)
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    override fun getPreferredSize() = Dimension(JBUI.scale(150), JBUI.scale(14))
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(14))
    override fun getMinimumSize() = Dimension(JBUI.scale(60), JBUI.scale(14))

    private fun setFromMouse(e: MouseEvent) {
        val thumb = JBUI.scale(THUMB)
        val travel = (width - thumb).coerceAtLeast(1)
        val frac = (e.x - thumb / 2.0) / travel
        val v = (frac * 2.0 - 1.0)
        value = if (abs(v) < SNAP) 0.0 else v // snap to the center detent
        onChange()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val thumb = JBUI.scale(THUMB)
            val trackX = thumb / 2
            val trackW = (width - thumb).coerceAtLeast(1)
            val trackY = JBUI.scale(1)
            val trackH = height - JBUI.scale(2)

            for (i in 0 until trackW) {
                g2.color = gradient(i.toDouble() / trackW)
                g2.fillRect(trackX + i, trackY, 1, trackH)
            }
            g2.color = JBColor.border()
            g2.drawRect(trackX, trackY, trackW - 1, trackH - 1)

            // Zero tick.
            val zx = trackX + trackW / 2
            g2.color = JBColor.foreground()
            g2.drawLine(zx, trackY - JBUI.scale(1), zx, trackY + trackH)

            val frac = (value + 1.0) / 2.0
            val tx = trackX + (frac * trackW).roundToInt()
            g2.color = JBColor.background()
            g2.fillRoundRect(tx - thumb / 2, 0, thumb, height - 1, JBUI.scale(3), JBUI.scale(3))
            g2.color = JBColor.foreground()
            g2.drawRoundRect(tx - thumb / 2, 0, thumb - 1, height - 2, JBUI.scale(3), JBUI.scale(3))
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val THUMB = 8
        private const val SNAP = 0.035
    }
}

/** A plain horizontal slider whose value runs 0..1 with a neutral fill track. */
internal class LinearSlider : JComponent() {

    var fraction: Double = 0.0
        set(v) {
            field = v.coerceIn(0.0, 1.0)
            repaint()
        }

    var onChange: () -> Unit = {}

    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = setFromMouse(e)
            override fun mouseDragged(e: MouseEvent) = setFromMouse(e)
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    override fun getPreferredSize() = Dimension(JBUI.scale(150), JBUI.scale(14))
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, JBUI.scale(14))
    override fun getMinimumSize() = Dimension(JBUI.scale(60), JBUI.scale(14))

    private fun setFromMouse(e: MouseEvent) {
        if (!isEnabled) return
        val thumb = JBUI.scale(THUMB)
        val travel = (width - thumb).coerceAtLeast(1)
        fraction = (e.x - thumb / 2.0) / travel
        onChange()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val thumb = JBUI.scale(THUMB)
            val trackX = thumb / 2
            val trackW = (width - thumb).coerceAtLeast(1)
            val trackY = JBUI.scale(1)
            val trackH = height - JBUI.scale(2)

            g2.color = if (isEnabled) TRACK_BG else DISABLED_BG
            g2.fillRect(trackX, trackY, trackW, trackH)
            g2.color = if (isEnabled) TRACK_FILL else DISABLED_FILL
            g2.fillRect(trackX, trackY, (fraction * trackW).roundToInt().coerceIn(0, trackW), trackH)
            g2.color = JBColor.border()
            g2.drawRect(trackX, trackY, trackW - 1, trackH - 1)

            val tx = trackX + (fraction * trackW).roundToInt()
            g2.color = JBColor.background()
            g2.fillRoundRect(tx - thumb / 2, 0, thumb, height - 1, JBUI.scale(3), JBUI.scale(3))
            g2.color = if (isEnabled) JBColor.foreground() else JBColor.GRAY
            g2.drawRoundRect(tx - thumb / 2, 0, thumb - 1, height - 2, JBUI.scale(3), JBUI.scale(3))
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val THUMB = 8
        private val TRACK_BG = JBColor(Color(0xCD, 0xCD, 0xCD), Color(0x51, 0x51, 0x51))
        private val TRACK_FILL = JBColor(Color(0x9A, 0x9A, 0x9A), Color(0x6E, 0x6E, 0x6E))
        private val DISABLED_BG = JBColor(Color(0xE0, 0xE0, 0xE0), Color(0x3C, 0x3C, 0x3C))
        private val DISABLED_FILL = JBColor(Color(0xC0, 0xC0, 0xC0), Color(0x4A, 0x4A, 0x4A))
    }
}

/** A small color swatch; clicking opens the platform color chooser and fires [onChange]. */
internal class SwatchButton(initial: Color, private val enableOpacity: Boolean = true) : JComponent() {

    var color: Color = initial
        set(v) {
            field = v
            repaint()
        }

    var onChange: (Color) -> Unit = {}

    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val picked = ColorChooserService.getInstance()
                    .showDialog(this@SwatchButton, "Choose Color", color, enableOpacity) ?: return
                color = picked
                onChange(picked)
            }
        })
    }

    override fun getPreferredSize() = Dimension(JBUI.scale(30), JBUI.scale(18))
    override fun getMaximumSize() = preferredSize
    override fun getMinimumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        CanvasRender.checkerboard(g2, Rectangle(0, 0, width, height))
        g2.color = color
        g2.fillRect(0, 0, width, height)
        g2.color = JBColor.border()
        g2.drawRect(0, 0, width - 1, height - 1)
    }
}
