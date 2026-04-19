package net.mehvahdjukaar.candle.gutter

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiMethod
import net.mehvahdjukaar.candle.util.findPlatformVirtualOverrides
import javax.swing.Icon

class VirtualOverrideLineMarkerProvider :
    RelatedMethodLineMarkerProvider<PsiMethod>() {
    override val tooltipTranslationKey = "gutter.virtualOverride.tooltip"
    override val navTitleTranslationKey = "gutter.virtualOverride.popup"
    override val PsiMethod.relatedMethods: Set<PsiMethod> get() = findPlatformVirtualOverrides()
    override val converter = PsiMethodConverter.JAVA

    override fun getName(): String = "Virtual Override line marker"
    override fun getIcon(): Icon = AllIcons.Gutter.OverridingMethod
}
