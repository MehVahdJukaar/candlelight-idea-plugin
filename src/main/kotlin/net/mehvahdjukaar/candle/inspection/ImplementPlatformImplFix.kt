package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.Platform
import net.mehvahdjukaar.candle.util.getDefaultReturnValue
import org.jetbrains.jps.model.java.JavaSourceRootType

class ImplementPlatformImplFix(private val platforms: List<Platform>) : LocalQuickFix {
    override fun getFamilyName(): String {
        val platform = platforms.singleOrNull()
        return if (platform != null)
            CandleBundle["inspection.implementExpectPlatform.single", platform]
        else
            CandleBundle["inspection.implementExpectPlatform"]
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = findMethod(descriptor.psiElement) ?: error("Could not find method from ${descriptor.psiElement}")
        val facade = JavaPsiFacade.getInstance(project)
        val missingPackages = HashMap<Platform, String>()

        val implClassName = Platform.getPlatformImplImplementationName(method.containingClass!!)
        val candidateClasses = facade.findClasses(implClassName, GlobalSearchScope.projectScope(project))

        val expectedSignature = ExpectedImplSignature.fromExpectMethod(method)

        for (platform in platforms) {
            val implClass = candidateClasses.firstOrNull { platform.hasElement(it) }
                ?: run {
                    val packageName = implClassName.substringBeforeLast('.')
                    val pkg = facade.findPackage(packageName)

                    if (pkg == null) {
                        missingPackages[platform] = packageName
                        return@run null
                    }

                    val platformDirs = pkg.directories.filter {
                        it.virtualFile.path.contains("/${platform.id}/", ignoreCase = true)
                    }

                    val dir = if (platformDirs.isNotEmpty()) {
                        findJavaSourceDirectory(platformDirs.toTypedArray())
                    } else {
                        null
                    }

                    if (dir != null) {
                        val shortName = implClassName.substringAfterLast('.')
                        JavaDirectoryService.getInstance().getClasses(dir)
                            .firstOrNull { it.name == shortName }
                            ?: JavaDirectoryService.getInstance().createClass(dir, shortName)
                    } else {
                        missingPackages[platform] = packageName
                        null
                    }
                } ?: continue

            // Pass the original method so we can generate a clean parameter name for the instance parameter
            addMethod(project, expectedSignature, implClass, method)
        }

        if (missingPackages.isNotEmpty()) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                missingPackages.forEach { (platform, packageName) ->
                    var directory: PsiDirectory? = null
                    val modules = ModuleManager.getInstance(project).modules.asSequence()
                        .distinctBy { it.name }
                        .toList()

                    fun handleModule(module: Module, test: Boolean): Boolean {
                        if (module.name.contains(platform.id, ignoreCase = true) && !module.name.contains("common")) {
                            val dir = ModuleRootManager.getInstance(module).contentEntries.asSequence()
                                .flatMap { it.getSourceFolders(if (test) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE) }
                                .filter { !JavaProjectRootsUtil.isForGeneratedSources(it) }
                                .mapNotNull { it.file }
                                .firstOrNull { it.path.contains("/main/") }
                            if (dir != null) {
                                directory = PsiManager.getInstance(project).findDirectory(dir)
                                return true
                            }
                        }
                        return false
                    }

                    for (module in modules) { if (handleModule(module, false)) break }
                    if (directory == null) {
                        for (module in modules) { if (handleModule(module, true)) break }
                    }

                    ImplementPlatformImplFixDialog(project, platform, packageName, method, directory).show()
                }
            }
        }
    }

    companion object {
        /**
         * Creates a static implementation method in the given class using the expected signature.
         * The first parameter (the instance owner) will use the simple class name if the original
         * method is non‑static.
         */
        fun addMethod(
            project: Project,
            expectedSignature: ExpectedImplSignature,
            clazz: PsiClass,
            originalMethod: PsiMethod? = null
        ) {
            WriteCommandAction.runWriteCommandAction(project) {
                val elementFactory = JavaPsiFacade.getElementFactory(project)

                val paramTexts = mutableListOf<String>()
                val isInstanceMethod = originalMethod != null && !originalMethod.hasModifierProperty(PsiModifier.STATIC)

                for ((index, psiType) in expectedSignature.parameterTypes.withIndex()) {
                    val typeName = if (index == 0 && isInstanceMethod) {
                        // Use simple name for the 'this' parameter
                        originalMethod.containingClass?.name ?: psiType.canonicalText
                    } else {
                        psiType.canonicalText
                    }
                    val paramName = if (index == 0 && isInstanceMethod) "instance" else "arg$index"
                    paramTexts.add("$typeName $paramName")
                }

                val returnTypeText = expectedSignature.returnType.canonicalText
                val methodText = buildString {
                    append("public static $returnTypeText ${expectedSignature.name}(")
                    append(paramTexts.joinToString(", "))
                    append(") {\n")
                    if (expectedSignature.returnType == PsiType.VOID) {
                        append("    throw new UnsupportedOperationException(\"TODO: Implement for platform\");\n")
                    } else {
                        append("    // TODO: Implement for platform\n")
                        append("    return ${getDefaultReturnValue(expectedSignature.returnType)};\n")
                    }
                    append("}")
                }

                val newMethod = elementFactory.createMethodFromText(methodText, clazz)
                val inserted = GenerateMembersUtil.insert(clazz, newMethod, clazz.methods.lastOrNull(), false)
                (inserted as? Navigatable)?.navigate(true)
            }
        }


    }

    private tailrec fun findMethod(element: PsiElement): PsiMethod? =
        when {
            element is PsiMethod -> element
            element.parent != null -> findMethod(element.parent)
            else -> null
        }

    private fun findJavaSourceDirectory(directories: Array<out PsiDirectory>): PsiDirectory {
        if (directories.size == 1) return directories.single()

        val srcMainJava = directories.firstOrNull {
            val path = it.virtualFile.toNioPath().toAbsolutePath()
            "src/main/java" in path.toString().replace(path.fileSystem.separator, "/")
        }
        if (srcMainJava != null) return srcMainJava

        val anyJava = directories.firstOrNull {
            "java" in it.virtualFile.toNioPath().toAbsolutePath().toString()
        }
        if (anyJava != null) return anyJava

        return directories.first()
    }
}
