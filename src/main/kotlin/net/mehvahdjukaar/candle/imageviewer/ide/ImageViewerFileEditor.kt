package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import net.mehvahdjukaar.candle.imageviewer.format.GifStrip
import net.mehvahdjukaar.candle.imageviewer.format.McMeta
import net.mehvahdjukaar.candle.imageviewer.model.Animation
import net.mehvahdjukaar.candle.imageviewer.view.ImageEditorPanel
import net.mehvahdjukaar.candle.imageviewer.widget.ImageViewerComponent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.SwingConstants

class ImageViewerFileEditor(private val project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val listeners = mutableListOf<PropertyChangeListener>()
    private var modified = false
    private val editorFile = IdeEditorFile(file)
    private var editorPanel: ImageEditorPanel? = null
    private lateinit var focusComponent: JComponent
    private val component: JComponent = build()

    init {
        //ide doesnt ask to save custom editors on close, edits would just be lost
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener.Before {
                override fun beforeFileClosed(source: FileEditorManager, closedFile: VirtualFile) {
                    if (closedFile == file) promptSaveOnClose()
                }
            },
        )
    }

    //no cancel button, the close cant be vetoed from here
    private fun promptSaveOnClose() {
        val panel = editorPanel ?: return
        if (!panel.hasUnsavedChanges()) return
        val choice = Messages.showYesNoDialog(
            project,
            "Save changes to ${file.name} before closing?",
            "Unsaved Image Changes",
            "Save",
            "Don't Save",
            Messages.getQuestionIcon(),
        )
        if (choice == Messages.YES) panel.saveNow()
    }

    private fun build(): JComponent = try {
        val bytes = file.contentsToByteArray()
        val ext = file.extension?.lowercase()
        if (ext == "gif") {
            val gif = runCatching { GifStrip.decode(bytes) }.getOrNull()
            if (gif != null) {
                ImageEditorPanel(editorFile, gif.strip, gif.frameCount, gif.frameDurationTicks, true) { setModified(it) }
                    .also { editorPanel = it; focusComponent = it.preferredFocus }
            } else {
                viewOnly(bytes)
            }
        } else {
            val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            if (decoded != null) {
                val meta = McMeta.readFor(editorFile, decoded.width, decoded.height)
                ImageEditorPanel(
                    editorFile, decoded,
                    meta?.frameCount ?: 1,
                    meta?.frameDurationTicks ?: Animation.DEFAULT_DURATION_TICKS,
                    false,
                ) { setModified(it) }.also { editorPanel = it; focusComponent = it.preferredFocus }
            } else {
                viewOnly(bytes)
            }
        }
    } catch (t: Throwable) {
        errorLabel("Failed to load ${file.name}: ${t.message}").also { focusComponent = it }
    }

    private fun viewOnly(bytes: ByteArray): JComponent {
        val icon = ImageIcon(bytes)
        return if (icon.iconWidth <= 0) {
            errorLabel("Unable to read image: ${file.name}").also { focusComponent = it }
        } else {
            ImageViewerComponent(icon.image, icon.iconWidth, icon.iconHeight).also { focusComponent = it }
        }
    }

    private fun errorLabel(message: String): JComponent =
        JBLabel(message, SwingConstants.CENTER).apply { border = JBUI.Borders.empty(20) }

    private fun setModified(value: Boolean) {
        if (value == modified) return
        val old = modified
        modified = value
        val event = PropertyChangeEvent(this, "modified", old, value) // PROP_MODIFIED isn't visible from kotlin
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
