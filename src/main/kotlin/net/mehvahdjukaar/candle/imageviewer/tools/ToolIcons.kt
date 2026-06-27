package net.mehvahdjukaar.candle.imageviewer.tools

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import javax.swing.Icon

/**
 * Toolbar icons for the editing tools, loaded from bundled SVGs (Lucide, ISC-licensed) under
 * `resources/icons/imageviewer/`. Each icon has a `*_dark.svg` sibling so [IconLoader] picks the
 * right contrast for the active theme.
 *
 * This file also keeps the small set of programmatically-drawn glyph routines used by [ToolCursors]
 * to build custom mouse cursors — cursors need a raster bitmap with a precise hotspot, which is
 * simpler to author in code than to derive from the SVGs.
 */
object ToolIcons {
    val PICK: Icon = load("pick")
    val SELECT: Icon = load("select")
    val MOVE: Icon = load("move")
    val PENCIL: Icon = load("pencil")
    val ERASER: Icon = load("eraser")
    val ZOOM: Icon = load("zoom")
    val RECOLOR: Icon = load("recolor")

    private fun load(name: String): Icon = IconLoader.getIcon("/icons/imageviewer/$name.svg", javaClass)
}

// --- Cursor glyphs (grid space = GLYPH_GRID units) ----------------------------------------------
//
// Drawn in code rather than reused from the SVGs because a cursor needs a fixed-size raster image
// with a precise hotspot. Each glyph is authored against a GLYPH_GRID x GLYPH_GRID logical grid so
// the same routine renders at any cursor size.

/** Logical grid every cursor glyph is authored against. */
internal const val GLYPH_GRID = 16

/** Theme-aware foreground used for every cursor glyph. */
private val GLYPH_COLOR: Color get() = JBColor.foreground()

private fun thin(width: Float) =
    BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

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

/** Magnifier: a lens centered around (~7, 7) with a handle to the bottom-right and a small plus. */
internal fun drawZoomGlyph(g: Graphics2D) {
    g.stroke = thin(1.4f)
    g.draw(java.awt.geom.Ellipse2D.Float(3f, 3f, 8f, 8f))
    g.draw(Line2D.Float(10.0f, 10.0f, 13.5f, 13.5f))
    g.stroke = thin(1.1f)
    g.draw(Line2D.Float(7.0f, 5.0f, 7.0f, 9.0f))
    g.draw(Line2D.Float(5.0f, 7.0f, 9.0f, 7.0f))
}

/**
 * Prepare [g] for cursor-glyph drawing: antialiasing, pure strokes, the theme [GLYPH_COLOR] and a
 * unit scale that maps the [GLYPH_GRID] authoring grid onto [targetPx] device pixels.
 */
internal fun setupGlyphGraphics(g: Graphics2D, targetPx: Int) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.color = GLYPH_COLOR
    val s = targetPx.toDouble() / GLYPH_GRID
    g.scale(s, s)
}
