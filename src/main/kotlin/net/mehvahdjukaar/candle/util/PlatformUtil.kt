package net.mehvahdjukaar.candle.util

import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dk.brics.automaton.SpecialOperations.getStrings
import net.mehvahdjukaar.candle.inspection.ExpectedImplSignature
import net.mehvahdjukaar.candle.util.Annotations.splitValueStrings

fun PsiModifierListOwner.hasAnnotation(type: AnnotationType): Boolean =
    type.any { hasAnnotation(it) }

/**
 * True if this method is a common, untransformed `@PlatformImpl` method.
 */
val PsiMethod.hasPlatformImplAnnotation: Boolean
    get() = hasAnnotation(AnnotationType.PLATFORM_IMPLEMENTATION)

val PsiMethod.isVirtualOverrideAnnotation: Boolean
    get() = hasAnnotation(AnnotationType.VIRTUAL_OVERRIDE);

/**
 * Finds the first annotation of the [type] on this method.
 */
fun PsiMethod.findAnnotation(type: AnnotationType): PsiAnnotation? =
    annotations.firstOrNull {
        type.any { name -> it.hasQualifiedName(name) }
    }

// TODO: Cache these somehow? Both commonMethods and platformMethods might be really slow and could benefit from caching.

/**
 * The common declarations corresponding to this platform method.
 */
val PsiMethod.commonMethods: Set<PsiMethod>
    get() {
        val clazz = containingClass ?: return emptySet()
        val name = clazz.binaryName ?: return emptySet()
        val pkg = name.substringBeforeLast('.')

        val nameMatches = name.endsWith("Impl") && Platform.entries.any { pkg.endsWith(".${Platform.platSubPackageName()}") }
        if (!nameMatches) return emptySet()

        val commonPkg = pkg.substringBeforeLast('.')
        val commonClassName = name.substringAfterLast('.').removeSuffix("Impl")
        val baseClass = "$commonPkg.$commonClassName"

        return JavaPsiFacade.getInstance(project).findPackage(commonPkg)
            ?.getClasses(getScopeFor(this))
            ?.asSequence()
            ?.flatMap { it.asSequenceWithInnerClasses() }
            ?.filter { it.binaryName?.replace("$", "") == baseClass }
            ?.flatMap { commonClass ->
                // Find the common @PlatformImpl method that corresponds to this impl method
                commonClass.methods.asSequence().filter { commonMethod ->
                    commonMethod.hasPlatformImplAnnotation &&
                        ExpectedImplSignature.fromExpectMethod(commonMethod).matchesImplMethod(this)
                }
            }
            ?.toSet()
            ?: emptySet()
    }

/**
 * The platform implementations of this common method.
 */
val PsiMethod.platformMethodsByPlatform: Map<Platform, Set<PsiMethod>>
    get() {
        if (!hasPlatformImplAnnotation) return emptyMap()
        val clazz = containingClass ?: return emptyMap()
        val expectedSignature = ExpectedImplSignature.fromExpectMethod(this)

        return Platform.entries.associateWith { platform ->
            val implementationClassName = Platform.getImplementationName(clazz)

            JavaPsiFacade.getInstance(project)
                .findClasses(implementationClassName, getScopeFor(this))
                .asSequence()
                .flatMap { implClass ->
                    implClass.methods.asSequence().filter { implMethod ->
                        expectedSignature.matchesImplMethod(implMethod)
                    }
                }
                .toSet()
        }
    }



/**
 * The platform implementations of this common method.
 */
val PsiMethod.platformMethods: Set<PsiMethod>
    get() = platformMethodsByPlatform.flatMap { (_, methods) -> methods }.toSet()


/**
 * The binary name of this class in dot-dollar format (eg. `a.b.C$D`)
 */
val PsiClass.binaryName: String?
    get() =
        if (containingClass != null) {
            containingClass!!.binaryName + "$" + name
        } else {
            qualifiedName
        }

/**
 * Gets a sequence of this class and all its inner classes, recursed infinitely.
 */
fun PsiClass.asSequenceWithInnerClasses(): Sequence<PsiClass> =
    sequence {
        yield(this@asSequenceWithInnerClasses)
        yieldAll(innerClasses.asSequence().flatMap { it.asSequenceWithInnerClasses() })
    }

/**
 * Gets a value from this platform map, falling back to the [Platform.fallbackPlatforms] if not specified here.
 */
fun <V : Any> Map<Platform, V>.getWithPlatformFallback(platform: Platform): V? =
    this[platform] ?: platform.fallbackPlatforms.asSequence().mapNotNull { getWithPlatformFallback(it) }.firstOrNull()



/**
 * Gets the searching scope for searching for classes related to the [element].
 * If the element's corresponding module is not null (= an element in this project),
 * uses the project scope. Otherwise uses the all scope.
 */
private fun getScopeFor(element: PsiElement): GlobalSearchScope =
    if (ModuleUtil.findModuleForPsiElement(element) != null) {
        GlobalSearchScope.projectScope(element.project)
    } else {
        GlobalSearchScope.allScope(element.project)
    }


fun getDefaultReturnValue(returnType: PsiType?): String {
    return when (returnType) {
        PsiType.BOOLEAN -> "false"
        PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT -> "0"
        PsiType.LONG -> "0L"
        PsiType.FLOAT -> "0.0f"
        PsiType.DOUBLE -> "0.0"
        else -> "null"
    }
}

fun PsiMethod.signatureKey(): String =
    name + "(" + parameterList.parameters.joinToString(",") { it.type.canonicalText } + ")"
