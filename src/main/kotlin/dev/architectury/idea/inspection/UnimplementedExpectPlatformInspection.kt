package dev.architectury.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dev.architectury.idea.util.AnnotationType
import dev.architectury.idea.util.ArchitecturyBundle
import dev.architectury.idea.util.Platform
import dev.architectury.idea.util.findAnnotation
import dev.architectury.idea.util.isCommonExpectPlatform

class UnimplementedExpectPlatformInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                // Only inspect @ExpectPlatform methods in common source sets
                if (!method.isCommonExpectPlatform) return

                val containingClass = method.containingClass ?: return
                val project = method.project
                val facade = JavaPsiFacade.getInstance(project)
                val implClassName = Platform.getImplementationName(containingClass)

                // Find all candidate implementation classes across the project
                val candidateClasses = facade.findClasses(implClassName, GlobalSearchScope.projectScope(project))

                // Build the expected signature of the implementation method
                val expectedSignature = ExpectedImplSignature.fromExpectMethod(method)

                val missingPlatforms = Platform.values().filter { platform ->
                    // Skip platforms that are not present in this project/module
                    if (!platform.isIn(project)) return@filter false

                    // Find the impl class that belongs to this platform's module
                    val implClass = candidateClasses.firstOrNull { platform.hasClass(it) }

                    if (implClass == null) {
                        true // class missing entirely
                    } else {
                        // Check if a method exists with the expected signature
                        !hasMatchingImplMethod(implClass, expectedSignature)
                    }
                }

                if (missingPlatforms.isNotEmpty()) {
                    val fixes = missingPlatforms.mapTo(ArrayList()) { ImplementExpectPlatformFix(listOf(it)) }

                    // Add "Fix all" option if multiple platforms are missing
                    if (fixes.size > 1) {
                        fixes.add(0, ImplementExpectPlatformFix(missingPlatforms))
                    }

                    holder.registerProblem(
                        method.findAnnotation(AnnotationType.EXPECT_PLATFORM) ?: method.nameIdentifier ?: method,
                        ArchitecturyBundle["inspection.missingExpectPlatform", method.name, missingPlatforms.joinToString()],
                        *fixes.toTypedArray()
                    )
                }
            }

            /**
             * Checks if the given PsiClass contains a method that matches the expected implementation signature.
             */
            private fun hasMatchingImplMethod(implClass: PsiClass, expected: ExpectedImplSignature): Boolean {
                return implClass.findMethodsByName(expected.name, false).any { implMethod ->
                    // 1. Implementation method must be static
                    if (!implMethod.hasModifierProperty(PsiModifier.STATIC)) return@any false

                    // 2. Parameter types must match exactly
                    val implParams = implMethod.parameterList.parameters
                    val expectedTypes = expected.parameterTypes
                    if (implParams.size != expectedTypes.size) return@any false

                    val typeMatch = implParams.zip(expectedTypes).all { (param, expectedType) ->
                        param.type.equals(expectedType)
                    }
                    if (!typeMatch) return@any false

                    // 3. Return type must match (allowing covariance? usually exact match is expected)
                    implMethod.returnType?.equals(expected.returnType) ?: (expected.returnType == PsiType.VOID)
                }
            }
        }
}
