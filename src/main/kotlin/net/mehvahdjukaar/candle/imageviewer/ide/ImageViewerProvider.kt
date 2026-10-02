package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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
        private val SUPPORTED_EXTENSIONS = setOf("png", "gif", "jpg", "jpeg", "bmp", "wbmp")
    }
}
