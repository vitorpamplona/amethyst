/*
 * JVM stand-ins for the Compose APIs that exist only in the Android artifacts.
 *
 * Same mechanism and wiring as :androidStubs — compileOnly on a shared source
 * set, implementation on the JVM target only — but for `androidx.compose.*`
 * rather than `android.*`. These declarations deliberately live in Compose's
 * own packages so that existing `import androidx.compose.ui.platform.LocalContext`
 * and `import androidx.compose.ui.res.stringResource` lines resolve unchanged:
 * from the Compose Android artifacts on Android, and from here on the JVM.
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
