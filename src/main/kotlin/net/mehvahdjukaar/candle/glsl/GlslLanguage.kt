package net.mehvahdjukaar.candle.glsl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.FileViewProvider
import javax.swing.Icon

//own id so it doesnt clash with other GLSL plugins
object GlslLanguage : Language("CandleGLSL") {
    private fun readResolve(): Any = GlslLanguage

    override fun getDisplayName(): String = "GLSL"
}

object GlslFileType : LanguageFileType(GlslLanguage) {
    private val ICON = IconLoader.getIcon("/icons/glsl/shader.svg", GlslFileType::class.java)

    override fun getName(): String = "Candle GLSL"

    override fun getDescription(): String = "GLSL shader"

    override fun getDefaultExtension(): String = "glsl"

    override fun getIcon(): Icon = ICON
}


class GlslFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GlslLanguage) {
    override fun getFileType(): FileType = GlslFileType
}
