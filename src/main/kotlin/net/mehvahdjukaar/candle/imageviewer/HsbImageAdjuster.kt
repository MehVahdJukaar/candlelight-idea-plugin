package net.mehvahdjukaar.candle.imageviewer

import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * A Photoshop-style Hue / Saturation / Brightness adjuster for the whole active layer (or the active
 * selection). Each slider is bipolar - centered at zero - and dragging any of them re-derives the
 * layer from the snapshot taken when the popup opened, so the three shifts always compose from the
 * original rather than stacking.
 */
internal class HsbImageAdjuster : LayerAdjustProcedure("Adjust Hue / Saturation / Brightness") {

    private var hue = 0f   // -1..1  → -180°..+180° of hue rotation (i.e. ±0.5 turns)
    private var sat = 0f   // -1..1  → fully desaturated .. fully saturated
    private var bri = 0f   // -1..1  → black .. white

    // A mid reference color for the saturation track, so it reads as "grayer ← → more vivid".
    private val hueRef = Color.getHSBColor(0.58f, 0.7f, 0.9f)
    private val hueSlider = BipolarSlider { Color.getHSBColor(it.toFloat(), 1f, 1f) }
    private val satSlider = BipolarSlider { f ->
        val h = Color.RGBtoHSB(hueRef.red, hueRef.green, hueRef.blue, null)
        Color.getHSBColor(h[0], f.toFloat(), 0.9f)
    }
    private val briSlider = BipolarSlider { Color.getHSBColor(0f, 0f, it.toFloat()) }

    private val hueValue = valueLabel()
    private val satValue = valueLabel()
    private val briValue = valueLabel()

    override fun focusTarget(controls: JComponent): JComponent = hueSlider

    override fun buildControls(): JComponent {
        hueSlider.onChange = { hue = hueSlider.value.toFloat(); preview() }
        satSlider.onChange = { sat = satSlider.value.toFloat(); preview() }
        briSlider.onChange = { bri = briSlider.value.toFloat(); preview() }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(controlRow("Hue", hueSlider, hueValue))
            add(spacer())
            add(controlRow("Saturation", satSlider, satValue))
            add(spacer())
            add(controlRow("Brightness", briSlider, briValue))
        }
    }

    override fun applyTo(base: BufferedImage) {
        document.applyHsbAdjustment(base, hue * 0.5f, sat, bri)
        hueValue.text = "${(hue * 180).roundToInt()}°"
        satValue.text = signed((sat * 100).roundToInt())
        briValue.text = signed((bri * 100).roundToInt())
    }

    override fun reset() {
        hue = 0f; sat = 0f; bri = 0f
        hueSlider.value = 0.0; satSlider.value = 0.0; briSlider.value = 0.0
    }

    override fun isNoOp(): Boolean = hue == 0f && sat == 0f && bri == 0f
}
