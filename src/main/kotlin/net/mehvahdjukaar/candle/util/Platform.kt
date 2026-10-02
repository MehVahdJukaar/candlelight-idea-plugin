package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.PropertyKey

enum class Platform(
    val id: String,
    @param:PropertyKey(resourceBundle = BUNDLE) private val translationKey: String,
    val identifyingPackage: String,
    val fallbackPlatforms: List<Platform> = emptyList(),
) {
    FABRIC("fabric", "platform.fabric", "net.fabricmc"),
    FORGE("forge", "platform.forge", "net.minecraftforge.common"),
    NEOFORGE("neoforge", "platform.neoforge", "net.neoforged.neoforge.common")
    // QUILT(PlatformIds.QUILT, "platform.quilt", "org.quiltmc", listOf(FABRIC)),
    ;

    fun hasElement(element: PsiElement): Boolean {
        ModuleRoleDetector.detectRole(element).platform?.let { return it == this }
        element.containingFile?.virtualFile?.let { file ->
            val module = ModuleUtil.findModuleForPsiElement(element)
            ModuleRoleDetector.detectRoleForFile(file, element.project, module).platform?.let { return it == this }
        }
        if (element is PsiClass && element.isPlatformImplClass()) {
            val available = listAvailable(element.project)
            if (available.size == 1 && available.single() == this) return true
        }
        return false
    }

    fun isIn(project: Project): Boolean =
        JavaPsiFacade.getInstance(project).findPackage(identifyingPackage) != null

    override fun toString() = CandleBundle[translationKey]


    companion object {

        fun fromString(string: String): Platform? {
            return Platform.entries.find { string == it.id }
        }

//TODO: cache and make faster
        fun getPlatformImplImplementationName(clazz: PsiClass): String {
            val className = clazz.binaryName ?: error("Could not get binary name of $this")
            val parts = className.split('.')
            val head = parts.dropLast(1).joinToString(separator = ".")
            val tail = parts.last().replace("$", "")
            val platPackageStr = platSubPackageName();
            return "$head.${platPackageStr}.${tail}Impl"
        }

        fun platSubPackageName(): String {
            return "platform"
        }

        fun listAvailable(project: Project): List<Platform> {
            return Platform.entries.filter { it.isIn(project) }
        }

        fun matchesPlatImplName(name: String, pkg: String): Boolean {
            return name.endsWith("Impl") && Platform.entries.any { pkg.endsWith(".${platSubPackageName()}") }

        }
    }

    fun findModuleForPlatform(project: Project): Module? {
        return ModuleManager.getInstance(project).modules.find { module ->
            val name: String = module.name.lowercase()
            // the dot is so ".neoforge.main" doesnt match forge
            name.contains(".${this.id}.main")
        }
    }
}
