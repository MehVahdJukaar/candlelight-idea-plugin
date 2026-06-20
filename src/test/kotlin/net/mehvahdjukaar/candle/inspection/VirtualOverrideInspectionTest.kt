package net.mehvahdjukaar.candle.inspection

import net.mehvahdjukaar.candle.CandleLightFixtureTestCase
import net.mehvahdjukaar.candle.util.Platform
import net.mehvahdjukaar.candle.util.findPlatformVirtualOverrides
import net.mehvahdjukaar.candle.util.isValidVirtualOverrideForPlatform

class VirtualOverrideInspectionTest : CandleLightFixtureTestCase() {

    fun testValidNeoForgeVirtualOverridePassesInspection() {
        addFabricPlatformMarker()
        addNeoForgePlatformMarker()
        addVirtualOverrideAnnotation()
        addNeoForgeBlockExtension()
        addCommonBlockHierarchy()

        openCommonBlockEditor()
        myFixture.enableInspections(VirtualOverrideInspection::class.java)
        val highlights = myFixture.doHighlighting()

        assertTrue(
            highlights.none { highlight ->
                highlight.description?.contains("Invalid @VirtualOverride", ignoreCase = true) == true ||
                    highlight.description?.contains("does not override", ignoreCase = true) == true
            }
        )
    }

    fun testVirtualOverrideLinksToNeoForgeExtensionMethod() {
        addFabricPlatformMarker()
        addNeoForgePlatformMarker()
        addVirtualOverrideAnnotation()
        addNeoForgeBlockExtension()
        addCommonBlockHierarchy()

        val method = myFixture.findClass("com.example.RopeBlock").findMethodsByName("isLadder", false).single()
        assertTrue(method.isValidVirtualOverrideForPlatform(Platform.NEOFORGE))
        val related = method.findPlatformVirtualOverrides()

        assertEquals(1, related.size)
        assertEquals("isLadder", related.single().name)
        assertTrue(related.single().containingClass?.qualifiedName?.contains("IBlockExtension") == true)
    }

    fun testMissingPlatformVirtualOverrideIsReported() {
        addFabricPlatformMarker()
        addNeoForgePlatformMarker()
        addVirtualOverrideAnnotation()
        addNeoForgeBlockExtension()
        addCommonBlockHierarchy(
            virtualOverrideMethod = """
                @VirtualOverride("neoforge")
                public boolean isNotOnPlatform() {
                    return false;
                }
            """.trimIndent()
        )

        openCommonBlockEditor()
        myFixture.enableInspections(VirtualOverrideInspection::class.java)
        val highlights = myFixture.doHighlighting()

        assertTrue(
            highlights.any { highlight ->
                highlight.description?.contains("does not override", ignoreCase = true) == true
            }
        )
    }
}
