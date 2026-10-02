package net.mehvahdjukaar.candle.contributor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import net.mehvahdjukaar.candle.util.AnnotationType
import net.mehvahdjukaar.candle.util.PlatformVirtualMethod
import net.mehvahdjukaar.candle.util.findAllPlatformVirtualOverridableMethods
import net.mehvahdjukaar.candle.util.getDefaultReturnValue
import net.mehvahdjukaar.candle.util.isCommon
import net.mehvahdjukaar.candle.util.signatureKey

class VirtualOverrideCompletionProvider : CompletionProvider<CompletionParameters>() {


    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {

        val position = parameters.position
        val psiFile = position.containingFile
        val project = position.project
        val module = ModuleUtil.findModuleForPsiElement(psiFile) ?: return
        if (!module.isCommon) return

        val containingMethodBody = PsiTreeUtil.getParentOfType(position, PsiCodeBlock::class.java)
        if (containingMethodBody != null) {
            val method = PsiTreeUtil.getParentOfType(position, PsiMethod::class.java)
            if (method != null && PsiTreeUtil.isAncestor(method.body, position, true)) {
                return
            }
        }

        val reference = PsiTreeUtil.getParentOfType(position, PsiReferenceExpression::class.java)
        val isQualifiedCall = reference != null && reference.qualifierExpression != null
        if (isQualifiedCall) return


        val containingClass = PsiTreeUtil.getParentOfType(position, PsiClass::class.java) ?: return

        val platformVirtualMethods = containingClass.findAllPlatformVirtualOverridableMethods()

        val existingSignatures = containingClass.methods.map { it.signatureKey() }.toSet()

        val filteredMethods = platformVirtualMethods.filter { pvm ->
            pvm.signatureKey !in existingSignatures
        }


        for (pvm in filteredMethods) {
            val lookupElement = createLookupElement(pvm, containingClass)
            val lowPriorityElement = PrioritizedLookupElement.withPriority(lookupElement, -100000.0)
            result.addElement(lowPriorityElement)
        }
    }

    private fun createLookupElement(pvm: PlatformVirtualMethod, targetClass: PsiClass): LookupElement {
        val method = pvm.method
        val returnType = method.returnType?.presentableText ?: "void"
        val params = method.parameterList.parameters.joinToString(", ") { it.type.presentableText }
        val tailText = " -> $returnType ($params)"
        val platformName = pvm.platform.id

        return LookupElementBuilder.create(method, method.name)
            .withIcon(method.getIcon(0))
            .withTailText(tailText, true)
            .withTypeText("$platformName · ${method.containingClass?.name}")
            .bold()
            .withInsertHandler { ctx, item ->
                val editor = ctx.editor
                val project = ctx.project
                val document = editor.document

                //completion already typed the name in, drop it before the stub goes in
                val startOffset = ctx.startOffset
                val tailOffset = ctx.tailOffset
                document.deleteString(startOffset, tailOffset)

                val methodText = buildMethodText(pvm)
                document.insertString(startOffset, methodText)

                val bodyStart = startOffset + methodText.indexOf("{") + 1
                editor.caretModel.moveToOffset(bodyStart)

                PsiDocumentManager.getInstance(project).commitDocument(document)
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                if (psiFile != null) {
                    val insertedMethod = PsiTreeUtil.findElementOfClassAtOffset(
                        psiFile, bodyStart, PsiMethod::class.java, false
                    )
                    insertedMethod?.let { CodeStyleManager.getInstance(project).reformat(it) }
                }
            }
    }

    private fun buildMethodText(pvm: PlatformVirtualMethod): String {
        val method = pvm.method
        val returnType = method.returnType?.let { getSimpleTypeName(it) } ?: "void"
        val methodName = method.name

        val params = method.parameterList.parameters.joinToString(", ") { param ->
            val typeName = getSimpleTypeName(param.type)
            "$typeName ${param.name}"
        }

        val exceptions = method.throwsList.referenceElements.joinToString(", ") { it.canonicalText }
        val throwsClause = if (exceptions.isNotEmpty()) " throws $exceptions" else ""

        val virtualOverrideAnnotation = AnnotationType.VIRTUAL_OVERRIDE.first().substringAfterLast('.')
        val platformId = pvm.platform.id.lowercase()

        return buildString {
            append("@$virtualOverrideAnnotation(\"$platformId\")\n")
            append("public $returnType $methodName($params)$throwsClause {\n")
            if (returnType != "void") {
                append("    // TODO: Implement for ${pvm.platform.id}\n")
                append("    return ${getDefaultReturnValue(method.returnType)};\n")
            } else {
                append("    // TODO: Implement for ${pvm.platform.id}\n")
            }
            append("}\n")
        }
    }

    private fun getSimpleTypeName(psiType: PsiType): String {
        return psiType.presentableText
    }
}
