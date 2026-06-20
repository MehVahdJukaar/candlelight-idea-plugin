package net.mehvahdjukaar.candle.presentation

import com.intellij.codeInsight.navigation.JavaGotoTargetPresentationProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import net.mehvahdjukaar.candle.settings.CandleSettings
import net.mehvahdjukaar.candle.util.ModuleRole
import net.mehvahdjukaar.candle.util.ModuleRoleDetector
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.KotlinGotoTargetPresentationProvider

enum class PrefixStyle {
    TAB,
    NAVIGATION,
}

object PlatformPresentationUtil {

    private val javaPresentationProvider = JavaGotoTargetPresentationProvider()
    private val kotlinPresentationProvider = KotlinGotoTargetPresentationProvider()

    fun detectRole(element: PsiElement): ModuleRole = ModuleRoleDetector.detectRole(element)

    fun configuredPrefix(project: Project, role: ModuleRole, style: PrefixStyle): String? {
        if (!CandleSettings.getInstance(project).isPrefixEnabled(role)) return null
        return when (style) {
            PrefixStyle.TAB -> role.tabPrefix
            PrefixStyle.NAVIGATION -> role.navigationPrefix
        }
    }

    fun prefixPresentableText(text: String, prefix: String): String {
        if (text.startsWith("$prefix ")) return text
        return "$prefix $text"
    }

    fun prefixPresentableText(text: String, project: Project, role: ModuleRole, style: PrefixStyle): String? {
        val prefix = configuredPrefix(project, role, style) ?: return null
        return prefixPresentableText(text, prefix)
    }

    fun baseTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
        javaPresentationProvider.getTargetPresentation(element, differentNames)?.let { return it }
        kotlinPresentationProvider.getTargetPresentation(element, differentNames)?.let { return it }

        if (element is NavigationItem) {
            val presentation = element.presentation
            if (presentation != null) {
                return targetPresentationFromItemPresentation(presentation)
            }
        }

        if (element is PsiNamedElement) {
            val name = element.name ?: return null
            return TargetPresentation.builder(name).presentation()
        }

        return null
    }

    fun withPlatformPrefix(base: TargetPresentation, project: Project, role: ModuleRole): TargetPresentation? {
        val prefixed = prefixPresentableText(base.presentableText, project, role, PrefixStyle.NAVIGATION) ?: return null
        return TargetPresentation.builder(base).presentableText(prefixed).presentation()
    }

    private fun targetPresentationFromItemPresentation(presentation: ItemPresentation): TargetPresentation {
        val builder = TargetPresentation.builder(presentation.presentableText ?: "")
        presentation.locationString?.let { builder.locationText(it) }
        presentation.getIcon(false)?.let { builder.icon(it) }
        return builder.presentation()
    }
}
