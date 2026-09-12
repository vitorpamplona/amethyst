import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

// `:commonsUI` is the Compose half of the shared layer: every composable,
// icon, theme, Coil fetcher and Compose-resource (strings/fonts/files) that the
// GUI front ends (Android, Desktop, iOS) render. It sits on top of `:commons`
// (headless models, state, ViewModels, relay client) and is never a dependency
// of `:cli`, which keeps Compose UI + Skiko off the CLI classpath.
// Source files keep their `com.vitorpamplona.amethyst.commons.*` packages so
// the module boundary is purely a build-graph constraint — consumers did not
// have to change a single import when the split happened.

// Disables the Kotlin/Native compiler cache for an iOS test binary so the
// Compose ui-uikit klib recompiles fresh instead of linking the broken prebuilt
// cache (see the call site in the `kotlin {}` block). The version guard makes
// Kotlin re-surface this workaround once we move past 2.4.20, so it can be
// dropped when a newer Compose/Kotlin pairing fixes the cache. Wrapped in a
// helper because @OptIn only applies to declarations, not bare statements.
@OptIn(KotlinNativeCacheApi::class)
fun TestExecutable.disableUiKitPrebuiltCache() =
    disableNativeCache(
        DisableCacheInKotlinVersion.`2_4_20`,
        "Compose ui-uikit prebuilt cache references UIViewLayoutRegion (iOS 17+); " +
            "linking the iOS test binary fails under Xcode 16.4.",
    )

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.amethyst.commons.ui"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        androidResources.enable = true

        withHostTest {
            isReturnDefaultValues = true
        }

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // iOS targets — same compile-only spike as :commons.
    iosArm64()
    iosSimulatorArm64()

    // Compose Multiplatform 1.11.x ships an `org.jetbrains.compose.ui:ui-uikit`
    // prebuilt Kotlin/Native cache whose CMPLayoutRegion object hard-references
    // the UIKit class `UIViewLayoutRegion` (introduced in iOS 17). Linking the
    // iOS *test* executable against that cache under Xcode 16.4 fails with
    //   ld: Undefined symbols: _OBJC_CLASS_$_UIViewLayoutRegion
    // because the cached object was built for a newer simulator SDK (18.5) than
    // the test binary is being linked for (14.0). Disabling the native cache for
    // the iOS test binaries makes ui-uikit recompile against the active SDK,
    // where the symbol resolves. See disableUiKitPrebuiltCache() above and
    // https://kotl.in/disable-native-cache
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<TestExecutable>().configureEach {
            disableUiKitPrebuiltCache()
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // The headless half. `api` because every composable here takes
                // or returns commons types (Note, User, ViewModels, states).
                api(project(":commons"))
                api(project(":quartz"))

                // Compose Multiplatform
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui.tooling.preview)

                // Lifecycle (KMP since 2.8.0). lifecycle-runtime-compose ships
                // iOS variants; lifecycle-viewmodel-compose (the viewModel()
                // Composable helper) is Android-only and lives in jvmAndroid.
                implementation(libs.androidx.lifecycle.viewmodel)
                implementation(libs.androidx.lifecycle.runtime.compose)

                // Image loading (Coil 3 - KMP). The okhttp network fetcher is
                // JVM-only and lives in jvmAndroid; iOS will pull coil-ktor
                // when that target wires its actual.
                implementation(libs.coil.compose)

                // LruCache (KMP-ready)
                implementation(libs.androidx.collection)

                // Immutable collections
                api(libs.kotlinx.collections.immutable)

                // Compose Multiplatform Resources (strings, fonts, napplet shell files)
                implementation(libs.jetbrains.compose.components.resources)

                // KMP syntax highlighter (Apache-2.0) for the git code browser.
                implementation(libs.highlights)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Shared JVM code for both Android and Desktop
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    // Coil's OkHttp network fetcher (JVM-only). iOS will use
                    // coil-ktor when the iOS Compose UI ships.
                    implementation(libs.coil.okhttp)

                    // OkHttp for the Blossom read-auth Coil fetcher.
                    implementation(libs.okhttp)

                    // Markdown rendering (richtext-commonmark). The single
                    // consumer (RenderMarkdown.kt) lives in jvmAndroid.
                    // iOS support pending Phase 3 markdown decision.
                    implementation(libs.markdown.commonmark)
                    implementation(libs.markdown.ui)
                    implementation(libs.markdown.ui.material3)

                    // viewModel() Compose helper. AndroidX publishes this
                    // artifact for android/jvmStubs/linuxx64Stubs but not iOS,
                    // so it stays in jvmAndroid until we either swap to the
                    // org.jetbrains.androidx.lifecycle variant or accept a
                    // platform-specific ViewModel access pattern on iOS.
                    implementation(libs.androidx.lifecycle.viewmodel.compose)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Desktop-specific Compose
                implementation(compose.desktop.currentOs)
                implementation(libs.jetbrains.compose.ui.tooling)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Android-specific Compose tooling
                implementation(libs.androidx.ui.tooling.preview)
            }
        }

        // iOS intermediate so iosArm64Main and iosSimulatorArm64Main share code.
        val iosMain =
            create("iosMain") {
                dependsOn(commonMain.get())
            }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        // Skiko-backed targets (desktop JVM + iOS) share pixel-format helpers
        // (org.jetbrains.skia.* resolves on both through Compose). Android is
        // deliberately NOT in this set — it renders through android.graphics.
        val skikoMain =
            create("skikoMain") {
                dependsOn(commonMain.get())
            }
        getByName("jvmMain").dependsOn(skikoMain)
        iosMain.dependsOn(skikoMain)

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    // Kept on the pre-split package so `Res` imports in every consumer
    // (amethyst, desktopApp, nappletHost) keep resolving unchanged.
    packageOfResClass = "com.vitorpamplona.amethyst.commons.resources"
    generateResClass = always
}

// iOS purity gate — same shape as :quartz / :commons verifyKmpPurity.
// commonMain here must stay free of JVM-only JSON / HTTP deps.
val verifyKmpPurity by tasks.registering {
    group = "verification"
    description = "Fails if iOS-targeted source sets import JVM-only deps."
    val checkedDirs =
        listOf(
            "src/commonMain", "src/commonTest",
            "src/appleMain", "src/appleTest",
            "src/nativeMain", "src/nativeTest",
            "src/iosMain", "src/iosTest",
            "src/skikoMain", "src/skikoTest",
            "src/iosArm64Main", "src/iosArm64Test",
            "src/iosSimulatorArm64Main", "src/iosSimulatorArm64Test",
            "src/linuxMain", "src/linuxTest",
            "src/linuxX64Main", "src/linuxX64Test",
            "src/macosMain", "src/macosTest",
            "src/macosArm64Main", "src/macosArm64Test",
        ).map { layout.projectDirectory.dir(it).asFile }
            .filter { it.exists() }
    inputs.files(checkedDirs)
    doLast {
        // Each pattern is paired with a short hint so the failure message
        // points at the canonical KMP replacement.
        val forbidden =
            listOf(
                "com.fasterxml.jackson" to "Jackson is JVM-only — use kotlinx.serialization",
                "okhttp3" to "OkHttp is JVM-only — wrap behind expect/actual or use Ktor on iOS",
                "System.currentTimeMillis" to "use TimeUtils.now()",
                "Thread.sleep" to "use kotlinx.coroutines.delay or platform-specific actual",
                "java.util.UUID" to "use kotlin.uuid.Uuid",
                "kotlin.jvm.Synchronized" to "use KmpLock.withLock {}",
                // The bare call, not just the annotation: `synchronized(lock) {}` resolves
                // from kotlin-stdlib-jvm with no import, so it compiles on Android/JVM and
                // only fails at the iOS compile step. Catch it here instead.
                "synchronized(" to "`synchronized` is JVM-only — use KmpLock.withLock {}",
                "kotlin.jvm.Volatile" to "use kotlin.concurrent.Volatile",
            )
        val offenders =
            checkedDirs.flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines().withIndex().mapNotNull { (idx, line) ->
                            val trimmed = line.trimStart()
                            // Skip KDoc / line-comment lines — those legitimately
                            // mention forbidden names (migration notes, doc refs).
                            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                                return@mapNotNull null
                            }
                            forbidden.firstOrNull { (pattern, _) -> line.contains(pattern) }?.let { (hit, hint) ->
                                "${file.relativeTo(rootDir)}:${idx + 1}: '$hit' — $hint"
                            }
                        }
                    }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "iOS-targeted source sets must not reference JVM-only APIs. " +
                    "Move the offending code to jvmAndroid/ or behind an expect/actual:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }
}

tasks.named("check").configure { dependsOn(verifyKmpPurity) }
