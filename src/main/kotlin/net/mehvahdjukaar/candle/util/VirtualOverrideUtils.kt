// VirtualOverrideUtils.kt
package net.mehvahdjukaar.candle.util

import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.InheritanceUtil
import net.mehvahdjukaar.candle.util.Annotations.splitValueStrings

// ---------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------

data class PlatformVirtualMethod(
    val method: PsiMethod,
    val platform: Platform
)

/**
 * Returns all platform methods that this common method virtually overrides.
 * Only returns methods that are **platform‑specific** (exist in some but not all platforms).
 */
fun PsiMethod.findPlatformVirtualOverrides(): Set<PsiMethod> {
    val containingClass = containingClass ?: return emptySet()
    val signature = signatureKey()
    val index = containingClass.getVirtualMethodIndex()
    val methodsForSignature = index[signature] ?: return emptySet()

    val platforms = methodsForSignature.map { it.platform }.toSet()
    val availablePlatforms = Platform.availables(project)

    // Return methods only if they are platform‑specific
    return if (platforms.isNotEmpty() && platforms.size < availablePlatforms.size) {
        methodsForSignature.map { it.method }.toSet()
    } else {
        emptySet()
    }
}

/**
 * Returns all platform‑specific overridable methods for this class.
 * A method is included only if it exists in **exactly one** available platform.
 */
fun PsiClass.findAllPlatformVirtualOverridableMethods(): List<PlatformVirtualMethod> {
    val index = getVirtualMethodIndex()
    val availablePlatforms = Platform.availables(project)
    return index.values
        .filter { methods ->
            val platforms = methods.map { it.platform }.toSet()
            platforms.size == 1 && platforms.size < availablePlatforms.size
        }
        .map { it.first() }
        .toList()
}

// ---------------------------------------------------------------------
// Core Index
// ---------------------------------------------------------------------

/**
 * Returns a cached map of method signature -> list of PlatformVirtualMethod for this class.
 * The list contains entries for every overridable method found in the platform hierarchies.
 */
private fun PsiClass.getVirtualMethodIndex(): Map<String, List<PlatformVirtualMethod>> {
    return CachedValuesManager.getCachedValue(this) {
        val dependencies = mutableSetOf<PsiElement>(this)
        val index = mutableMapOf<String, MutableList<PlatformVirtualMethod>>()

        val availablePlatforms = Platform.availables(project)

        // All supertypes of this common class, excluding itself and Object
        val commonSuperTypes = collectAllSuperTypes(this, dependencies)
            .filter { it.qualifiedName != CommonClassNames.JAVA_LANG_OBJECT && it != this }

        // Handle @OptionalInterface annotations (shallow – only on this class)
        val implicitInterfaces = collectOptionalInterfaces(this)

        val allSuperTypesStrings = commonSuperTypes
            .mapNotNull { it.qualifiedName }
            .toMutableList()
        allSuperTypesStrings += implicitInterfaces


        for (platform in availablePlatforms) {
            val platformModule = platform.findModuleForPlatform(project) ?: continue


            for (qualifiedName in allSuperTypesStrings) {
                val platformSuperType = JavaPsiFacade.getInstance(project)
                    .findClass(qualifiedName, GlobalSearchScope.moduleRuntimeScope(platformModule, false))
                    ?: continue

                dependencies.add(platformSuperType)
                val platformHierarchy = collectAllSuperTypes(platformSuperType, dependencies)

                for (platformClass in platformHierarchy) {
                    for (method in platformClass.methods) {
                        if (!isOverridable(method)) continue
                        val key = method.signatureKey()
                        index.getOrPut(key) { mutableListOf() }
                            .add(PlatformVirtualMethod(method, platform))
                        dependencies.add(method)
                    }
                }
            }
        }

        CachedValueProvider.Result(index, *dependencies.toTypedArray())
    }
}

/**
 * Checks if this method is a valid virtual override for the given platform.
 * Returns true if the method exists in the platform's supertype hierarchy.
 */
fun PsiMethod.isValidVirtualOverrideForPlatform(platformId: String): Boolean {
    val containingClass = containingClass ?: return false
    val signature = signatureKey()
    val index = containingClass.getVirtualMethodIndex()
    val methodsForSignature = index[signature] ?: return false

    return methodsForSignature.any { it.platform.id.equals(platformId, ignoreCase = true) }
}


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
    val allImplicitAnnotations = mutableListOf<String>();
    for (ann in AnnotationType.OPTIONAL_INTERFACE) {
        val optionalAnnotation = psiClass.getAnnotation(ann)
        if (optionalAnnotation != null) {
            allImplicitAnnotations.addAll(optionalAnnotation.splitValueStrings("value"));
        }
    }
    return allImplicitAnnotations
}
