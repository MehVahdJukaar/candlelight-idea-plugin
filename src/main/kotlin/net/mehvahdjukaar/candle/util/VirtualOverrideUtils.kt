package net.mehvahdjukaar.candle.util

import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiSuperMethodUtil
import com.intellij.psi.util.TypeConversionUtil
import net.mehvahdjukaar.candle.util.Annotations.splitValueStrings
import org.jetbrains.kotlin.idea.base.util.module

// ---------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------

class PlatformVirtualMethod(
    val method: PsiMethod,
    val platform: Platform,
    val substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY
) {
    val name: String get() = method.name
    val parametersCount: Int get() = method.parameterList.parametersCount

    fun matches(otherMethod: PsiMethod): Boolean {
        if (otherMethod.name != name) return false
        val otherParams = otherMethod.parameterList.parameters
        if (otherParams.size != parametersCount) return false

        val thisParams = method.parameterList.parameters
        for (i in 0 until parametersCount) {
            val thisType = substitutor.substitute(thisParams[i].type)
            val otherType = otherParams[i].type

            if (!TypeConversionUtil.erasure(thisType).isAssignableFrom(TypeConversionUtil.erasure(otherType))) return false
        }

        val thisReturnType = substitutor.substitute(method.returnType ?: PsiTypes.voidType())
        val otherReturnType = otherMethod.returnType ?: PsiTypes.voidType()

        return TypeConversionUtil.erasure(thisReturnType).isAssignableFrom(TypeConversionUtil.erasure(otherReturnType))
    }
}

/**
 * Returns all platform methods that this common method virtually overrides.
 * Only returns methods that are **platform‑specific** (exist in some but not all platforms).
 */
fun PsiMethod.findPlatformVirtualOverrides(): Set<PsiMethod> {
    val containingClass = containingClass ?: return emptySet()
    val index = containingClass.getVirtualMethodIndex()
    val methodsForName = index[this.name] ?: return emptySet()

    val matchingMethods = mutableListOf<PlatformVirtualMethod>()
    for (platformMap in methodsForName.values) {
        for (pvm in platformMap) {
            if (pvm.matches(this)) {
                matchingMethods.add(pvm)
            }
        }
    }

    val platforms = matchingMethods.map { it.platform }.toSet()
    val availablePlatforms = Platform.listAvailable(project)

    // Return methods only if they are platform‑specific
    return if (platforms.isNotEmpty() && platforms.size < availablePlatforms.size) {
        matchingMethods.map { it.method }.toSet()
    } else {
        emptySet()
    }
}

/**
 * Returns all platform‑specific overridable methods for this class.
 * A method is included only if it exists in **exactly one** available platform.
 * Works only for classes inside the “common” module.
 */
fun PsiClass.findAllPlatformVirtualOverridableMethods(): List<PlatformVirtualMethod> {
    val index = getVirtualMethodIndex()
    val availablePlatforms = Platform.listAvailable(project)

    // Group platform methods by their "identity" (name + parameters count for now, as a heuristic)
    val grouped = mutableMapOf<String, MutableList<PlatformVirtualMethod>>()
    for (methodsForName in index.values) {
        for (platformMap in methodsForName.values) {
            for (pvm in platformMap) {
                val key = "${pvm.name}${pvm.parametersCount}"
                grouped.getOrPut(key) { mutableListOf() }.add(pvm)
            }
        }
    }

    return grouped.values
        .filter { methods ->
            val platforms = methods.map { it.platform }.toSet()
            platforms.size == 1 && platforms.size < availablePlatforms.size
        }
        .map { it.first() }
        .toList()
}

/**
 * Checks if this method is a valid virtual override for the given platform.
 * Returns true if the method exists in the platform's supertype hierarchy.
 */
fun PsiMethod.isValidVirtualOverrideForPlatform(plat: Platform): Boolean {
    val containingClass = containingClass ?: return false
    val index = containingClass.getVirtualMethodIndex()
    val platformsForName = index[this.name] ?: return false
    val platformMethods = platformsForName[plat] ?: return false

    return platformMethods.any { it.matches(this) }
}

// ---------------------------------------------------------------------
// Index building (no caching)
// ---------------------------------------------------------------------

private fun isCommon(clazz: PsiElement): Boolean {
    return clazz.module?.name?.contains(".common.", ignoreCase = true) ?: false
}

/**
 * Builds a fresh map of method signature -> list of PlatformVirtualMethod for this class.
 * Walks all available platform module hierarchies and collects overridable methods.
 * Only computes for classes inside the “common” module.
 */
private fun PsiClass.getVirtualMethodIndex(): Map<String, Map<Platform, List<PlatformVirtualMethod>>> {
    if (!isCommon(this)) return emptyMap()

    val dependencies = mutableSetOf<PsiElement>()   // no longer used for caching, but for collection
    val index = mutableMapOf<String, MutableMap<Platform, MutableList<PlatformVirtualMethod>>>()
    val project = project
    val availablePlatforms = Platform.listAvailable(project)
    if (availablePlatforms.size <= 1) return emptyMap();

    // Supertypes of the original class (excluding java.lang.Object and the class itself)
    val commonSuperTypes = collectAllSuperTypes(this, dependencies)
        .filter { it.qualifiedName != CommonClassNames.JAVA_LANG_OBJECT && it != this }

    // Interfaces added via annotations
    val implicitInterfaces = collectOptionalInterfaces(this)

    val allSuperTypesStrings = commonSuperTypes
        .mapNotNull { it.qualifiedName }
        .toMutableList()
    allSuperTypesStrings += implicitInterfaces
    val facade = JavaPsiFacade.getInstance(project)

    // Cache for already collected hierarchies inside this call (simple HashMap, no IDE caching)

    for (platform in availablePlatforms) {
        val platformHierarchyCache = HashMap<PsiClass, Set<PsiClass>>()
        val platformModule = platform.findModuleForPlatform(project) ?: continue
        val scope = moduleWithDependenciesAndLibrariesScope(platformModule, false);
        for (qualifiedName in allSuperTypesStrings) {
            val platformSuperType = facade.findClass(qualifiedName, scope) ?: continue

            val substitutor = TypeConversionUtil.getClassSubstitutor(platformSuperType, this, PsiSubstitutor.EMPTY)
                ?: PsiSubstitutor.EMPTY

            // Expand the hierarchy of that class in the platform module
            val hierarchy = platformHierarchyCache.getOrPut(platformSuperType) {
                collectAllSuperTypes(platformSuperType, dependencies)
            }

            for (platformClass in hierarchy) {
                for (method in platformClass.methods) {
                    if (!isOverridable(method)) continue

                    val methodsForName = index.getOrPut(method.name) { mutableMapOf() }
                    val platformMethods = methodsForName.getOrPut(platform) { mutableListOf() }
                    platformMethods.add(PlatformVirtualMethod(method, platform, substitutor))
                }
            }
        }
    }

    return index
}

// ---------------------------------------------------------------------
// Overridable check
// ---------------------------------------------------------------------

private fun isOverridable(method: PsiMethod): Boolean {
    return !(method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty(PsiModifier.PRIVATE))
}

// ---------------------------------------------------------------------
// Hierarchy Utilities
// ---------------------------------------------------------------------

private fun collectAllSuperTypes(psiClass: PsiClass, dependencies: MutableSet<PsiElement>): Set<PsiClass> {
    val result = mutableSetOf<PsiClass>()

    val superClasses = mutableSetOf<PsiClass>()
    InheritanceUtil.getSuperClasses(psiClass, superClasses, true)
    result.addAll(superClasses)
    dependencies.addAll(superClasses)

    for (ifaceElement in psiClass.interfaces) {
        val iface = when (ifaceElement) {
            is PsiClass -> ifaceElement
            else -> continue
        }
        result.add(iface)
        dependencies.add(iface)
        result.addAll(collectAllSuperTypes(iface, dependencies))
    }

    result.add(psiClass)
    return result
}

private fun collectOptionalInterfaces(psiClass: PsiClass): List<String> {
    val allImplicitAnnotations = mutableListOf<String>()
    for (ann in AnnotationType.OPTIONAL_INTERFACE) {
        val optionalAnnotation = psiClass.getAnnotation(ann)
        if (optionalAnnotation != null) {
            allImplicitAnnotations.addAll(optionalAnnotation.splitValueStrings("value"))
        }
    }
    return allImplicitAnnotations
}
