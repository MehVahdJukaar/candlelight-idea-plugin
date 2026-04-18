import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.17+"
    kotlin("jvm") version "2.0+"
}

val kotlinVersion = "1.9.0"
val kotlinLanguageVersion = kotlinVersion.substringBeforeLast('.')

group = "dev.architectury"
version = "1.7.2-candle"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib", kotlinVersion))
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2023.3+")
    plugins.set(listOf("java", "Kotlin"))
    updateSinceUntilBuild.set(false)
}

tasks {
    jar {
        from("COPYING", "COPYING.LESSER")
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions {
            apiVersion = kotlinLanguageVersion
            languageVersion = kotlinLanguageVersion
        }
    }

    patchPluginXml {
        sinceBuild.set("221")
    }
}
