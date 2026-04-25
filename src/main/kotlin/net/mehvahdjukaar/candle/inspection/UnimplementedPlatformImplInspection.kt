package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import net.mehvahdjukaar.candle.util.AnnotationType
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.Platform
import net.mehvahdjukaar.candle.util.findAnnotation
import net.mehvahdjukaar.candle.util.hasPlatformImplAnnotation

class UnimplementedPlatformImplInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                // Only inspect @PlatformImpl methods in common source sets
                if (!method.hasPlatformImplAnnotation) return

                val containingClass = method.containingClass ?: return
                val project = method.project
                val facade = JavaPsiFacade.getInstance(project)
                val implClassName = Platform.getPlatformImplImplementationName(containingClass)

                // Find all candidate implementation classes across the project
                val candidateClasses = facade.findClasses(implClassName, GlobalSearchScope.projectScope(project))

                // Build the expected signature of the implementation method
                val expectedSignature = ExpectedImplSignature.fromExpectMethod(method)

                val missingPlatforms = Platform.listAvailable(project).filter { platform ->

                    // Find the impl class that belongs to this platform's module
                    val implClass = candidateClasses.firstOrNull { platform.hasElement(it) }

                    if (implClass == null) {
                        true // class missing entirely
                    } else {
                        // Check if a method exists with the expected signature
                        !hasMatchingImplMethod(implClass, expectedSignature)
                    }
                }

                if (missingPlatforms.isNotEmpty()) {
                    val fixes = missingPlatforms.mapTo(ArrayList()) { ImplementPlatformImplFix(listOf(it)) }

                    // Add "Fix all" option if multiple platforms are missing
                    if (fixes.size > 1) {
                        fixes.add(0, ImplementPlatformImplFix(missingPlatforms))
                    }

                    holder.registerProblem(
                        method.findAnnotation(AnnotationType.PLATFORM_IMPLEMENTATION) ?: method.nameIdentifier ?: method,
                        CandleBundle["inspection.missingExpectPlatform", method.name, missingPlatforms.joinToString()],
                        ProblemHighlightType.ERROR,
                        *fixes.toTypedArray()
                    )
                }
            }

            /**
             * Checks if the given PsiClass contains a method that matches the expected implementation signature.
             */
            private fun hasMatchingImplMethod(implClass: PsiClass, expected: ExpectedImplSignature): Boolean {
                return implClass.findMethodsByName(expected.name, false).any { expected.matchesImplMethod(it) }
            }
        }
}
