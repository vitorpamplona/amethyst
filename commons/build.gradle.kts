import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// `:commons` is the HEADLESS half of the shared layer: domain models, state
// holders, ViewModels, the relay client, services. It is consumed by every
// front end including the headless `:cli`, so it must never depend on Compose
// UI (ui / foundation / material3), Coil, Compose resources or Skiko — those
// live in `:commonsUI`, which sits on top of this module. Only the Compose
// *runtime* (@Stable/@Immutable + snapshot state) is allowed here.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    // Kept on purpose even though no @Composable lives here anymore: the
    // Compose compiler stamps @StabilityInferred on every class it compiles,
    // which is what lets the apps' composables treat commons models (Note,
    // User, states) as stable/skippable. Dropping it would silently make all
    // of them "unstable" from the UI's point of view.
    alias(libs.plugins.jetbrainsComposeCompiler)
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

        withHostTest {
            isReturnDefaultValues = true
        }

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

                // Compose *runtime* only — @Stable/@Immutable annotations and
                // snapshot state (mutableStateOf, State) used by state holders.
                // No ui / foundation / material3 here: that is :commonsUI.
                implementation(libs.jetbrains.compose.runtime)

                // Lifecycle ViewModel (KMP since 2.8.0, ships iOS variants).
                // The Compose-side helpers (lifecycle-runtime-compose,
                // viewModel()) live in :commonsUI.
                implementation(libs.androidx.lifecycle.viewmodel)

                // LruCache (KMP-ready)
                implementation(libs.androidx.collection)

                // Immutable collections
                api(libs.kotlinx.collections.immutable)

                // JSON for custom-feed definitions (KMP — replaces Jackson
                // for the one commonMain serializer that was blocking iOS).
                implementation(libs.kotlinx.serialization.json)
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

                    // OkHttp (+ coroutines bridge) for the link-preview fetcher
                    // (service/preview/UrlPreview). JVM-only; iOS will swap to
                    // Ktor when its UI ships.
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Secure key storage via OS keychain (macOS/Windows/Linux)
                implementation(libs.java.keyring)

                // EXIF stripping for image uploads (used by service/upload/MediaCompressor).
                implementation(libs.commons.imaging)

                // Image re-encode + progressive downscale (used by service/upload/ImageReencoder).
                // Pure-Java, MIT. See docs/plans/2026-06-08-feat-desktop-image-compression-plan.md.
                implementation(libs.thumbnailator)

                // Native OS notification bridges — Nucleus per-OS JNI shims.
                // macOS: UNUserNotificationCenter. Windows: WinRT Toasts. Linux: freedesktop D-Bus.
                // Only the matching-OS module's native lib loads at runtime; the others
                // stay dormant on the classpath.
                implementation("io.github.kdroidfilter:nucleus.notification-macos:1.15.7")
                implementation("io.github.kdroidfilter:nucleus.notification-windows:1.15.7")
                implementation("io.github.kdroidfilter:nucleus.notification-linux:1.15.7")
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // androidx.core KTX (Bitmap.scale, prefs.edit {}) used by the
                // Android actuals. Was reaching us transitively through the
                // Compose UI artifacts before the :commonsUI split.
                implementation(libs.androidx.core.ktx)

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

        // jvmTest needs the JVM secp256k1 bindings whenever a test
        // exercises a real signer (e.g. UploadOrchestratorTest, which
        // signs Blossom auth events end-to-end).
        getByName("jvmTest") {
            dependencies {
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

// JVM tests run AWT-backed code (ImageIO, Thumbnailator, BufferedImage) — pin
// headless mode so a stray Toolkit.getDefaultToolkit() in a transitive dep
// never bounces the macOS Dock during CI/local test runs.
tasks.withType<Test>().configureEach {
    if (name == "jvmTest") {
        jvmArgs("-Djava.awt.headless=true")
    }
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
