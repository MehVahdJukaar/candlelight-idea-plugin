package net.mehvahdjukaar.candle.gutter

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiMethod
import net.mehvahdjukaar.candle.util.platformMethods
import javax.swing.Icon

class CommonMethodLineMarkerProvider  :
    RelatedMethodLineMarkerProvider<PsiMethod>() {
    override val tooltipTranslationKey = "architectury.gutter.goToPlatform"
    override val navTitleTranslationKey = "architectury.gutter.chooseImpl"
    override val PsiMethod.relatedMethods: Set<PsiMethod> get() = platformMethods
    override val converter = PsiMethodConverter.JAVA

    override fun getName(): String = "ExpectPlatform line marker"
    override fun getIcon(): Icon = AllIcons.Gutter.ImplementedMethod
}

