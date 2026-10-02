package net.mehvahdjukaar.candle.util

import com.intellij.openapi.project.DumbService
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesAndLibrariesScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import net.mehvahdjukaar.candle.util.Annotations.splitValueStrings

class PlatformVirtualMethod(
    val method: PsiMethod,
    val platform: Platform,
    val substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY
) {
    val name: String get() = method.name
    val parametersCount: Int get() = method.parameterList.parametersCount
    val signatureKey: String
        get() = method.signatureKey(substitutor)

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

    return if (platforms.isNotEmpty() && platforms.size < availablePlatforms.size) {
        matchingMethods.map { it.method }.toSet()
    } else {
        emptySet()
    }
}

fun PsiClass.findAllPlatformVirtualOverridableMethods(): List<PlatformVirtualMethod> {
    val index = getVirtualMethodIndex()
    val availablePlatforms = Platform.listAvailable(project)

    val grouped = mutableMapOf<String, MutableList<PlatformVirtualMethod>>()
    for (methodsForName in index.values) {
        for (platformMap in methodsForName.values) {
            for (pvm in platformMap) {
                grouped.getOrPut(pvm.signatureKey) { mutableListOf() }.add(pvm)
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

fun PsiMethod.isValidVirtualOverrideForPlatform(plat: Platform): Boolean {
    val containingClass = containingClass ?: return false
    val index = containingClass.getVirtualMethodIndex()
    val platformsForName = index[this.name] ?: return false
    val platformMethods = platformsForName[plat] ?: return false

    return platformMethods.any { it.matches(this) }
}

private fun PsiClass.getVirtualMethodIndex(): Map<String, Map<Platform, List<PlatformVirtualMethod>>> {
    //resolution is incomplete while indexing. the false positives make gutter markers flash on startup and latch the gutter width too wide
    if (DumbService.isDumb(project)) return emptyMap()
    if (!ModuleRoleDetector.isCommonElement(this)) return emptyMap()
    return buildVirtualMethodIndex()
}

private fun PsiClass.buildVirtualMethodIndex(): Map<String, Map<Platform, List<PlatformVirtualMethod>>> {

    val dependencies = mutableSetOf<PsiElement>()
    val index = mutableMapOf<String, MutableMap<Platform, MutableList<PlatformVirtualMethod>>>()
    val project = project
    val availablePlatforms = Platform.listAvailable(project)
    if (availablePlatforms.size <= 1) return emptyMap();

    val commonSuperTypes = collectAllSuperTypes(this, dependencies)
        .filter { it.qualifiedName != CommonClassNames.JAVA_LANG_OBJECT && it != this }

    val implicitInterfaces = collectOptionalInterfaces(this)

    val allSuperTypesStrings = commonSuperTypes
        .mapNotNull { it.qualifiedName }
        .toMutableList()
    allSuperTypesStrings += implicitInterfaces
    val facade = JavaPsiFacade.getInstance(project)

    for (platform in availablePlatforms) {
        val platformHierarchyCache = HashMap<PsiClass, Set<PsiClass>>()
        val platformModule = platform.findModuleForPlatform(project)
        val scope = platformModule?.let { moduleWithDependenciesAndLibrariesScope(it, false) }
            ?: GlobalSearchScope.allScope(project)
        for (qualifiedName in allSuperTypesStrings) {
            // architectury can have both the vanilla and the patched copy of a class in scope. findClass would only get one
            for (platformSuperType in facade.findClasses(qualifiedName, scope)) {
                val substitutor = TypeConversionUtil.getClassSubstitutor(platformSuperType, this, PsiSubstitutor.EMPTY)
                    ?: PsiSubstitutor.EMPTY

                val hierarchy = platformHierarchyCache.getOrPut(platformSuperType) {
                    collectAllSuperTypes(platformSuperType, dependencies)
                }

                for (platformClass in hierarchy) {
                    val classPlatform = platformClass.qualifiedName
                        ?.let { ModuleRoleDetector.detectPlatformFromPackage(it) }
                    val belongsToOtherPlatform = classPlatform != null && classPlatform != platform
                    if (belongsToOtherPlatform) continue

                    for (method in platformClass.methods) {
                        if (!isOverridable(method)) continue

                        val methodsForName = index.getOrPut(method.name) { mutableMapOf() }
                        val platformMethods = methodsForName.getOrPut(platform) { mutableListOf() }
                        platformMethods.add(PlatformVirtualMethod(method, platform, substitutor))
                    }
                }
            }
        }
    }

    return index
}

private fun isOverridable(method: PsiMethod): Boolean {
    return !(method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty(PsiModifier.PRIVATE))
}

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
    val interfaces = mutableListOf<String>()
    for (ann in AnnotationType.OPTIONAL_INTERFACE) {
        val optionalAnnotation = psiClass.getAnnotation(ann)
        if (optionalAnnotation != null) {
            interfaces.addAll(optionalAnnotation.splitValueStrings("value"))
        }
    }
    return interfaces
}
