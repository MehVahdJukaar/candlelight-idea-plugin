package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/**
 * Gradle source-set role for Candlelight / multi-loader projects.
 */
enum class ModuleRole(val platform: Platform?) {
    COMMON(null),
    FABRIC(Platform.FABRIC),
    NEOFORGE(Platform.NEOFORGE),
    FORGE(Platform.FORGE),
    UNKNOWN(null);

    val tabPrefix: String?
        get() = when (this) {
            FABRIC -> "[F]"
            NEOFORGE -> "[N]"
            FORGE -> "[G]"
            else -> null
        }

    companion object {
        fun fromPlatform(platform: Platform): ModuleRole = when (platform) {
            Platform.FABRIC -> FABRIC
            Platform.NEOFORGE -> NEOFORGE
            Platform.FORGE -> FORGE
        }
    }
}

object ModuleRoleDetector {

    fun detectRole(module: Module?): ModuleRole {
        if (module == null) return ModuleRole.UNKNOWN
        detectFromModuleName(module.name)?.let { return it }
        detectFromContentRoots(module)?.let { return it }
        return ModuleRole.UNKNOWN
    }

    fun detectRole(element: PsiElement): ModuleRole {
        element.module?.let { module ->
            detectRole(module).takeIf { it != ModuleRole.UNKNOWN }?.let { return it }
        }
        element.containingFile?.virtualFile?.let { file ->
            return detectRoleForFile(file, element.project, element.module)
        }
        return ModuleRole.UNKNOWN
    }

    /**
     * Resolves platform/common role for editor tabs, including library and dependency sources.
     */
    fun detectRoleForFile(file: VirtualFile, project: Project, contextModule: Module?): ModuleRole {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val inProjectSources = fileIndex.isInSourceContent(file)

        if (!inProjectSources) {
            detectFromPath(file.path)?.takeIf { it.isPlatformSource }?.let { return it }
            primaryTypePackage(project, file)?.let { pkg ->
                detectPlatformFromPackage(pkg)?.let { return ModuleRole.fromPlatform(it) }
            }
        }

        contextModule?.let { detectRole(it) }?.takeIf { it != ModuleRole.UNKNOWN }?.let { return it }

        detectFromPath(file.path)?.let { return it }
        primaryTypePackage(project, file)?.let { pkg ->
            detectPlatformFromPackage(pkg)?.let { return ModuleRole.fromPlatform(it) }
        }
        return ModuleRole.UNKNOWN
    }

    fun findModuleForPlatform(project: Project, platform: Platform): Module? =
        ModuleManager.getInstance(project).modules.firstOrNull { detectRole(it).platform == platform }

    fun isCommonModule(module: Module?): Boolean = detectRole(module) == ModuleRole.COMMON

    fun isCommonElement(element: PsiElement): Boolean {
        when (detectRole(element)) {
            ModuleRole.COMMON -> return true
            ModuleRole.FABRIC, ModuleRole.NEOFORGE, ModuleRole.FORGE -> return false
            ModuleRole.UNKNOWN -> {}
        }
        return isStructurallyCommon(element)
    }

    /** Fallback when Gradle module naming does not expose the common source set. */
    private fun isStructurallyCommon(element: PsiElement): Boolean {
        val clazz = when (element) {
            is PsiMethod -> element.containingClass
            is PsiClass -> element
            else -> null
        } ?: return false
        return !clazz.isPlatformImplClass() && clazz.methods.any { it.hasPlatformImplAnnotation }
    }

    private val ModuleRole.isPlatformSource: Boolean
        get() = platform != null

    private fun detectFromModuleName(moduleName: String): ModuleRole? {
        val name = moduleName.lowercase()
        if (matchesModuleSegment(name, "common")) return ModuleRole.COMMON
        if (matchesModuleSegment(name, "neoforge")) return ModuleRole.NEOFORGE
        if (matchesModuleSegment(name, "fabric")) return ModuleRole.FABRIC
        if (matchesModuleSegment(name, "forge")) return ModuleRole.FORGE
        return null
    }

    private fun matchesModuleSegment(moduleName: String, segment: String): Boolean =
        moduleName.contains(".$segment.") ||
            moduleName.endsWith(".$segment") ||
            moduleName.endsWith(".$segment.main") ||
            moduleName == segment ||
            moduleName.endsWith(":$segment")

    private fun detectFromContentRoots(module: Module): ModuleRole? {
        for (root in ModuleRootManager.getInstance(module).contentRoots) {
            detectFromPath(root.path)?.let { role ->
                if (role != ModuleRole.UNKNOWN) return role
            }
        }
        return null
    }

    fun detectFromPath(path: String): ModuleRole? {
        val normalized = normalizePath(path)
        if (containsPathSegment(normalized, "common")) return ModuleRole.COMMON
        if (containsPathSegment(normalized, "neoforge")) return ModuleRole.NEOFORGE
        if (containsPathSegment(normalized, "fabric")) return ModuleRole.FABRIC
        if (containsPathSegment(normalized, "forge")) return ModuleRole.FORGE
        return null
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/').lowercase()

    private fun containsPathSegment(path: String, segment: String): Boolean =
        path.contains("/$segment/") ||
            path.contains("/$segment-") ||
            path.contains("-$segment/") ||
            path.contains("-$segment-") ||
            path.contains("-$segment.") ||
            path.contains("_${segment}_") ||
            path.contains("_${segment}-") ||
            path.contains(".$segment.") ||
            path.endsWith("/$segment") ||
            path.endsWith("-$segment") ||
            path.endsWith("_$segment")

    fun detectPlatformFromPackage(qualifiedName: String): Platform? = when {
        qualifiedName.startsWith("net.fabricmc.") || qualifiedName == "net.fabricmc" -> Platform.FABRIC
        qualifiedName.startsWith("net.neoforged.") || qualifiedName == "net.neoforged" -> Platform.NEOFORGE
        qualifiedName.startsWith("net.minecraftforge.") || qualifiedName == "net.minecraftforge" -> Platform.FORGE
        else -> null
    }

    private fun primaryTypePackage(project: Project, file: VirtualFile): String? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val qualifiedName = when (psiFile) {
            is PsiJavaFile -> psiFile.classes.firstOrNull()?.qualifiedName
            is KtFile -> psiFile.declarations
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull()
                ?.fqName
                ?.asString()
            else -> null
        } ?: return null
        return qualifiedName.substringBeforeLast('.', missingDelimiterValue = qualifiedName)
    }
}
