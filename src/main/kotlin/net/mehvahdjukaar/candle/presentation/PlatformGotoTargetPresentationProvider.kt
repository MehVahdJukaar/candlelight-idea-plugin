package net.mehvahdjukaar.candle.presentation

import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import net.mehvahdjukaar.candle.settings.CandleSettings
import net.mehvahdjukaar.candle.presentation.PrefixStyle

/**
 * Adds [F]/[N]/[G]/[C] prefixes to Go to Class and Search Everywhere class/symbol entries.
 */
class PlatformGotoTargetPresentationProvider : GotoTargetPresentationProvider {

    override fun getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
        if (!CandleSettings.getInstance(element.project).navigationPlatformPrefixesEnabled) {
            return null
        }

        val role = PlatformPresentationUtil.detectRole(element)
        if (PlatformPresentationUtil.configuredPrefix(element.project, role, PrefixStyle.NAVIGATION) == null) return null

        val base = PlatformPresentationUtil.baseTargetPresentation(element, differentNames) ?: return null
        return PlatformPresentationUtil.withPlatformPrefix(base, element.project, role)
    }
}
