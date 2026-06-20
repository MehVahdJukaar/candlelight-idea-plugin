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
        myFixture.addClass(
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

    protected fun addNeoForgePlatformMarker() {
        myFixture.addClass(
            """
            package net.neoforged.neoforge.common;
            public final class NeoForgeCommonMarker {}
            """.trimIndent()
        )
    }

    protected fun addVirtualOverrideAnnotation() {
        myFixture.addClass(
            """
            package net.mehvahdjukaar.candlelight.api;
            public @interface VirtualOverride {
                String value();
            }
            """.trimIndent()
        )
    }

    protected fun addNeoForgeBlockExtension() {
        myFixture.addClass(
            """
            package net.neoforged.neoforge.common.extensions;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.level.LevelReader;
            import net.minecraft.core.BlockPos;
            import net.minecraft.world.entity.LivingEntity;

            public interface IBlockExtension {
                default boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
                    return false;
                }
            }
            """.trimIndent()
        )
    }

    protected fun addCommonBlockHierarchy(virtualOverrideMethod: String = DEFAULT_IS_LADDER_OVERRIDE) {
        myFixture.addFileToProject(
            "common/src/com/example/RopeBlock.java",
            """
            package com.example;

            import net.mehvahdjukaar.candlelight.api.VirtualOverride;
            import net.minecraft.world.level.block.Block;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.level.LevelReader;
            import net.minecraft.core.BlockPos;
            import net.minecraft.world.entity.LivingEntity;

            public class RopeBlock extends Block {
                public RopeBlock() {
                    super(null);
                }

                $virtualOverrideMethod
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.world.level.block;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.level.block.state.BlockBehaviour;
            import net.neoforged.neoforge.common.extensions.IBlockExtension;

            // Models the NeoForge-remapped Block, which implements IBlockExtension. The single-module
            // light fixture can't host per-platform Block variants, so the package-based platform
            // attribution in the index is what keeps these methods NeoForge-specific.
            public class Block extends BlockBehaviour implements IBlockExtension {
                protected Block(Properties properties) {
                    super(properties);
                }
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.world.level.block.state;
            public class BlockState {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.world.level;
            public interface LevelReader {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.core;
            public class BlockPos {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.world.entity;
            public class LivingEntity {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package net.minecraft.world.level.block.state;
            public class BlockBehaviour {
                public static class Properties {}
                protected BlockBehaviour(Properties properties) {}
            }
            """.trimIndent()
        )
    }

    protected fun openCommonBlockEditor() {
        val file = myFixture.findClass("com.example.RopeBlock").containingFile!!
        myFixture.openFileInEditor(file.virtualFile)
    }

    protected fun openExampleEditor() {
        val file = myFixture.findClass("com.example.Example").containingFile!!
        myFixture.openFileInEditor(file.virtualFile)
    }

    companion object {
        private val DEFAULT_IS_LADDER_OVERRIDE = """
            @VirtualOverride("neoforge")
            public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
                return true;
            }
        """.trimIndent()
    }
}
