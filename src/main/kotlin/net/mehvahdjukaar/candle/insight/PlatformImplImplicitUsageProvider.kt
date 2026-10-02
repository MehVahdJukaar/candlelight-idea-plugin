package net.mehvahdjukaar.candle.insight

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import net.mehvahdjukaar.candle.util.commonMethods
import net.mehvahdjukaar.candle.util.hasPlatformImplAnnotation

class PlatformImplImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (element is PsiMethod) {
            return element.commonMethods.isNotEmpty()
        }

        if (element is PsiParameter) {
            val method = element.parent.parent
            return method is PsiMethod && (method.hasPlatformImplAnnotation || method.commonMethods.isNotEmpty())
        }

        if (element is PsiClass) {
            return element.methods.any { method ->
                method.commonMethods.isNotEmpty() || method.hasPlatformImplAnnotation
            }
        }

        return false
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = false
}
