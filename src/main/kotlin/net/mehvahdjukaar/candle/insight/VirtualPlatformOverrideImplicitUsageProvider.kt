package net.mehvahdjukaar.candle.insight

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import net.mehvahdjukaar.candle.util.findPlatformVirtualOverrides

class VirtualPlatformOverrideImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        when (element) {
            is PsiMethod -> return element.findPlatformVirtualOverrides().isNotEmpty()
            is PsiParameter -> {
                val parent = element.parent.parent
                if (parent is PsiMethod) {
                    return parent.findPlatformVirtualOverrides().isNotEmpty()
                }
            }
        }
        return false
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false
    override fun isImplicitWrite(element: PsiElement): Boolean = false
}
