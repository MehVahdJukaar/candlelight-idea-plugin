package net.mehvahdjukaar.candle

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import net.mehvahdjukaar.candle.settings.CandleSettings

abstract class CandleLightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        CandleSettings.getInstance(project).psiCachingEnabled = true
        addPlatformImplAnnotation()
    }

    protected fun addPlatformImplAnnotation() {
        myFixture.addClass(
            """
            package net.mehvahdjukaar.candlelight.api;
            public @interface PlatformImpl {}
            """.trimIndent()
        )
    }

    protected fun addFabricPlatformMarker() {
        myFixture.addClass(
            """
            package net.fabricmc.loader;
            public final class FabricLoader {}
            """.trimIndent()
        )
    }

    protected fun addCommonPlatformImplClass(
        extraCommonMembers: String = "",
        implMethodBody: String = "throw new UnsupportedOperationException();"
    ) {
        myFixture.addClass(
            """
            package com.example;
            import net.mehvahdjukaar.candlelight.api.PlatformImpl;

            public class Example {
                @PlatformImpl
                public static void doWork() {
                    throw new AssertionError();
                }
                $extraCommonMembers
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "fabric/com/example/platform/ExampleImpl.java",
            """
            package com.example.platform;
            public class ExampleImpl {
                public static void doWork() {
                    $implMethodBody
                }
            }
            """.trimIndent()
        )
    }
}
