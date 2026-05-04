package net.mehvahdjukaar.candle.refactor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import net.mehvahdjukaar.candle.util.commonMethods
import net.mehvahdjukaar.candle.util.hasPlatformImplAnnotation
import net.mehvahdjukaar.candle.util.platformMethods

class PlatformImplRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is PsiMethod && (element.hasPlatformImplAnnotation || element.commonMethods.isNotEmpty())
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (element !is PsiMethod) return

        val relatedMethods = mutableSetOf<PsiMethod>()
        if (element.hasPlatformImplAnnotation) {
            relatedMethods.add(element)
            relatedMethods.addAll(element.platformMethods)
        } else {
            val commons = element.commonMethods
            relatedMethods.addAll(commons)
            commons.forEach {
                relatedMethods.addAll(it.platformMethods)
            }
            // If the element itself is not in relatedMethods (e.g. it's one of the platform implementations), add it
            relatedMethods.add(element)
        }

        for (method in relatedMethods) {
            if (method != element) {
                allRenames[method] = newName
            }
        }
    }
}
