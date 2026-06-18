package net.mehvahdjukaar.candle.util

import net.mehvahdjukaar.candle.CandleLightFixtureTestCase
import net.mehvahdjukaar.candle.settings.CandleSettings

class PlatformImplNavigationTest : CandleLightFixtureTestCase() {

    fun testCommonMethodFindsPlatformImplementation() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        val commonMethod = myFixture.findClass("com.example.Example").findMethodsByName("doWork", false).single()
        val implMethod = myFixture.findClass("com.example.platform.ExampleImpl").findMethodsByName("doWork", false).single()

        assertContainsElements(commonMethod.platformMethods, implMethod)
    }

    fun testPlatformImplementationFindsCommonMethod() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        val implMethod = myFixture.findClass("com.example.platform.ExampleImpl").findMethodsByName("doWork", false).single()
        val commonMethod = myFixture.findClass("com.example.Example").findMethodsByName("doWork", false).single()

        assertContainsElements(implMethod.commonMethods, commonMethod)
    }

    fun testCachingCanBeDisabledWithoutChangingResults() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        val commonMethod = myFixture.findClass("com.example.Example").findMethodsByName("doWork", false).single()

        CandleSettings.getInstance(project).psiCachingEnabled = true
        val cached = commonMethod.platformMethods.toSet()

        CandleSettings.getInstance(project).psiCachingEnabled = false
        val uncached = commonMethod.platformMethods.toSet()

        assertEquals(cached, uncached)
    }
}
