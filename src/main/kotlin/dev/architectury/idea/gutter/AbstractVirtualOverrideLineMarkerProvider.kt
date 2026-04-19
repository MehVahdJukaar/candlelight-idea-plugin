package dev.architectury.idea.gutter

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import dev.architectury.idea.util.findPlatformVirtualOverrides
import javax.swing.Icon

abstract class AbstractVirtualOverrideLineMarkerProvider<M : PsiElement> :
    RelatedMethodLineMarkerProvider<M>() {
    override val tooltipTranslationKey = "architectury.gutter.virtualOverride.tooltip"
    override val navTitleTranslationKey = "architectury.gutter.virtualOverride.popup"
    override val PsiMethod.relatedMethods: Set<PsiMethod> get() = findPlatformVirtualOverrides()

    override fun getName(): String = "Virtual Override line marker"
    override fun getIcon(): Icon = AllIcons.Gutter.OverridingMethod
}
