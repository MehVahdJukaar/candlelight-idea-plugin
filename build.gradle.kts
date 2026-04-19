plugins {
    id("org.jetbrains.intellij.platform") version "2.3.0"
    kotlin("jvm") version "2.3.0"
}

group = "dev.architectury"
version = "1.7.0-candlelight"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}


dependencies {
    intellijPlatform {
        // Try a confirmed 2024 or 2025 version
        create("IC", "2025.1")
        bundledPlugin("com.intellij.java")

        bundledPlugin("org.jetbrains.kotlin")
        instrumentationTools()

    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // "251" corresponds to the 2025.1 release cycle
            sinceBuild.set("251")
            untilBuild.set("251.*")
        }
    }
}

tasks {
    jar {
        from("COPYING", "COPYING.LESSER")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}
