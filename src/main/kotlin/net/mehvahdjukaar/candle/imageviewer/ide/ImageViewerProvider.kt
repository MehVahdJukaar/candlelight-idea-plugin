package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Registers [ImageViewerFileEditor] as the editor for common raster image formats.
 *
 * Uses [FileEditorPolicy.HIDE_OTHER_EDITORS] so this is the only tab shown, replacing both the text
 * editor and any other (built-in or third-party) image viewer.
 */
class ImageViewerProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.extension?.lowercase() in SUPPORTED_EXTENSIONS

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        IdePlatform.install()
        return ImageViewerFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "candle-image-viewer"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS

    companion object {
        // Formats the AWT Toolkit decodes natively (incl. animated GIFs).
        private val SUPPORTED_EXTENSIONS = setOf("png", "gif", "jpg", "jpeg", "bmp", "wbmp")
    }
}
