package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.PropertyKey

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
    val fallbackPlatforms: List<Platform> = emptyList(),
    private val markerPackages: List<String> = listOf(identifyingPackage),
) {
    FABRIC("fabric", "platform.fabric", "net.fabricmc", markerPackages = listOf("net.fabricmc", "net.fabricmc.loader")),
    FORGE("forge", "platform.forge", "net.minecraftforge.common", markerPackages = listOf("net.minecraftforge.common", "net.minecraftforge")),
    NEOFORGE("neoforge", "platform.neoforge", "net.neoforged.neoforge.common", markerPackages = listOf("net.neoforged.neoforge.common", "net.neoforged.neoforge"))
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

    fun isIn(project: Project): Boolean {
        val facade = JavaPsiFacade.getInstance(project)
        return markerPackages.any { facade.findPackage(it) != null }
    }

    override fun toString() = CandleBundle[translationKey]


    companion object {

        fun fromString(string: String): Platform? {
            return Platform.entries.find { string == it.id }
        }

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
            return name.endsWith("Impl") && Platform.entries.any { pkg.endsWith(".${platSubPackageName()}") }

        }
    }

    fun findModuleForPlatform(project: Project): Module? =
        ModuleRoleDetector.findModuleForPlatform(project, this)
}
