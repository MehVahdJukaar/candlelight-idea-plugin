package net.mehvahdjukaar.candle.imageviewer.tools

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import javax.swing.Icon

/**
 * Programmatically-drawn toolbar icons (and the shared glyph routines reused by [ToolCursors]) for
 * the editing tools.
 *
 * These are deliberately drawn in code rather than loaded from `AllIcons` paths: a missing
 * `AllIcons` path renders blank at runtime with no compile error and is fragile across IDE
 * versions, whereas these glyphs are crisp, HiDPI/theme-aware and need no bundled assets.
 *
 * Each glyph is a monochrome single-color line drawing in the IntelliJ toolbar aesthetic, painted
 * with a theme-aware [JBColor] so it tracks light/dark themes. All glyphs are authored against a
 * fixed [GLYPH_GRID] x [GLYPH_GRID] logical grid so the same routine serves both the 16px toolbar
 * icon and an arbitrarily-sized cursor.
 */

/** Logical grid every glyph is authored against. */
internal const val GLYPH_GRID = 16

/** Theme-aware foreground used for every glyph. */
private val GLYPH_COLOR: Color get() = JBColor.foreground()

private fun thin(width: Float) =
    BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

// --- Shared glyph routines (grid space = GLYPH_GRID units) --------------------------------------

/** Eyedropper: a dropper held at a 45° angle with the picking tip at the bottom-left (~3, 13). */
internal fun drawEyedropperGlyph(g: Graphics2D) {
    g.stroke = thin(1.2f)
    g.draw(Line2D.Float(4.5f, 11.5f, 11.0f, 5.0f))
    g.draw(Line2D.Float(6.0f, 13.0f, 12.5f, 6.5f))
    g.stroke = thin(1.4f)
    g.draw(Line2D.Float(10.5f, 5.5f, 13.0f, 3.0f))
    g.draw(Line2D.Float(12.0f, 7.0f, 14.5f, 4.5f))
    g.draw(Line2D.Float(11.0f, 4.0f, 14.0f, 7.0f))
    g.stroke = thin(1.2f)
    g.draw(Line2D.Float(4.5f, 11.5f, 3.0f, 13.0f))
    g.draw(Line2D.Float(6.0f, 13.0f, 4.5f, 14.5f))
}

/** Marquee select: a dashed rectangle, like Photoshop's rectangular selection. */
internal fun drawMarqueeGlyph(g: Graphics2D) {
    g.stroke = BasicStroke(
        1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
        floatArrayOf(2f, 1.6f), 0f,
    )
    g.drawRect(2, 2, 12, 12)
}

/** Move: a 4-way arrow cross centered in the grid. */
internal fun drawMoveGlyph(g: Graphics2D) {
    g.stroke = thin(1.2f)
    val c = 8f
    g.draw(Line2D.Float(c, 2.5f, c, 13.5f))
    g.draw(Line2D.Float(2.5f, c, 13.5f, c))
    g.draw(Line2D.Float(c, 2.5f, c - 2f, 4.5f))
    g.draw(Line2D.Float(c, 2.5f, c + 2f, 4.5f))
    g.draw(Line2D.Float(c, 13.5f, c - 2f, 11.5f))
    g.draw(Line2D.Float(c, 13.5f, c + 2f, 11.5f))
    g.draw(Line2D.Float(2.5f, c, 4.5f, c - 2f))
    g.draw(Line2D.Float(2.5f, c, 4.5f, c + 2f))
    g.draw(Line2D.Float(13.5f, c, 11.5f, c - 2f))
    g.draw(Line2D.Float(13.5f, c, 11.5f, c + 2f))
}

/** Pencil: held at 45°, sharp tip pointing to the bottom-left drawing point (~3.5, 12.5). */
internal fun drawPencilGlyph(g: Graphics2D) {
    g.stroke = thin(1.2f)
    g.draw(Line2D.Float(11.0f, 3.0f, 5.5f, 8.5f))
    g.draw(Line2D.Float(13.0f, 5.0f, 7.5f, 10.5f))
    g.draw(Line2D.Float(11.0f, 3.0f, 13.0f, 5.0f))
    g.draw(Line2D.Float(5.5f, 8.5f, 7.5f, 10.5f))
    g.draw(Line2D.Float(5.5f, 8.5f, 3.5f, 12.5f))
    g.draw(Line2D.Float(7.5f, 10.5f, 3.5f, 12.5f))
}

/** Eraser: a rubber block held at an angle with a small motion stroke near the tip. */
internal fun drawEraserGlyph(g: Graphics2D) {
    g.stroke = thin(1.2f)
    g.draw(Line2D.Float(11.5f, 3.5f, 14.0f, 6.0f))
    g.draw(Line2D.Float(14.0f, 6.0f, 7.5f, 12.5f))
    g.draw(Line2D.Float(7.5f, 12.5f, 5.0f, 10.0f))
    g.draw(Line2D.Float(5.0f, 10.0f, 11.5f, 3.5f))
    g.draw(Line2D.Float(7.0f, 8.0f, 9.5f, 10.5f))
    g.draw(Line2D.Float(3.0f, 13.5f, 5.5f, 13.5f))
}

/**
 * Prepare [g] for glyph drawing: antialiasing, pure strokes, the theme [GLYPH_COLOR] and a unit
 * scale that maps the [GLYPH_GRID] authoring grid onto [targetPx] device pixels.
 */
internal fun setupGlyphGraphics(g: Graphics2D, targetPx: Int) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.color = GLYPH_COLOR
    val s = targetPx.toDouble() / GLYPH_GRID
    g.scale(s, s)
}

// --- Toolbar icons ------------------------------------------------------------------------------

/**
 * Base class for the tool toolbar glyphs. Sets up an antialiased [Graphics2D] scaled via
 * [JBUI.scale] so subclasses draw against the fixed [GLYPH_GRID] grid and get correct HiDPI output.
 */
abstract class ToolIcon : Icon {

    override fun getIconWidth(): Int = JBUI.scale(GLYPH_GRID)
    override fun getIconHeight(): Int = JBUI.scale(GLYPH_GRID)

    final override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.translate(x, y)
            setupGlyphGraphics(g2, JBUI.scale(GLYPH_GRID))
            paintGlyph(g2)
        } finally {
            g2.dispose()
        }
    }

    /** Draw the glyph against the [GLYPH_GRID] grid; color/scale/AA are pre-set. */
    protected abstract fun paintGlyph(g: Graphics2D)
}

/** Eyedropper toolbar icon. */
class EyedropperIcon : ToolIcon() {
    override fun paintGlyph(g: Graphics2D) = drawEyedropperGlyph(g)
}

/** Marquee-select toolbar icon. */
class MarqueeIcon : ToolIcon() {
    override fun paintGlyph(g: Graphics2D) = drawMarqueeGlyph(g)
}

/** Move toolbar icon. */
class MoveIcon : ToolIcon() {
    override fun paintGlyph(g: Graphics2D) = drawMoveGlyph(g)
}

/** Pencil toolbar icon. */
class PencilIcon : ToolIcon() {
    override fun paintGlyph(g: Graphics2D) = drawPencilGlyph(g)
}

/** Eraser toolbar icon. */
class EraserIcon : ToolIcon() {
    override fun paintGlyph(g: Graphics2D) = drawEraserGlyph(g)
}
