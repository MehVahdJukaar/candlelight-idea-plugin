package net.mehvahdjukaar.candle.imageviewer

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.beans.PropertyChangeListener
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.SwingConstants

/** A bare-bones [FileEditor] that simply shows an image (animated GIFs included). */
class ImageViewerFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val component: JComponent = build()

    private fun build(): JComponent = try {
        // ImageIcon loads the bytes synchronously (so dimensions are known) and animates GIFs.
        val icon = ImageIcon(file.contentsToByteArray())
        if (icon.iconWidth <= 0 || icon.iconHeight <= 0) {
            errorLabel("Unable to read image: ${file.name}")
        } else {
            ImageViewerComponent(icon.image, icon.iconWidth, icon.iconHeight)
        }
    } catch (t: Throwable) {
        errorLabel("Failed to load ${file.name}: ${t.message}")
    }

    private fun errorLabel(message: String): JComponent =
        JBLabel(message, SwingConstants.CENTER).apply { border = JBUI.Borders.empty(20) }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName(): String = "Image"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {}
}
