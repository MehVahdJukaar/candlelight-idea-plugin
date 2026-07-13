package net.mehvahdjukaar.candle.imageviewer

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.FlowLayout
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * Adds a soft outer glow around the active layer's content: the silhouette is feathered outward and
 * composited behind the pixels in the chosen color. Size and intensity preview live and commit as a
 * single undo step. The glow is clipped to the layer bounds.
 */
internal class OuterGlowProcedure : LayerAdjustProcedure("Outer Glow") {

    private var color = Color(255, 235, 140)
    private var radius = 6      // 1..RADIUS_MAX px
    private var intensity = 0.8f // 0..1

    // The color chooser is a modal dialog, so keep the popup alive while it is up.
    override val dismissOnClickOutside = false

    private val swatch = SwatchButton(color)
    private val sizeSlider = LinearSlider()
    private val intensitySlider = LinearSlider()
    private val sizeValue = valueLabel()
    private val intensityValue = valueLabel()

    override fun focusTarget(controls: JComponent): JComponent = intensitySlider

    override fun buildControls(): JComponent {
        swatch.onChange = { color = it; preview() }
        sizeSlider.fraction = (radius - 1.0) / (RADIUS_MAX - 1.0)
        sizeSlider.onChange = { radius = (1 + sizeSlider.fraction * (RADIUS_MAX - 1)).roundToInt(); preview() }
        intensitySlider.fraction = intensity.toDouble()
        intensitySlider.onChange = { intensity = intensitySlider.fraction.toFloat(); preview() }

        val colorRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(swatch)
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(controlRow("Color", colorRow))
            add(spacer())
            add(controlRow("Radius", sizeSlider, sizeValue))
            add(spacer())
            add(controlRow("Intensity", intensitySlider, intensityValue))
        }
    }

    override fun applyTo(base: BufferedImage) {
        document.applyOuterGlow(base, color, radius, intensity)
        sizeValue.text = "$radius px"
        intensityValue.text = "${(intensity * 100).roundToInt()}%"
    }

    override fun reset() {
        color = Color(255, 235, 140); radius = 6; intensity = 0.8f
        swatch.color = color
        sizeSlider.fraction = (radius - 1.0) / (RADIUS_MAX - 1.0)
        intensitySlider.fraction = intensity.toDouble()
    }

    override fun isNoOp(): Boolean = intensity <= 0f || radius <= 0 || color.alpha == 0

    companion object {
        private const val RADIUS_MAX = 40
    }
}
