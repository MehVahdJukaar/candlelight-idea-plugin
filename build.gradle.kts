import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform") version "2.17.0"
    kotlin("jvm") version "2.3.0"
}

group = "net.mehvahdjukaar"
version = "2.3.0"

repositories {
    mavenCentral()
    mavenLocal() //candle-image-editor

    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}


dependencies {
    intellijPlatform {
        intellijIdea("2026.1.3")
        bundledPlugin("com.intellij.java")

        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    implementation("net.mehvahdjukaar:candle-image-editor:1.0.1")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
            untilBuild.set(provider { null })
        }
    }
}

tasks {
    jar {
        from("COPYING", "COPYING.LESSER")

        // single jar install has no lib/ folder, so the editor core has to be inside it
        from(
            configurations.runtimeClasspath.get()
                .filter { it.name.startsWith("candle-image-editor") }
                .map { zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    test {
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
