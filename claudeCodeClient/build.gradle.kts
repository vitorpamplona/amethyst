import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// An unofficial Kotlin client for the Claude Code CLI: it starts the user's installed `claude`
// in stream-json mode and speaks its control protocol (permission prompts, interrupt, rename),
// and reads the CLI's own session files to list and replay sessions. It bundles nothing from
// Anthropic. Kept free of Amethyst and Nostr types so it can move to its own repository.
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
