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
        // if the method is implementing a common PlatformImpl method mark it as used
        if (element is PsiMethod) {
            return element.commonMethods.isNotEmpty()
        }

        // if the method is annotated with PlatformImpl or implements an PlatformImpl method,
        // mark all of its parameters as used.
        if (element is PsiParameter) {
            // the method is the parent's parent
            val parent = element.parent.parent

            return parent is PsiMethod && (parent.hasPlatformImplAnnotation || parent.commonMethods.isNotEmpty())
        }

        // NEW: mark containing class as used if it contains at least one relevant method
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
