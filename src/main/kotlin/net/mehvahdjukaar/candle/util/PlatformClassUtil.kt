package net.mehvahdjukaar.candle.util

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope

fun PsiClass.isPlatformImplClass(): Boolean {
    val binaryName = binaryName ?: return false
    val pkg = binaryName.substringBeforeLast('.')
    val simpleName = binaryName.substringAfterLast('.').replace("$", "")
    return Platform.matchesPlatImplName(simpleName, pkg)
}

fun PsiClass.commonClassFromImpl(): PsiClass? {
    if (!isPlatformImplClass()) return null
    val binaryName = binaryName ?: return null
    val pkg = binaryName.substringBeforeLast('.')
    val platformSegment = ".${Platform.platSubPackageName()}"
    if (!pkg.endsWith(platformSegment)) return null

    val commonPkg = pkg.removeSuffix(platformSegment)
    val commonSimpleName = binaryName.substringAfterLast('.').replace("$", "").removeSuffix("Impl")
    return JavaPsiFacade.getInstance(project)
        .findClass("$commonPkg.$commonSimpleName", scopeForPlatformRelations(this))
}

fun PsiClass.platformImplClasses(): List<PsiClass> {
    if (isPlatformImplClass()) {
        return commonClassFromImpl()?.platformImplClasses().orEmpty()
    }
    val implClassName = Platform.getPlatformImplImplementationName(this)
    return JavaPsiFacade.getInstance(project)
        .findClasses(implClassName, scopeForPlatformRelations(this))
        .toList()
}

fun PsiClass.participatesInPlatformImplGraph(): Boolean =
    isPlatformImplClass() ||
        methods.any { it.hasPlatformImplAnnotation } ||
        platformImplClasses().isNotEmpty()

fun implSimpleNameForCommon(commonSimpleName: String): String = "${commonSimpleName}Impl"

fun commonSimpleNameFromImpl(implSimpleName: String): String = implSimpleName.removeSuffix("Impl")

internal fun scopeForPlatformRelations(element: com.intellij.psi.PsiElement): GlobalSearchScope =
    GlobalSearchScope.projectScope(element.project)
