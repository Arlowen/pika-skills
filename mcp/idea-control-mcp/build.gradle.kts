import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.pika.idea"
version = "0.1.7"

val defaultIdeaPath = "/Applications/IntelliJ IDEA.app"
val ideaPath = providers.gradleProperty("ideaPath").orElse(defaultIdeaPath)
val targetIdeaVersion = providers.gradleProperty("targetIdeaVersion").orNull

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (targetIdeaVersion == null) {
            local(ideaPath)
        } else {
            intellijIdea(targetIdeaVersion)
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = true
    projectName = "Pika-MCP"

    pluginConfiguration {
        name = "Pika MCP"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "253.*"
        }
    }

    pluginVerification {
        ides {
            local(ideaPath)
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2025.3.2")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("pika.mcp.port", "0")
    }
}
