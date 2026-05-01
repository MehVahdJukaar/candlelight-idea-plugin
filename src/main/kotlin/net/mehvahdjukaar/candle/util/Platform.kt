package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.debugger.Scope
import org.jetbrains.kotlin.idea.base.util.module

/**
 * @property id a unique string ID for this platform from [PlatformIds]
 * @property translationKey a resource bundle key for the display name of this platform
 * @property fallbackPlatforms fallback platforms used for finding `@PlatformImpl` implementation methods
 * @property identifyingPackage a package that is only present when a module is on this platform
 */
enum class Platform(
    val id: String,
    @param:PropertyKey(resourceBundle = BUNDLE) private val translationKey: String,
    val identifyingPackage: String,
    val fallbackPlatforms: List<Platform> = emptyList()
) {
    FABRIC("fabric", "platform.fabric", "net.fabricmc"),
    FORGE("forge", "platform.forge", "net.minecraftforge.common"),
    NEOFORGE("neoforge", "platform.neoforge", "net.neoforged.neoforge.common")
    // QUILT(PlatformIds.QUILT, "platform.quilt", "org.quiltmc", listOf(FABRIC)),
    ;

    fun hasElement(clazz: PsiElement): Boolean {
        val module = if (clazz is PsiClass) clazz.scope.module else clazz.module
        return module?.name?.contains(".${this.id}.", ignoreCase = true) ?: false
    }

    fun isIn(project: Project): Boolean =
        JavaPsiFacade.getInstance(project).findPackage(identifyingPackage) != null

    override fun toString() = CandleBundle[translationKey]

    companion object {

        /**
         * Gets the name of the [clazz]'s implementation version for this platform.
         *
         * Example: `com.example.Example` -> `com.example.forge.ExampleImpl`
         */

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
        return    name.endsWith("Impl") && Platform.entries.any { pkg.endsWith(".${platSubPackageName()}") }

        }
    }

    fun findModuleForPlatform(project: Project): Module? {
        return ModuleManager.getInstance(project).modules.find { module ->
            val name: String = module.name.lowercase()
            // Matches: ":fabric", "myproject.fabric.main", "fabric-api", etc.
             name.contains(".${this.id}.main") //name.startsWith("ideaproject") &&
        }
    }

    /*
    fun findScopeForPlatform(project: Project): GlobalSearchScope? {
        var modules = ModuleManager.getInstance(project).modules.find { module ->
            val name: String = module.name.lowercase()
            // Matches: ":fabric", "myproject.fabric.main", "fabric-api", etc.
            name.contains(".${this.id}") //name.startsWith("ideaproject") &&
        }
        return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope()

    } */

}
