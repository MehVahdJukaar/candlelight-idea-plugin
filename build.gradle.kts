import org.gradle.internal.execution.caching.CachingState.enabled
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import sun.tools.jar.resources.jar

plugins {
    id("org.jetbrains.intellij.platform") version "2.17.0"
    kotlin("jvm") version "2.3.0"
}

group = "net.mehvahdjukaar"
version = "2.2.4"

repositories {
    mavenCentral()
    mavenLocal() // for the shared editor core (candle-image-editor)

    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}


dependencies {
    intellijPlatform {
        // Build against the platform we actually ship on. Compiling against 2025.1 and running
        // on 2026.1 caused a binary-incompat linkage error that broke the image viewer's
        // FileEditor at runtime. Note: the old create("IC", …) coordinate is no longer
        // published since 2025.3 — use intellijIdea(version).
        intellijIdea("2026.1.3")
        bundledPlugin("com.intellij.java")

        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    // The shared editor core (pure Java, zero deps), also bundled by the Nautilus Studio mod.
    // Publish it from the candle-image-editor project: ./gradlew :core:publishToMavenLocal
    implementation("net.mehvahdjukaar:candle-image-editor:1.0.1")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Compatible from 2025.1 onward; leave the upper bound open so future builds load.
            sinceBuild.set("251")
            untilBuild.set(provider { null })
        }
    }
}

tasks {
    jar {
        from("COPYING", "COPYING.LESSER")

        // Embed the pure-Java editor core (candle-image-editor) directly into the plugin jar so the
        // jar is self-contained. We install by dropping this single jar into the IDE plugins folder,
        // which has no lib/ sibling to resolve the dependency from - without this, IdeUiBackend can't
        // find its superclass platform.UiBackend and the image FileEditor fails to load at runtime.
        from(
            configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("candle-image-editor") }
            .map { zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    test {
        // Tests are disabled: the publish (`github`) task and local `build` skip them.
        enabled = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}


tasks.register("github") {
    group = "publish"
    dependsOn("build")
    doLast {
        val version = project.version.toString()

        // Gradle 9 removed Project.exec; shell out via ProcessBuilder instead.
        fun git(vararg args: String) {
            val code = ProcessBuilder(listOf("git", *args)).inheritIO().start().waitFor()
            if (code != 0) throw GradleException("git ${args.joinToString(" ")} failed with exit $code")
        }
        git("tag", "v$version")
        git("push", "origin", "v$version")
    }
}
