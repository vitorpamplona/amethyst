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

// Arti for iOS, the Android model: tools/arti-build/build-arti-ios.sh builds the Rust wrapper
// as two static archives (device, simulator), strips their debug info and writes them to
// libs/arti/<triple>/libarti.a, which are COMMITTED, as Android's jniLibs .so are. Building the
// app or the tests only reads them: no Rust toolchain needed. Whoever changes tools/arti-build
// reruns the script (and verify-reproducible-ios.sh) and commits the archives with the change.
// See tools/arti-build/README.md -> "iOS".
//
// The cinterop needs the Apple SDK, so the Arti bindings (the cinterop and src/iosArtiMain) are
// set up on a Mac only, and compiling :iosApp elsewhere keeps working without them
// (src/iosNoArtiMain stands in, with Tor unavailable).
val withArti = HostManager.hostIsMac
val artiBuildDir = rootProject.layout.projectDirectory.dir("tools/arti-build")
val artiLibs = layout.projectDirectory.dir("libs/arti")
val artiDeviceTarget = "aarch64-apple-ios"
val artiSimulatorTarget = "aarch64-apple-ios-sim"

// Like Android's verifyNativeAbis: a missing or wrong archive links into nothing useful, so fail
// the build that would link it, with the fix, rather than at Xcode's undefined-symbol errors.
val verifyArtiIosArchives =
    tasks.register("verifyArtiIosArchives") {
        group = "verification"
        description = "Checks the committed Arti archives in iosApp/libs/arti (arm64, every C export)."
        val archives = listOf(artiDeviceTarget, artiSimulatorTarget).map { artiLibs.file("$it/libarti.a").asFile }
        val header = artiBuildDir.file("include/arti_ffi.h").asFile
        inputs.files(archives + header)
        doLast {
            val declared = Regex("""\b(arti_[a-z_]+)\(""").findAll(header.readText()).map { it.groupValues[1] }.toSet()
            // MH_MAGIC_64 then CPU_TYPE_ARM64, little-endian: the first object member's header.
            val arm64MachO = "\u00cf\u00fa\u00ed\u00fe\u000c\u0000\u0000\u0001"
            archives.forEach { archive ->
                val fix = "Rebuild it: tools/arti-build/build-arti-ios.sh (README.md -> \"iOS\")."
                if (!archive.isFile) throw GradleException("Missing $archive. $fix")
                // ISO-8859-1 maps each byte to one char, so the archive can be searched as text.
                val bytes = archive.readText(Charsets.ISO_8859_1)
                if (!bytes.startsWith("!<arch>\n")) throw GradleException("$archive is not a static archive. $fix")
                if (!bytes.contains(arm64MachO)) throw GradleException("$archive holds no arm64 Mach-O object. $fix")
                // The symbol table (__.SYMDEF) names every exported function, NUL-terminated.
                val missing = declared.filterNot { bytes.contains("_$it\u0000") }
                if (missing.isNotEmpty()) throw GradleException("$archive does not export $missing. $fix")
            }
        }
    }

// The app: AmethystKit is a static framework, so Kotlin/Native leaves its `_arti_*` references
// for Xcode's link of the app, which takes the archive from libs/arti/<triple>
// (LIBRARY_SEARCH_PATHS and -larti -liconv in xcode/project.yml).
if (withArti) {
    tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
        dependsOn(verifyArtiIosArchives)
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
        // The test executables link the committed archive themselves; the app's link is Xcode's
        // (see embedAndSignAppleFrameworkForXcode above).
        val rustTarget = if (target.name == "iosArm64") artiDeviceTarget else artiSimulatorTarget
        target.binaries.withType<TestExecutable>().configureEach {
            // -liconv: the one system library in build-arti-ios.sh's native-static-libs
            // (-liconv -lSystem -lc -lm) that a Kotlin/Native link does not already pass.
            linkerOpts("-L${artiLibs.dir(rustTarget).asFile.absolutePath}", "-larti", "-liconv")
            // The B12a ATS experiment: embed an Info.plist into the test executable (a bare binary
            // has none, and without one ATS lets plain http:// through).
            providers.gradleProperty("amethyst.ios.testInfoPlist").orNull?.let { plist ->
                linkerOpts("-sectcreate", "__TEXT", "__info_plist", plist)
            }
            // The archive as an input too, or a rebuilt libarti.a leaves the executable stale.
            linkTaskProvider.configure {
                dependsOn(verifyArtiIosArchives)
                inputs.file(artiLibs.file("$rustTarget/libarti.a"))
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

// The napplet session tests load the real shared shell page, read from the checkout: a test
// executable on the simulator can read the Mac's files, and has no compose-resources bundle.
val generateIosTestFiles =
    tasks.register("generateIosTestFiles") {
        val shell = rootProject.file("commonsUI/src/commonMain/composeResources/files/napplet/shell.html").absolutePath
        val outputDir = layout.buildDirectory.dir("generated/testFiles/iosSimulatorArm64Test/kotlin")
        inputs.property("shell", shell)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("com/vitorpamplona/amethyst/ios/napplet/NappletTestFiles.kt").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                |package com.vitorpamplona.amethyst.ios.napplet
                |
                |/** Generated by :iosApp:generateIosTestFiles. */
                |internal object NappletTestFiles {
                |    const val SHELL_HTML = "$shell"
                |}
                |""".trimMargin(),
            )
        }
    }
kotlin.sourceSets.getByName("iosSimulatorArm64Test").kotlin.srcDir(generateIosTestFiles)

// iOS purity gate — shared task, see gradle/kmp-purity.gradle.kts.
apply(from = rootProject.file("gradle/kmp-purity.gradle.kts"))
