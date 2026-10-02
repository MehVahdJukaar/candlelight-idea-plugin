package net.mehvahdjukaar.candle.highlight

import net.mehvahdjukaar.candle.CandleLightFixtureTestCase

class MethodVisibilityHighlightVisitorTest : CandleLightFixtureTestCase() {

    fun testOnlyNonPublicDeclarationsGetColored() {
        myFixture.configureByText(
            "Rope.java",
            """
            public class Rope {
                public void tick() {
                    pullDown();
                    updateLength(4);
                    snap();
                    tick();
                }
                private void pullDown() {}
                protected void updateLength(int length) {}
                void snap() {}
            }
            """.trimIndent()
        )
        assertEquals(listOf("pullDown"), coloredWith(MethodVisibilityHighlightVisitor.PRIVATE_METHOD))
        assertEquals(listOf("updateLength"), coloredWith(MethodVisibilityHighlightVisitor.PROTECTED_METHOD))
        assertEquals(listOf("snap"), coloredWith(MethodVisibilityHighlightVisitor.PACKAGE_PRIVATE_METHOD))
    }

    fun testColorFollowsModifierEdits() {
        myFixture.configureByText(
            "Rope.java",
            """
            public class Rope {
                <caret>public void tick() {}
            }
            """.trimIndent()
        )
        myFixture.doHighlighting()

        myFixture.editor.selectionModel.selectWordAtCaret(false)
        myFixture.type("private")
        assertEquals(listOf("tick"), coloredWith(MethodVisibilityHighlightVisitor.PRIVATE_METHOD))

        myFixture.editor.selectionModel.selectWordAtCaret(false)
        myFixture.type("public")
        assertEquals(emptyList<String>(), coloredWith(MethodVisibilityHighlightVisitor.PRIVATE_METHOD))
    }

    private fun coloredWith(key: Any) =
        myFixture.doHighlighting().filter { it.type.attributesKey == key }.map { it.text }
}
