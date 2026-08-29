/*
 * The desktop implementation of the shared playback seam.
 *
 * Shared UI code talks to `androidx.media3.common.Player`; on Android that is
 * ExoPlayer, and here it is kdroidFilter's ComposeMediaPlayer (MIT), which
 * wraps Media Foundation on Windows, AVFoundation on macOS and GStreamer on
 * Linux. The desktop entry point installs this engine at startup via
 * `VideoEngine.installed`.
 *
 * It is a module of its own rather than part of :desktopApp so that either
 * desktop front end can install it, and so the engine can be tested without
 * building an application.
 */
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":androidxStubs"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)

    // See the note in :desktopApp — composemediaplayer leaks kotlinx-coroutines-test
    // as a runtime dependency in its published POM; it is test-only code that must
    // never reach a production classpath.
    implementation(libs.composemediaplayer) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
    }

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
