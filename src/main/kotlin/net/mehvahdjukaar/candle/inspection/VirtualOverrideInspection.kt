package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInspection.*
import com.intellij.psi.*
import net.mehvahdjukaar.candle.util.AnnotationType
import net.mehvahdjukaar.candle.util.isValidVirtualOverrideForPlatform

class VirtualOverrideInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                // Find the @VirtualOverride annotation
                val annotation = method.annotations.firstOrNull { ann ->
                    AnnotationType.VIRTUAL_OVERRIDE.any { ann.hasQualifiedName(it) }
                } ?: return

                // Extract platform value
                val platformValue = annotation.findAttributeValue("value") as? PsiLiteralExpression
                val platformId = platformValue?.value as? String

                // Get the annotation name identifier (e.g., "VirtualOverride")
                val annotationNameElement = annotation.nameReferenceElement ?: annotation

                if (platformId.isNullOrEmpty()) {
                    // Missing platform value – highlight the annotation name
                    holder.registerProblem(
                        annotationNameElement,
                        "@VirtualOverride must specify a platform",
                        ProblemHighlightType.ERROR
                    )
                    return
                }

                // Validate override
                if (!method.isValidVirtualOverrideForPlatform(platformId)) {
                    // Invalid override – highlight just the annotation name
                    holder.registerProblem(
                        annotationNameElement,
                        "Method does not override any method from platform '$platformId'",
                        ProblemHighlightType.ERROR,
                        //RemoveVirtualOverrideFix()
                    )
                }
            }
        }
    }
}

class RemoveVirtualOverrideFix : LocalQuickFix {
    override fun getFamilyName(): String = "Remove @VirtualOverride annotation"

    override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val annotation = (element as? PsiAnnotation) ?: element.parent as? PsiAnnotation ?: return

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            annotation.delete()
        }
    }
}
