package net.mehvahdjukaar.candle.title

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ModuleAwareTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val base = file.name
        val suffix = when {
            module.name.contains("fabric") -> "[F]"
            module.name.contains("neoforge") -> "[N]"
            module.name.contains("forge") -> "[N]"
            else -> null
        }
        if (suffix == null) return null
        return "$suffix $base"
    }
}

