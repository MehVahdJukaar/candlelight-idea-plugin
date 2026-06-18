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

        myFixture.enableInspections(UnimplementedPlatformImplInspection::class.java)
        val warnings = myFixture.doHighlighting()

        assertTrue(
            warnings.any { it.description.contains("no implementation", ignoreCase = true) }
        )
    }

    fun testExistingPlatformImplementationIsAccepted() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        myFixture.enableInspections(UnimplementedPlatformImplInspection::class.java)
        val warnings = myFixture.doHighlighting()

        assertTrue(
            warnings.none { it.description.contains("no implementation", ignoreCase = true) }
        )
    }
}
