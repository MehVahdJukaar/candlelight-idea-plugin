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
import net.mehvahdjukaar.candle.util.AnnotationType
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.Platform
import net.mehvahdjukaar.candle.util.getDefaultReturnValue
import org.jetbrains.jps.model.java.JavaSourceRootType

class ImplementPlatformImplFix(private val platforms: List<Platform>) : LocalQuickFix {
    override fun getFamilyName(): String {
        val platform = platforms.singleOrNull()
        return if (platform != null)
            CandleBundle["inspection.implementPlatformImpl.single", platform]
        else
            CandleBundle["inspection.implementPlatformImpl"]
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

                    val platformDirs = pkg?.directories?.filter {
                        it.virtualFile.path.contains("/${platform.id}/", ignoreCase = true)
                    } ?: emptyList()

                    var dir = if (platformDirs.isNotEmpty()) {
                        findJavaSourceDirectory(platformDirs.toTypedArray())
                    } else {
                        null
                    }

                    if (dir == null) {
                        val module = platform.findModuleForPlatform(project)
                        if (module != null) {
                            val contentEntry = ModuleRootManager.getInstance(module).contentEntries.firstOrNull()
                            val sourceRoot = contentEntry?.getSourceFolders(JavaSourceRootType.SOURCE)
                                ?.firstOrNull { !JavaProjectRootsUtil.isForGeneratedSources(it) && it.file?.path?.contains("/main/") == true }
                                ?: contentEntry?.getSourceFolders(JavaSourceRootType.SOURCE)?.firstOrNull()

                            val rootFile = sourceRoot?.file
                            if (rootFile != null) {
                                val rootDir = PsiManager.getInstance(project).findDirectory(rootFile)
                                if (rootDir != null) {
                                    dir = rootDir
                                    packageName.split('.').forEach { part ->
                                        if (part.isNotEmpty()) {
                                            dir = dir!!.findSubdirectory(part) ?: dir!!.createSubdirectory(part)
                                        }
                                    }
                                }
                            }
                        }
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

                val isInstanceMethod = originalMethod != null && !originalMethod.hasModifierProperty(PsiModifier.STATIC)

                val methodText = buildString {
                    append("public static ")
                    if (originalMethod != null) {
                        val returnType = originalMethod.returnType?.presentableText ?: "void"
                        val returnAnnos = originalMethod.modifierList.annotations
                            .filter { anno ->
                                val qName = anno.qualifiedName
                                qName == null || AnnotationType.PLATFORM_IMPLEMENTATION.none { p -> p == qName }
                            }
                            .joinToString(" ") { it.text }
                        if (returnAnnos.isNotEmpty()) append(returnAnnos).append(" ")
                        append(returnType).append(" ").append(originalMethod.name).append("(")

                        val params = mutableListOf<String>()
                        if (isInstanceMethod) {
                            val containingClass = originalMethod.containingClass
                            val typeName = containingClass?.name ?: expectedSignature.parameterTypes.first().presentableText
                            params.add("$typeName instance")
                        }
                        originalMethod.parameterList.parameters.forEach {
                            val annos = it.modifierList?.annotations
                                ?.filter { anno ->
                                    val qName = anno.qualifiedName
                                    qName == null || AnnotationType.PLATFORM_IMPLEMENTATION.none { p -> p == qName }
                                }
                                ?.joinToString(" ") { a -> a.text } ?: ""
                            val paramText = if (annos.isNotEmpty()) "$annos ${it.type.presentableText} ${it.name}" else "${it.type.presentableText} ${it.name}"
                            params.add(paramText)
                        }
                        append(params.joinToString(", "))
                    } else {
                        // Fallback if originalMethod is null (shouldn't happen with our new flow)
                        val returnTypeText = expectedSignature.returnType.presentableText
                        append(returnTypeText).append(" ").append(expectedSignature.name).append("(")
                        val paramTexts = expectedSignature.parameterTypes.mapIndexed { index, psiType ->
                            "${psiType.presentableText} arg$index"
                        }
                        append(paramTexts.joinToString(", "))
                    }

                    append(") {\n")
                    if (expectedSignature.returnType == PsiType.VOID || (expectedSignature.returnType as? PsiPrimitiveType)?.name == "void") {
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
