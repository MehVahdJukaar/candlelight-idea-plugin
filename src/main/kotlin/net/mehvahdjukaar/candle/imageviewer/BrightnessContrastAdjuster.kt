package net.mehvahdjukaar.candle.imageviewer

import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * A Photoshop-style Brightness / Contrast adjuster for the whole active layer (or the active
 * selection). Both sliders are bipolar - centered at zero - and re-derive the layer from the
 * snapshot taken when the popup opened, so the two shifts compose from the original.
 */
internal class BrightnessContrastAdjuster : LayerAdjustProcedure("Adjust Brightness / Contrast") {

    private var brightness = 0f // -1..1
    private var contrast = 0f   // -1..1

    private val briSlider = BipolarSlider { Color.getHSBColor(0f, 0f, it.toFloat()) }
    private val conSlider = BipolarSlider { f -> // dark ← gray → bright ramp reads as "more contrast"
        val v = (0.5 + (f - 0.5) * 1.6).coerceIn(0.0, 1.0)
        Color.getHSBColor(0f, 0f, v.toFloat())
    }

    private val briValue = valueLabel()
    private val conValue = valueLabel()

    override fun focusTarget(controls: JComponent): JComponent = briSlider

    override fun buildControls(): JComponent {
        briSlider.onChange = { brightness = briSlider.value.toFloat(); preview() }
        conSlider.onChange = { contrast = conSlider.value.toFloat(); preview() }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(controlRow("Brightness", briSlider, briValue))
            add(spacer())
            add(controlRow("Contrast", conSlider, conValue))
        }
    }

    override fun applyTo(base: BufferedImage) {
        document.applyBrightnessContrast(base, brightness, contrast)
        briValue.text = signed((brightness * 100).roundToInt())
        conValue.text = signed((contrast * 100).roundToInt())
    }

    override fun reset() {
        brightness = 0f; contrast = 0f
        briSlider.value = 0.0; conSlider.value = 0.0
    }

    override fun isNoOp(): Boolean = brightness == 0f && contrast == 0f
}
