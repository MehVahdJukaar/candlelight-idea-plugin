package net.mehvahdjukaar.candle.refactor

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import net.mehvahdjukaar.candle.util.commonClassFromImpl
import net.mehvahdjukaar.candle.util.commonMethods
import net.mehvahdjukaar.candle.util.commonSimpleNameFromImpl
import net.mehvahdjukaar.candle.util.hasPlatformImplAnnotation
import net.mehvahdjukaar.candle.util.implSimpleNameForCommon
import net.mehvahdjukaar.candle.util.isPlatformImplClass
import net.mehvahdjukaar.candle.util.participatesInPlatformImplGraph
import net.mehvahdjukaar.candle.util.platformImplClasses
import net.mehvahdjukaar.candle.util.platformMethods

class PlatformImplRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = when (element) {
        is PsiMethod -> element.hasPlatformImplAnnotation || element.commonMethods.isNotEmpty()
        is PsiClass -> element.participatesInPlatformImplGraph()
        else -> false
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        when (element) {
            is PsiMethod -> prepareMethodRenaming(element, newName, allRenames)
            is PsiClass -> prepareClassRenaming(element, newName, allRenames)
        }
    }

    private fun prepareMethodRenaming(element: PsiMethod, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val relatedMethods = mutableSetOf<PsiMethod>()
        if (element.hasPlatformImplAnnotation) {
            relatedMethods.add(element)
            relatedMethods.addAll(element.platformMethods)
        } else {
            val commons = element.commonMethods
            relatedMethods.addAll(commons)
            commons.forEach { relatedMethods.addAll(it.platformMethods) }
            relatedMethods.add(element)
        }

        for (method in relatedMethods) {
            if (method != element) {
                allRenames[method] = newName
            }
        }
    }

    private fun prepareClassRenaming(element: PsiClass, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (element.isPlatformImplClass()) {
            val commonClass = element.commonClassFromImpl()
            val relatedImpls = element.platformImplClasses().ifEmpty { listOf(element) }
            val newCommonName = commonSimpleNameFromImpl(newName)
            val newImplName = implSimpleNameForCommon(newCommonName)

            commonClass?.takeIf { it != element }?.let { allRenames[it] = newCommonName }
            for (implClass in relatedImpls) {
                if (implClass != element) {
                    allRenames[implClass] = newImplName
                }
            }
            return
        }

        val newImplName = implSimpleNameForCommon(newName)
        for (implClass in element.platformImplClasses()) {
            allRenames[implClass] = newImplName
        }
    }
}
