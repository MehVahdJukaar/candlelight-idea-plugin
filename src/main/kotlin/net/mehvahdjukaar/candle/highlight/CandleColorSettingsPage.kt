package net.mehvahdjukaar.candle.highlight

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import net.mehvahdjukaar.candle.util.CandleBundle
import javax.swing.Icon

class CandleColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = CandleBundle["settings.display.name"]

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter =
        SyntaxHighlighterFactory.getSyntaxHighlighter(JavaLanguage.INSTANCE, null, null)

    override fun getDemoText(): String = """
        public class Rope {
            public void tick() {
                pullDown();
                updateLength(4);
                snap();
            }

            private void <priv>pullDown</priv>() {
            }

            protected void <prot>updateLength</prot>(int length) {
            }

            void <pkg>snap</pkg>() {
            }
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "priv" to MethodVisibilityHighlightVisitor.PRIVATE_METHOD,
        "prot" to MethodVisibilityHighlightVisitor.PROTECTED_METHOD,
        "pkg" to MethodVisibilityHighlightVisitor.PACKAGE_PRIVATE_METHOD,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor(CandleBundle["colors.privateMethod"], MethodVisibilityHighlightVisitor.PRIVATE_METHOD),
        AttributesDescriptor(CandleBundle["colors.protectedMethod"], MethodVisibilityHighlightVisitor.PROTECTED_METHOD),
        AttributesDescriptor(CandleBundle["colors.packagePrivateMethod"], MethodVisibilityHighlightVisitor.PACKAGE_PRIVATE_METHOD),
    )

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
