import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.pika.idea"
version = "0.1.0"

val defaultIdeaPath = "/Applications/IntelliJ IDEA.app"
val defaultMcpPluginPath =
    "${System.getProperty("user.home")}/Library/Application Support/JetBrains/IntelliJIdea2024.2/plugins/mcp-server-plugin"
val ideaPath = providers.gradleProperty("ideaPath").orElse(defaultIdeaPath)
val mcpPluginPath = providers.gradleProperty("mcpPluginPath").orElse(defaultMcpPluginPath)

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(ideaPath)
        localPlugin(mcpPluginPath)
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

    pluginConfiguration {
        name = "Run and Changelist Control MCP"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "242.*"
        }
    }

    pluginVerification {
        ides {
            local(ideaPath)
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
