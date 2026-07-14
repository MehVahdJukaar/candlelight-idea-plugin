package net.mehvahdjukaar.candle.imageviewer.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import net.mehvahdjukaar.candle.imageviewer.platform.EditorFile
import java.io.IOException

/** Adapts an IDE [VirtualFile] to the editor's [EditorFile]; writes go through a WriteAction. */
class IdeEditorFile(val vfile: VirtualFile) : EditorFile {
    override fun name(): String = vfile.name
    override fun extension(): String? = vfile.extension

    override fun writeBytes(bytes: ByteArray) {
        WriteAction.run<IOException> { vfile.setBinaryContent(bytes) }
    }

    override fun readSibling(fileName: String): ByteArray? =
        vfile.parent?.findChild(fileName)?.contentsToByteArray()
}
