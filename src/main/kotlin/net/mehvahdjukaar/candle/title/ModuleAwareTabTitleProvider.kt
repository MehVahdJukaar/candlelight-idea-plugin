package net.mehvahdjukaar.candle.title

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import net.mehvahdjukaar.candle.util.ModuleRoleDetector
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class ModuleAwareTabTitleProvider : EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (!declaresType(project, file)) return null

        val module = ModuleUtilCore.findModuleForFile(file, project)
        val prefix = ModuleRoleDetector.detectRoleForFile(file, project, module).tabPrefix ?: return null
        return "$prefix ${file.name}"
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
