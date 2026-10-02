package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiThrowStatement
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.hasPlatformImplAnnotation

class PlatformImplBodyInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!method.hasPlatformImplAnnotation) return

                if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.NATIVE)) {
                    return
                }

                val body = method.body
                if (body == null) {
                    holder.registerProblem(
                        method.nameIdentifier ?: method,
                        CandleBundle["inspection.platformImpl.missingBody"],
                        AddAssertionErrorBodyFix()
                    )
                    return
                }

                if (!isValidExpectBody(body)) {
                    holder.registerProblem(
                        body,
                        CandleBundle["inspection.platformImpl.invalidBody"],
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
