import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// An unofficial Kotlin client for the Claude Code CLI: it starts the user's installed `claude`
// in stream-json mode and speaks its control protocol (permission prompts, interrupt, rename),
// and reads the CLI's own session files to list and replay sessions. It bundles nothing from
// Anthropic. Kept free of Amethyst and Nostr types so it can move to its own repository.
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.serialization)
}

// Built by the JDK Gradle runs on (JDK 25), as Java 21 bytecode, like the other modules: no
// separate JDK 21 toolchain to install.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(project(":agentCore"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
