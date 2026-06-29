import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform") version "2.17.0"
    kotlin("jvm") version "2.3.0"
}

group = "net.mehvahdjukaar"
version = "1.9.1"

repositories {
    mavenCentral()

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
    }

    test {
        useJUnit()
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
