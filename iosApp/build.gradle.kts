// `:iosApp` is the iOS shim over the shared app (plans/2026-10-09-ios-app.md, B0/B1): it builds the
// AmethystKit framework that the Xcode project in `xcode/` links, and holds only what iOS alone has.
// Screens, navigation and the service classes come from :commons and :commonsUI.
//
// Kotlin/Native compiles this module's Kotlin on any host (`compileKotlinIosSimulatorArm64`), but
// linking the framework needs Xcode, so `link*FrameworkIos*` runs only on a Mac.

import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

// Arti for iOS: tools/arti-build/build-arti-ios.sh builds the Rust wrapper as two static
// archives (device, simulator) into tools/arti-build/out/ios, which git ignores: ~18 MB each,
// rebuilt from the pinned sources like Android's .so but not committed. Needs macOS, Xcode and
// rustup; Gradle runs it for whatever links Arti (the app, through Xcode's call of
// embedAndSignAppleFrameworkForXcode, and the test executables), and skips it while its inputs
// are unchanged. See tools/arti-build/README.md -> "iOS".
//
// The cinterop needs the Apple SDK, so the Arti bindings (the cinterop and src/iosArtiMain) are
// set up on a Mac only, and compiling :iosApp elsewhere keeps working without them
// (src/iosNoArtiMain stands in, with Tor unavailable).
val withArti = HostManager.hostIsMac
val artiBuildDir = rootProject.layout.projectDirectory.dir("tools/arti-build")
val artiIosOut = artiBuildDir.dir("out/ios")
val artiDeviceTarget = "aarch64-apple-ios"
val artiSimulatorTarget = "aarch64-apple-ios-sim"

// Xcode names the SDK it builds for in PLATFORM_NAME; then only that slice is built (each one
// is a ~70 s cold Rust build). Anywhere else (a terminal, the test tasks) both are.
val artiTargets: Provider<List<String>> =
    providers
        .environmentVariable("PLATFORM_NAME")
        .map {
            when (it) {
                "iphonesimulator" -> listOf(artiSimulatorTarget)
                "iphoneos" -> listOf(artiDeviceTarget)
                else -> listOf(artiDeviceTarget, artiSimulatorTarget)
            }
        }.orElse(listOf(artiDeviceTarget, artiSimulatorTarget))
val buildArtiIos =
    tasks.register<Exec>("buildArtiIos") {
        group = "build"
        description = "Builds Arti's iOS static libraries (tools/arti-build/build-arti-ios.sh)."
        workingDir(artiBuildDir)
        val targets = artiTargets.get()
        commandLine(listOf("./build-arti-ios.sh") + targets.map { "--target=$it" })
        inputs.property("targets", targets)
        inputs.dir(artiBuildDir.dir("src"))
        inputs.dir(artiBuildDir.dir("include"))
        inputs.files(
            artiBuildDir.files(
                "Cargo.toml",
                "Cargo.lock",
                "ARTI_VERSION",
                "IOS_XCODE_VERSION",
                "rust-toolchain.toml",
                "repro-env.sh",
                "build-arti-ios.sh",
            ),
        )
        targets.forEach { outputs.file(artiIosOut.file("$it/libarti.a")) }
    }

// The app: AmethystKit is a static framework, so Kotlin/Native leaves its `_arti_*` references
// for Xcode's link of the app, which takes the archive from tools/arti-build/out/ios/<triple>
// (LIBRARY_SEARCH_PATHS and -larti -liconv in xcode/project.yml). Xcode's pre-build step runs
// embedAndSignAppleFrameworkForXcode, so building the app builds Arti first.
if (withArti) {
    tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
        dependsOn(buildArtiIos)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "AmethystKit"
            isStatic = true
        }

        if (!withArti) return@forEach
        // Arti, Tor in Rust (plans/2026-11-25-ios-tor.md B12a): the C ABI of tools/arti-build.
        // The cinterop reads only the committed header, so compiling needs no Rust toolchain.
        target.compilations.getByName("main").cinterops.create("arti") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/arti.def"))
            includeDirs(artiBuildDir.dir("include"))
            packageName = "com.vitorpamplona.amethyst.ios.tor.cinterop"
        }
        // The test executables link the archive build-arti-ios.sh writes themselves; the app's
        // link is Xcode's (see embedAndSignAppleFrameworkForXcode above).
        val rustTarget = if (target.name == "iosArm64") artiDeviceTarget else artiSimulatorTarget
        target.binaries.withType<TestExecutable>().configureEach {
            // -liconv: the one system library in build-arti-ios.sh's native-static-libs
            // (-liconv -lSystem -lc -lm) that a Kotlin/Native link does not already pass.
            linkerOpts("-L${artiIosOut.dir(rustTarget).asFile.absolutePath}", "-larti", "-liconv")
            // The B12a ATS experiment: embed an Info.plist into the test executable (a bare binary
            // has none, and without one ATS lets plain http:// through).
            providers.gradleProperty("amethyst.ios.testInfoPlist").orNull?.let { plist ->
                linkerOpts("-sectcreate", "__TEXT", "__info_plist", plist)
            }
            // The archive as an input too, or a rebuilt libarti.a leaves the executable stale.
            linkTaskProvider.configure {
                dependsOn(buildArtiIos)
                inputs.file(artiIosOut.file("$rustTarget/libarti.a"))
            }
        }
    }

    sourceSets {
        val iosMain =
            create("iosMain") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(project(":commons"))
                    implementation(project(":commonsUI"))
                    implementation(libs.quartz)
                    implementation(libs.jetbrains.compose.runtime)
                    implementation(libs.jetbrains.compose.ui)
                    implementation(libs.androidx.lifecycle.viewmodel)
                    // The relay sockets and HTTP clients on NSURLSession (Apache-2.0, see :commons).
                    implementation(libs.ktor.client.core)
                    implementation(libs.ktor.client.websockets)
                    implementation(libs.ktor.client.darwin)
                    // Images: Coil over the same Ktor client. Apache-2.0, as the rest of Coil.
                    implementation(libs.coil.compose)
                    implementation(libs.coil.network.ktor3)
                }
            }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        iosMain.kotlin.srcDir(if (withArti) "src/iosArtiMain/kotlin" else "src/iosNoArtiMain/kotlin")

        // Simulator-only: the Arti spike tests (B12a) run on the simulator, never on a device.
        getByName("iosSimulatorArm64Test") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Arti's tests that need the Tor network (check.torproject.org, a public relay, an onion
// service) are off unless asked for, so a test run never depends on Tor being reachable:
//   ./gradlew :iosApp:iosSimulatorArm64Test -Pamethyst.arti.network=true
//
// HTTPS needs a booted simulator: in Kotlin/Native's default standalone mode every TLS handshake
// fails trust evaluation (-1200/-1202, a direct one too; measured 2026-11-25). Boot one and name it:
//   xcrun simctl boot <udid>
//   ./gradlew :iosApp:iosSimulatorArm64Test -Pamethyst.arti.network=true -Pamethyst.ios.testSimulator=<udid>
val artiNetworkTests = providers.gradleProperty("amethyst.arti.network").map { it.toBoolean() }.orElse(false)
val testSimulator = providers.gradleProperty("amethyst.ios.testSimulator")
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    if (!artiNetworkTests.get()) {
        filter.excludeTestsMatching("*TorNetworkSpikeTest*")
    }
    if (testSimulator.isPresent) {
        device.set(testSimulator)
        standalone.set(false)
    }
}

// The version the app shows, the same name Android's versionName carries (root build), with
// Android's "-DEBUG" suffix when Xcode builds the Debug configuration.
val generateIosBuildInfo =
    tasks.register("generateIosBuildInfo") {
        val versionName = rootProject.extra["appVersionName"] as String
        val debug = providers.environmentVariable("CONFIGURATION").orElse("Debug").map { it != "Release" }
        val outputDir = layout.buildDirectory.dir("generated/buildInfo/iosMain/kotlin")
        inputs.property("versionName", versionName)
        inputs.property("debug", debug)
        outputs.dir(outputDir)
        doLast {
            val name = versionName + if (debug.get()) "-DEBUG" else ""
            val file = outputDir.get().file("com/vitorpamplona/amethyst/ios/IosBuildInfo.kt").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                |package com.vitorpamplona.amethyst.ios
                |
                |/** Generated by :iosApp:generateIosBuildInfo from the root build's appVersionName. */
                |internal object IosBuildInfo {
                |    const val VERSION_NAME = "$name"
                |}
                |""".trimMargin(),
            )
        }
    }
kotlin.sourceSets.getByName("iosMain").kotlin.srcDir(generateIosBuildInfo)

// iOS purity gate — shared task, see gradle/kmp-purity.gradle.kts.
apply(from = rootProject.file("gradle/kmp-purity.gradle.kts"))
