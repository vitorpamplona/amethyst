/*
 * JVM stand-ins for the `androidx.*` APIs that only ship in Android artifacts.
 *
 * Same mechanism and wiring as :androidStubs — compileOnly on a shared source
 * set, implementation on the JVM target only — but for `androidx.*` rather than
 * `android.*`. Declarations deliberately live in the real androidx packages, so
 * that existing imports resolve unchanged: from the Android artifacts on
 * Android, and from here on the JVM.
 *
 * Covers Compose's id-taking resource accessors and LocalContext, plus the
 * androidx.core KTX helpers (toUri, ContextCompat, NotificationCompat) that the
 * app leans on. Unlike :androidStubs this is Kotlin, because most of what it
 * replaces are extension functions and composables rather than classes.
 */
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(project(":androidStubs"))
    api(project(":amethystShared"))
    implementation(compose.desktop.currentOs)
}
