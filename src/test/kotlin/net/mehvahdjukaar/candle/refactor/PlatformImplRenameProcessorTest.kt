package net.mehvahdjukaar.candle.refactor

import net.mehvahdjukaar.candle.CandleLightFixtureTestCase

class PlatformImplRenameProcessorTest : CandleLightFixtureTestCase() {

    fun testRenameCommonMethodSyncsPlatformImplementation() {
        addFabricPlatformMarker()
        addCommonPlatformImplClass("")

        val commonMethod = myFixture.findClass("com.example.Example").findMethodsByName("doWork", false).single()
        val implMethod = myFixture.findClass("com.example.platform.ExampleImpl").findMethodsByName("doWork", false).single()

        val processor = PlatformImplRenameProcessor()
        assertTrue(processor.canProcessElement(commonMethod))

        val renames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        processor.prepareRenaming(commonMethod, "execute", renames)

        assertEquals("execute", renames[implMethod])
    }

    fun testRenameCommonClassSyncsPlatformImplementationClass() {
        addCommonPlatformImplClass("")

        val commonClass = myFixture.findClass("com.example.Example")
        val implClass = myFixture.findClass("com.example.platform.ExampleImpl")

        val processor = PlatformImplRenameProcessor()
        assertTrue(processor.canProcessElement(commonClass))

        val renames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        processor.prepareRenaming(commonClass, "Worker", renames)

        assertEquals("WorkerImpl", renames[implClass])
    }

    fun testRenamePlatformImplClassSyncsCommonClass() {
        addCommonPlatformImplClass()

        val commonClass = myFixture.findClass("com.example.Example")
        val implClass = myFixture.findClass("com.example.platform.ExampleImpl")

        val processor = PlatformImplRenameProcessor()
        val renames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        processor.prepareRenaming(implClass, "WorkerImpl", renames)

        assertEquals("Worker", renames[commonClass])
    }

    fun testRenamePlatformImplMethodSyncsCommonDeclaration() {
        addCommonPlatformImplClass("")

        val implMethod = myFixture.findClass("com.example.platform.ExampleImpl").findMethodsByName("doWork", false).single()
        val commonMethod = myFixture.findClass("com.example.Example").findMethodsByName("doWork", false).single()

        val processor = PlatformImplRenameProcessor()
        val renames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        processor.prepareRenaming(implMethod, "execute", renames)

        assertEquals("execute", renames[commonMethod])
    }
}
