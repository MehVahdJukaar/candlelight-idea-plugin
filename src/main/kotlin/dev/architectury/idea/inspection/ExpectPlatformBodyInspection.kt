// ExpectPlatformBodyInspection.kt
package dev.architectury.idea.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import dev.architectury.idea.util.ArchitecturyBundle
import dev.architectury.idea.util.isCommonExpectPlatform

class ExpectPlatformBodyInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                // Only inspect @ExpectPlatform methods in common source sets
                if (!method.isCommonExpectPlatform) return

                // Abstract or native methods don't have bodies to check
                if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.NATIVE)) {
                    return
                }

                val body = method.body
                if (body == null) {
                    // Missing body entirely (shouldn't happen for non-abstract methods)
                    holder.registerProblem(
                        method.nameIdentifier ?: method,
                        ArchitecturyBundle["inspection.expectPlatform.missingBody"],
                        AddAssertionErrorBodyFix()
                    )
                    return
                }

                // Check if body is exactly a throw new AssertionError();
                if (!isValidExpectBody(body)) {
                    holder.registerProblem(
                        body,
                        ArchitecturyBundle["inspection.expectPlatform.invalidBody"],
                        ReplaceWithAssertionErrorFix()
                    )
                }
            }
        }

    private fun isValidExpectBody(body: PsiCodeBlock): Boolean {
        val statements = body.statements
        if (statements.size != 1) return false

        val stmt = statements[0]
        if (stmt !is PsiThrowStatement) return false

        val exception = stmt.exception ?: return false
        if (exception !is PsiNewExpression) return false

        val classRef = exception.classReference ?: return false
        return classRef.qualifiedName == "java.lang.AssertionError"
    }
}
