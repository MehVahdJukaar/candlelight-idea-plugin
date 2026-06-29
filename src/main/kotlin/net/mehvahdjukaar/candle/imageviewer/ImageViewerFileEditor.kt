package net.mehvahdjukaar.candle.imageviewer

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
class ImageViewerFileEditor(private val project: Project, private val file: VirtualFile) :
    UserDataHolderBase(), FileEditor {

    private val listeners = mutableListOf<PropertyChangeListener>()
    private var modified = false
    private var editorPanel: ImageEditorPanel? = null
    private lateinit var focusComponent: JComponent
    private val component: JComponent = build()

    init {
        // The platform doesn't prompt to save custom (non-document) editors when their tab closes,
        // so an edited image would be silently discarded. Catch the close and offer to save first.
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener.Before {
                override fun beforeFileClosed(source: FileEditorManager, closedFile: VirtualFile) {
                    if (closedFile == file) promptSaveOnClose()
                }
            },
        )
    }

    /**
     * Asks the user whether to persist unsaved edits before the tab closes. The platform gives custom
     * editors no way to veto the close, so this is a two-way Save / Don't Save choice (no Cancel).
     */
    private fun promptSaveOnClose() {
        val panel = editorPanel ?: return
        if (!panel.hasUnsavedChanges) return
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
            // Multi-frame GIFs open as a (read-only) sprite strip so the Animation controls can scrub
            // and play them; single-frame or undecodable GIFs fall back to the view-only component.
            val gif = runCatching { GifStrip.decode(bytes) }.getOrNull()
            if (gif != null) {
                ImageEditorPanel(file, gif.strip, gif.frameCount, gif.frameDurationTicks, readOnly = true, onModifiedChanged = { setModified(it) })
                    .also { editorPanel = it; focusComponent = it.preferredFocus }
            } else {
                viewOnly(bytes)
            }
        } else {
            // Static formats decode to one editable image. A sibling .mcmeta (Minecraft animation
            // metadata) pre-fills the frame count and speed when present.
            val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            if (decoded != null) {
                val meta = McMeta.readFor(file, decoded.width, decoded.height)
                ImageEditorPanel(
                    file, decoded,
                    initialFrames = meta?.frameCount ?: 1,
                    initialDurationTicks = meta?.frameDurationTicks ?: Animation.DEFAULT_DURATION_TICKS,
                    onModifiedChanged = { setModified(it) },
                ).also { editorPanel = it; focusComponent = it.preferredFocus }
            } else {
                viewOnly(bytes)
            }
        }
    } catch (t: Throwable) {
        errorLabel("Failed to load ${file.name}: ${t.message}").also { focusComponent = it }
    }

    /** View-only fallback for images ImageIO can't open editably (e.g. odd GIFs). */
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
