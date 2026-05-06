import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.nestsclient"
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

        withHostTest {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(project(":quartz"))
                implementation(project(":quic"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
        }

        androidMain {
            dependsOn(jvmAndroid)
            // Kwik QUIC + Flupke HTTP/3 dependencies are NOT yet declared.
            // See KwikWebTransportFactory.kt for the integration plan and
            // validated Maven coordinates / minimum versions before adding.
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }
    }
}

// Forward the nostrnests interop opt-in property from the Gradle JVM
// to test workers. Without this, `-DnestsInterop=true` on the Gradle
// command line never reaches `NostrNestsHarness.isEnabled()` (which
// reads it via `System.getProperty`), so every interop test silently
// skips. See `nestsClient/src/jvmTest/.../interop/NostrNestsHarness.kt`.
tasks.withType<Test>().configureEach {
    System.getProperty("nestsInterop")?.let { systemProperty("nestsInterop", it) }
    System.getProperty("nestsInteropRev")?.let { systemProperty("nestsInteropRev", it) }
    System.getProperty("nestsInteropMoqRev")?.let { systemProperty("nestsInteropMoqRev", it) }
    System.getProperty("nestsInteropExternal")?.let { systemProperty("nestsInteropExternal", it) }
    System.getProperty("nestsInteropDebug")?.let { systemProperty("nestsInteropDebug", it) }
    // Opt-in for tests that hit the real nostrnests.com infrastructure
    // (see NostrnestsProdAudioTransmissionTest). Forwarded the same way
    // the harness flags are.
    System.getProperty("nestsProd")?.let { systemProperty("nestsProd", it) }
    System.getProperty("nestsProdEndpoint")?.let { systemProperty("nestsProdEndpoint", it) }
    System.getProperty("nestsProdAuth")?.let { systemProperty("nestsProdAuth", it) }
    // Cross-stack interop (Hang/Rust) opt-in. Forwarded the same way as
    // -DnestsInterop. See nestsClient/plans/2026-05-06-cross-stack-interop-test.md.
    System.getProperty("nestsHangInterop")?.let { systemProperty("nestsHangInterop", it) }
}

// ---- Cross-stack interop: Rust sidecar build + binary path forwarding -------
//
// Phase 1 of the interop plan ships the workspace at `cli/hang-interop/`
// with three stub binaries (hang-listen, hang-publish, udp-loss-shim).
// `interopBuildHangSidecars` runs `cargo build --release` against it and
// resolves the upstream `moq-relay` + `moq-token` binaries via
// `cargo install`, caching everything under
// `~/.cache/amethyst-nests-interop/hang-interop-cargo/` so reruns are
// fast. Binary paths are forwarded to test workers as system properties.
//
// Opt-in only: Phase 1 just verifies the harness can boot a relay; the
// actual interop scenarios land in Phase 2 once `hang-listen` /
// `hang-publish` have real subscribe/publish loops. See
// `nestsClient/plans/2026-05-06-cross-stack-interop-test.md` for the
// full plan and the pinned upstream versions in `cli/hang-interop/REV`.

val hangInteropDir = rootProject.layout.projectDirectory.dir("cli/hang-interop")
val hangInteropCacheDir =
    layout.projectDirectory
        .dir(System.getProperty("user.home") ?: "/tmp")
        .dir(".cache/amethyst-nests-interop/hang-interop-cargo")

// Versions are duplicated from cli/hang-interop/REV so Gradle has them
// at configuration time; bumping requires touching both files.
val moqRelayVersion = "0.10.25"
val moqTokenCliVersion = "0.5.23"

val interopInstallMoqRelay by tasks.registering(Exec::class) {
    description = "cargo install moq-relay $moqRelayVersion (interop)"
    group = "interop"
    commandLine(
        "cargo", "install",
        "moq-relay",
        "--version", moqRelayVersion,
        "--root", hangInteropCacheDir.asFile.absolutePath,
        "--locked",
    )
    val installed =
        hangInteropCacheDir.dir("bin").file(
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) "moq-relay.exe" else "moq-relay",
        )
    outputs.file(installed)
    outputs.cacheIf { true }
    onlyIf { !installed.asFile.exists() }
    doFirst { hangInteropCacheDir.asFile.mkdirs() }
}

val interopInstallMoqTokenCli by tasks.registering(Exec::class) {
    description = "cargo install moq-token-cli $moqTokenCliVersion (interop)"
    group = "interop"
    commandLine(
        "cargo", "install",
        "moq-token-cli",
        "--version", moqTokenCliVersion,
        "--root", hangInteropCacheDir.asFile.absolutePath,
        "--locked",
    )
    val installed =
        hangInteropCacheDir.dir("bin").file(
            if (org.gradle.internal.os.OperatingSystem.current().isWindows) "moq-token-cli.exe" else "moq-token-cli",
        )
    outputs.file(installed)
    outputs.cacheIf { true }
    onlyIf { !installed.asFile.exists() }
    doFirst { hangInteropCacheDir.asFile.mkdirs() }
}

val interopBuildSidecars by tasks.registering(Exec::class) {
    description = "cargo build --release for cli/hang-interop sidecars"
    group = "interop"
    workingDir = hangInteropDir.asFile
    commandLine("cargo", "build", "--release")
    // Track only manifests + sources; the `target/` subtree is the
    // output, including it as an input would mark the task always
    // out-of-date.
    val sidecarSources =
        fileTree(hangInteropDir.asFile) {
            include("Cargo.toml", "Cargo.lock")
            include("hang-listen/**", "hang-publish/**", "udp-loss-shim/**")
            exclude("**/target/**")
        }
    inputs.files(sidecarSources)
    outputs.dir(hangInteropDir.dir("target/release"))
}

val interopBuildHangSidecars by tasks.registering {
    description = "Build all hang-interop binaries (sidecars + moq-relay + moq-token)."
    group = "interop"
    dependsOn(interopBuildSidecars, interopInstallMoqRelay, interopInstallMoqTokenCli)
}

tasks.withType<Test>().configureEach {
    val isHangInterop = System.getProperty("nestsHangInterop") == "true"
    if (isHangInterop) {
        dependsOn(interopBuildHangSidecars)
    }
    val sidecarRelease = hangInteropDir.dir("target/release").asFile
    val cargoBin = hangInteropCacheDir.dir("bin").asFile
    systemProperty("nestsHangInteropSidecarsDir", sidecarRelease.absolutePath)
    systemProperty("nestsHangInteropCargoBinDir", cargoBin.absolutePath)
}
