package net.mehvahdjukaar.candle.gutter

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiMethod
import net.mehvahdjukaar.candle.util.commonMethods
import javax.swing.Icon

class PlatformMethodLineMarkerProvider :
    RelatedMethodLineMarkerProvider<PsiMethod>() {
    override val tooltipTranslationKey = "gutter.goToCommon"
    override val navTitleTranslationKey = "gutter.chooseCommon"
    override val PsiMethod.relatedMethods: Set<PsiMethod> get() = commonMethods
    override val converter = PsiMethodConverter.JAVA

    override fun getName(): String = "Platform implementation method line marker"
    override fun getIcon(): Icon = AllIcons.Gutter.ImplementingMethod
}
