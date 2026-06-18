package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInspection.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import net.mehvahdjukaar.candle.util.AnnotationType
import net.mehvahdjukaar.candle.util.CandleBundle
import net.mehvahdjukaar.candle.util.Platform
import net.mehvahdjukaar.candle.util.isValidVirtualOverrideForPlatform

class VirtualOverrideInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                val annotation = method.annotations.firstOrNull { ann ->
                    AnnotationType.VIRTUAL_OVERRIDE.any { ann.hasQualifiedName(it) }
                } ?: return

                val platformValue = annotation.findAttributeValue("value") as? PsiLiteralExpression
                val platformId = platformValue?.value as? String

                if (platformId.isNullOrEmpty()) {
                    holder.registerProblem(
                        annotation,
                        CandleBundle["inspection.virtualOverride.missingPlatform"],
                        ProblemHighlightType.ERROR,
                        RemoveVirtualOverrideFix()
                    )
                    return
                }

                val plat = Platform.fromString(platformId)
                if (plat == null) {
                    holder.registerProblem(
                        annotation,
                        CandleBundle["inspection.virtualOverride.invalidPlatform"],
                        ProblemHighlightType.ERROR,
                        RemoveVirtualOverrideFix()
                    )
                    return
                }

                if (!method.isValidVirtualOverrideForPlatform(plat)) {
                    holder.registerProblem(
                        annotation,
                        CandleBundle["inspection.virtualOverride.notOverriding", plat],
                        ProblemHighlightType.ERROR,
                        RemoveVirtualOverrideFix()
                    )
                }
            }
        }
    }
}

class RemoveVirtualOverrideFix : LocalQuickFix {
    override fun getFamilyName(): String = CandleBundle["inspection.virtualOverride.removeFix"]

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotation = descriptor.psiElement as? PsiAnnotation ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            annotation.delete()
        }
    }
}
