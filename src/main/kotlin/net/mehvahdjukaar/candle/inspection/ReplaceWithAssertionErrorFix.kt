package net.mehvahdjukaar.candle.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiMethod
import net.mehvahdjukaar.candle.util.CandleBundle

class ReplaceWithAssertionErrorFix : LocalQuickFix {
    override fun getFamilyName(): String =
        CandleBundle["inspection.platformImpl.replaceWithAssertion"]

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = when (val element = descriptor.psiElement) {
            is PsiMethod -> element
            is PsiCodeBlock -> element.parent as? PsiMethod
            else -> (element.parent as? PsiMethod) ?: return
        } ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val factory = JavaPsiFacade.getElementFactory(project)
            val newBody = factory.createCodeBlockFromText("{ throw new AssertionError(); }", method)

            val oldBody = method.body
            if (oldBody != null) {
                oldBody.replace(newBody)
            } else {
                method.addAfter(newBody, method.parameterList)
            }
        }
    }
}

class AddAssertionErrorBodyFix : LocalQuickFix {
    override fun getFamilyName(): String =
        CandleBundle["inspection.platformImpl.addBody"]

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = descriptor.psiElement as? PsiMethod ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val factory = JavaPsiFacade.getElementFactory(project)
            val body = factory.createCodeBlockFromText("{ throw new AssertionError(); }", method)
            method.addAfter(body, method.parameterList)
        }
    }
}
