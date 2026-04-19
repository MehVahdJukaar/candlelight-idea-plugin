package net.mehvahdjukaar.candle.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.java.JavaLanguage
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiIdentifier

class VirtualOverrideCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiIdentifier::class.java)
                .inside(PsiClass::class.java)
                .withLanguage(JavaLanguage.INSTANCE),
            VirtualOverrideCompletionProvider()
        )
    }
}
