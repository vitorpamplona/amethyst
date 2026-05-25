
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.serialization)
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
        namespace = "com.vitorpamplona.amethyst.commons"
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

        withHostTest {}

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // iOS targets — Phase 2 spike. Compile-only for now (no framework binary
    // configured yet). Reveals which transitive deps need iOS variants and
    // which commonMain files still reach for platform-only APIs.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":quartz"))

                // Compose Multiplatform
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui.tooling.preview)

                // Lifecycle (KMP since 2.8.0). lifecycle-viewmodel and
                // lifecycle-runtime-compose ship iOS variants;
                // lifecycle-viewmodel-compose (the viewModel() Composable
                // helper) is Android-only and lives in jvmAndroid below.
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

                // JSON for custom-feed definitions (KMP — replaces Jackson
                // for the one commonMain serializer that was blocking iOS).
                implementation(libs.kotlinx.serialization.json)

                // Compose Multiplatform Resources
                implementation(libs.jetbrains.compose.components.resources)
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
                    // Audio-rooms ViewModel needs the listener orchestration +
                    // audio pipeline types (NestsListener, AudioRoomPlayer,
                    // AudioPlayer interface). The :nestsClient module is
                    // jvmAndroid-only today (its QUIC + Opus + AudioRecord/Track
                    // stacks are JVM-bound), so the dep lives here, not in
                    // commonMain. iOS will need an audio-rooms reroute when
                    // Phase 5 lands.
                    implementation(project(":nestsClient"))

                    // Coil's OkHttp network fetcher (JVM-only). iOS will use
                    // coil-ktor when the iOS Compose UI ships.
                    implementation(libs.coil.okhttp)

                    // Markdown rendering (richtext-commonmark). The single
                    // consumer (RenderMarkdown.kt) already lives in jvmAndroid.
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

                // Secure key storage via OS keychain (macOS/Windows/Linux)
                implementation(libs.java.keyring)

                // EXIF stripping for image uploads (used by service/upload/MediaCompressor).
                implementation(libs.commons.imaging)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Android-specific Compose tooling
                implementation(libs.androidx.ui.tooling.preview)

                // Secure key storage via Android Keystore
                implementation(libs.androidx.security.crypto.ktx)
                implementation(libs.androidx.datastore.preferences)
            }
        }

        // iOS intermediate so iosArm64Main and iosSimulatorArm64Main share code.
        val iosMain =
            create("iosMain") {
                dependsOn(commonMain.get())
            }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)

                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
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
    packageOfResClass = "com.vitorpamplona.amethyst.commons.resources"
    generateResClass = always
}

// iOS purity gate — same shape as :quartz:verifyKmpPurity. See the rationale
// there. Commons gains this gate once FeedDefinitionSerializer.kt has been
// migrated off Jackson; future commonMain code must not reintroduce JVM-only
// JSON / HTTP deps.
val verifyKmpPurity by tasks.registering {
    group = "verification"
    description = "Fails if iOS-targeted source sets import JVM-only deps."
    val checkedDirs =
        listOf(
            "src/commonMain", "src/commonTest",
            "src/appleMain", "src/appleTest",
            "src/nativeMain", "src/nativeTest",
            "src/iosMain", "src/iosTest",
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
        val forbidden = listOf("com.fasterxml.jackson", "okhttp3")
        val offenders =
            checkedDirs.flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines().withIndex().mapNotNull { (idx, line) ->
                            forbidden.firstOrNull { line.contains(it) }?.let { hit ->
                                "${file.relativeTo(rootDir)}:${idx + 1}: '$hit'"
                            }
                        }
                    }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "iOS-targeted source sets must not reference JVM-only deps " +
                    "(Jackson, OkHttp). Move the offending code to jvmAndroid/ " +
                    "or behind an expect/actual:\n  " + offenders.joinToString("\n  "),
            )
        }
    }
}

tasks.named("check").configure { dependsOn(verifyKmpPurity) }
