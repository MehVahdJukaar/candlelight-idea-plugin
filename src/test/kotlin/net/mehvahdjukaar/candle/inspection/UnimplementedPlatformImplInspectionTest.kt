package net.mehvahdjukaar.candle.inspection

import net.mehvahdjukaar.candle.CandleLightFixtureTestCase

class UnimplementedPlatformImplInspectionTest : CandleLightFixtureTestCase() {

    fun testMissingPlatformImplementationIsReported() {
        addFabricPlatformMarker()
        myFixture.addClass(
            """
            package com.example;
            import net.mehvahdjukaar.candlelight.api.PlatformImpl;

            public class Example {
                @PlatformImpl
                public static void doWork() {
                    throw new AssertionError();
                }
            }
            """.trimIndent()
        )

        openExampleEditor()
        myFixture.enableInspections(UnimplementedPlatformImplInspection::class.java)
        val warnings = myFixture.doHighlighting()

        assertTrue(
            warnings.any { highlight ->
                highlight.description?.contains("no implementation", ignoreCase = true) == true
            }
        )
    }

    fun testExistingPlatformImplementationIsAccepted() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        openExampleEditor()
        myFixture.enableInspections(UnimplementedPlatformImplInspection::class.java)
        val warnings = myFixture.doHighlighting()

        assertTrue(
            warnings.none { highlight ->
                highlight.description?.contains("no implementation", ignoreCase = true) == true
            }
        )
    }
}
