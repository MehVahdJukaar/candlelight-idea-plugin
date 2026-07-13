package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * Overlays a two-color gradient onto the active layer's opaque pixels (or the selection). Supports a
 * linear gradient at an arbitrary angle or a radial one, with an adjustable scale and opacity. Two
 * swatches open the platform color chooser; everything previews live and commits as one undo step.
 */
internal class GradientOverlayProcedure : LayerAdjustProcedure("Gradient Overlay") {

    private var color1 = Color(0, 0, 0)
    private var color2 = Color(255, 255, 255)
    private var radial = false
    private var angleDeg = 0.0     // 0..360
    private var scale = 1.0        // 0.2..3.0
    private var opacity = 1.0f     // 0..1

    // The color chooser is a modal dialog, so keep the popup alive while it is up.
    override val dismissOnClickOutside = false

    private val swatch1 = SwatchButton(color1)
    private val swatch2 = SwatchButton(color2)
    private val radialCheck = JBCheckBox("Radial").apply { font = JBUI.Fonts.miniFont(); isOpaque = false }
    private val angleSlider = LinearSlider()
    private val scaleSlider = LinearSlider()
    private val opacitySlider = LinearSlider()
    private val angleValue = valueLabel()
    private val scaleValue = valueLabel()
    private val opacityValue = valueLabel()

    override fun focusTarget(controls: JComponent): JComponent = opacitySlider

    override fun buildControls(): JComponent {
        swatch1.onChange = { color1 = it; preview() }
        swatch2.onChange = { color2 = it; preview() }
        radialCheck.addActionListener {
            radial = radialCheck.isSelected
            angleSlider.isEnabled = !radial
            preview()
        }
        angleSlider.fraction = angleDeg / 360.0
        angleSlider.onChange = { angleDeg = angleSlider.fraction * 360.0; preview() }
        scaleSlider.fraction = (scale - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)
        scaleSlider.onChange = { scale = SCALE_MIN + scaleSlider.fraction * (SCALE_MAX - SCALE_MIN); preview() }
        opacitySlider.fraction = opacity.toDouble()
        opacitySlider.onChange = { opacity = opacitySlider.fraction.toFloat(); preview() }

        val colors = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(swatch1)
            add(JLabel("→").apply { font = JBUI.Fonts.miniFont() })
            add(swatch2)
            add(swap())
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(controlRow("Colors", colors))
            add(spacer())
            add(controlRow("Type", radialLeft()))
            add(spacer())
            add(controlRow("Angle", angleSlider, angleValue))
            add(spacer())
            add(controlRow("Scale", scaleSlider, scaleValue))
            add(spacer())
            add(controlRow("Opacity", opacitySlider, opacityValue))
        }
    }

    private fun swap(): JComponent = miniButton("Swap") {
        val tmp = color1; color1 = color2; color2 = tmp
        swatch1.color = color1; swatch2.color = color2
        preview()
    }

    private fun radialLeft(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        add(radialCheck)
    }

    override fun applyTo(base: BufferedImage) {
        document.applyGradientOverlay(base, color1, color2, radial, angleDeg, scale, opacity)
        angleValue.text = if (radial) "-" else "${angleDeg.roundToInt()}°"
        scaleValue.text = String.format("%.2f", scale)
        opacityValue.text = "${(opacity * 100).roundToInt()}%"
    }

    override fun reset() {
        color1 = Color(0, 0, 0); color2 = Color(255, 255, 255)
        radial = false; angleDeg = 0.0; scale = 1.0; opacity = 1.0f
        swatch1.color = color1; swatch2.color = color2
        radialCheck.isSelected = false
        angleSlider.isEnabled = true
        angleSlider.fraction = 0.0
        scaleSlider.fraction = (scale - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)
        opacitySlider.fraction = 1.0
    }

    override fun isNoOp(): Boolean = opacity <= 0f

    companion object {
        private const val SCALE_MIN = 0.2
        private const val SCALE_MAX = 3.0
    }
}
