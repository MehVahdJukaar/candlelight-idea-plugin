package net.mehvahdjukaar.candle.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import net.mehvahdjukaar.candle.presentation.PlatformPresentationUtil
import net.mehvahdjukaar.candle.presentation.PrefixStyle
import net.mehvahdjukaar.candle.settings.CandleSettings
import net.mehvahdjukaar.candle.util.ModuleRole
import net.mehvahdjukaar.candle.util.ModuleRoleDetector
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class PlatformProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        if (!CandleSettings.getInstance(project).projectViewPlatformPrefixesEnabled) return

        val file = node.virtualFile
        if (file != null && declaresType(project, file)) return

        val role = detectRole(node, project) ?: return
        val presentableText = data.presentableText ?: return
        val prefixed = PlatformPresentationUtil.prefixPresentableText(presentableText, project, role, PrefixStyle.NAVIGATION) ?: return
        data.presentableText = prefixed
    }

    private fun detectRole(node: ProjectViewNode<*>, project: Project): ModuleRole? {
        val file = node.virtualFile
        val module = resolveModule(node, project, file)

        if (file != null && declaresType(project, file)) {
            return ModuleRoleDetector.detectRoleForFile(file, project, module)
        }

        if (file != null) {
            ModuleRoleDetector.detectFromPath(file.path)?.let { role ->
                if (role != ModuleRole.UNKNOWN) return role
            }
        }

        val moduleRole = ModuleRoleDetector.detectRole(module)
        return moduleRole.takeIf { it != ModuleRole.UNKNOWN }
    }

    private fun resolveModule(node: ProjectViewNode<*>, project: Project, file: VirtualFile?): Module? {
        val value = node.value
        if (value is Module) return value
        if (file != null) return ModuleUtilCore.findModuleForFile(file, project)
        return null
    }

    private fun declaresType(project: Project, file: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
        return when (psiFile) {
            is PsiJavaFile -> psiFile.classes.isNotEmpty()
            is KtFile -> psiFile.declarations.any { it is KtClassOrObject }
            else -> false
        }
    }
}
