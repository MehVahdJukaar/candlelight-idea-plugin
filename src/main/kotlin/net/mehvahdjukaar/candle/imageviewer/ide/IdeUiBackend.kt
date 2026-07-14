package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.mehvahdjukaar.candle.imageviewer.platform.UiBackend
import java.awt.Color
import java.awt.Font

/** Backs the editor's [UiBackend] with IntelliJ's JBUI/JBColor/UIUtil. */
object IdeUiBackend : UiBackend {
    override fun scale(i: Int): Int = JBUI.scale(i)
    override fun miniFont(): Font = JBUI.Fonts.miniFont()
    override fun labelFont(): Font = UIUtil.getLabelFont()

    override fun color(light: Color, dark: Color): Color = JBColor(light, dark)
    override fun namedColor(key: String, fallback: Color): Color = JBColor.namedColor(key, fallback)

    override fun gray(): Color = JBColor.GRAY
    override fun black(): Color = JBColor.black
    override fun white(): Color = JBColor.white
    override fun blue(): Color = JBColor.blue
    override fun border(): Color = JBColor.border()
    override fun foreground(): Color = JBColor.foreground()
    override fun background(): Color = JBColor.background()

    override fun actionHoverBackground(): Color = JBUI.CurrentTheme.ActionButton.hoverBackground()
    override fun actionPressedBackground(): Color = JBUI.CurrentTheme.ActionButton.pressedBackground()
    override fun actionPressedBorder(): Color = JBUI.CurrentTheme.ActionButton.pressedBorder()
}
