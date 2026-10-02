package net.mehvahdjukaar.candle.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

class MethodVisibilityHighlightVisitor : HighlightVisitor {

    private var holder: HighlightInfoHolder? = null

    override fun suitableForFile(file: PsiFile): Boolean = file is PsiJavaFile

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        this.holder = holder
        try {
            action.run()
        } finally {
            this.holder = null
        }
        return true
    }

    override fun visit(element: PsiElement) {
        //on the method, not the name, so edits to the modifier list re-highlight it
        val method = element as? PsiMethod ?: return
        val name = method.nameIdentifier ?: return

        val type = when {
            method.hasModifierProperty(PsiModifier.PRIVATE) -> PRIVATE_METHOD_TYPE
            method.hasModifierProperty(PsiModifier.PROTECTED) -> PROTECTED_METHOD_TYPE
            method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) -> PACKAGE_PRIVATE_METHOD_TYPE
            else -> return
        }
        holder?.add(HighlightInfo.newHighlightInfo(type).range(name).create())
    }

    override fun clone(): HighlightVisitor = MethodVisibilityHighlightVisitor()

    companion object {
        val PRIVATE_METHOD = TextAttributesKey.createTextAttributesKey("CANDLE_PRIVATE_METHOD")
        val PROTECTED_METHOD = TextAttributesKey.createTextAttributesKey("CANDLE_PROTECTED_METHOD")
        val PACKAGE_PRIVATE_METHOD = TextAttributesKey.createTextAttributesKey("CANDLE_PACKAGE_PRIVATE_METHOD")

        private val PRIVATE_METHOD_TYPE =
            HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, PRIVATE_METHOD)
        private val PROTECTED_METHOD_TYPE =
            HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, PROTECTED_METHOD)
        private val PACKAGE_PRIVATE_METHOD_TYPE =
            HighlightInfoType.HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, PACKAGE_PRIVATE_METHOD)
    }
}
