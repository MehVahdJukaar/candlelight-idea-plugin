package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * Shows an image. Static formats (png/jpg/bmp) open in the editable [ImageEditorPanel]; animated GIFs
 * and anything ImageIO can't decode fall back to the view-only [ImageViewerComponent].
 */
class ImageViewerFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val listeners = mutableListOf<PropertyChangeListener>()
    private var modified = false
    private lateinit var focusComponent: JComponent
    private val component: JComponent = build()

    private fun build(): JComponent = try {
        val bytes = file.contentsToByteArray()
        val ext = file.extension?.lowercase()
        // GIFs are kept animated and view-only; ImageIO.read would only return the first frame.
        val decoded = if (ext == "gif") null else runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
        if (decoded != null) {
            ImageEditorPanel(file, decoded) { setModified(it) }.also { focusComponent = it.preferredFocus }
        } else {
            val icon = ImageIcon(bytes)
            if (icon.iconWidth <= 0) {
                errorLabel("Unable to read image: ${file.name}").also { focusComponent = it }
            } else {
                ImageViewerComponent(icon.image, icon.iconWidth, icon.iconHeight).also { focusComponent = it }
            }
        }
    } catch (t: Throwable) {
        errorLabel("Failed to load ${file.name}: ${t.message}").also { focusComponent = it }
    }

    private fun errorLabel(message: String): JComponent =
        JBLabel(message, SwingConstants.CENTER).apply { border = JBUI.Borders.empty(20) }

    private fun setModified(value: Boolean) {
        if (value == modified) return
        val old = modified
        modified = value
        // FileEditor.PROP_MODIFIED is not a Kotlin-visible constant here; its value is "modified".
        val event = PropertyChangeEvent(this, "modified", old, value)
        listeners.toList().forEach { it.propertyChange(event) }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = focusComponent
    override fun getName(): String = "Image"
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = modified
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) { listeners.add(listener) }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) { listeners.remove(listener) }
    override fun getFile(): VirtualFile = file
    override fun dispose() {}
}
