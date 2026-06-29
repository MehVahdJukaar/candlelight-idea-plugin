package net.mehvahdjukaar.candle.imageviewer

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Editor state shared by every open image editor in the IDE session, so it carries over when the
 * user switches between images: the last picked foreground color and the copy/paste clipboard.
 *
 * The color and the active tool are observable: changing either notifies all open editors so they
 * stay in sync live, not just when a new image is opened.
 */
object SharedEditorState {

    /** The most recently selected foreground color; seeds each newly opened editor's color picker. */
    var lastColor: Color = JBColor.black
        private set

    /** The id of the most recently selected tool; seeds each newly opened editor's active tool. */
    var lastToolId: String = "pencil"
        private set

    /** The last copied or cut selection, as a standalone ARGB image, or null if nothing was copied. */
    var clipboard: BufferedImage? = null

    // CopyOnWrite so a listener firing mid-iteration (e.g. an editor being shown/hidden) is safe.
    private val colorListeners = CopyOnWriteArrayList<(Color) -> Unit>()
    private val toolListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun addColorListener(listener: (Color) -> Unit) = colorListeners.add(listener)
    fun removeColorListener(listener: (Color) -> Unit) = colorListeners.remove(listener)

    fun addToolListener(listener: (String) -> Unit) = toolListeners.add(listener)
    fun removeToolListener(listener: (String) -> Unit) = toolListeners.remove(listener)

    /** Sets the shared color and notifies every open editor. No-op (and no notify) if unchanged. */
    fun setColor(color: Color) {
        if (color.rgb == lastColor.rgb) return
        lastColor = color
        colorListeners.forEach { it(color) }
    }

    /** Sets the shared tool id and notifies every open editor. No-op (and no notify) if unchanged. */
    fun setTool(toolId: String) {
        if (toolId == lastToolId) return
        lastToolId = toolId
        toolListeners.forEach { it(toolId) }
    }
}
