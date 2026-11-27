// `:iosApp` is the iOS shim over the shared app (plans/2026-10-09-ios-app.md, B0/B1): it builds the
// AmethystKit framework that the Xcode project in `xcode/` links, and holds only what iOS alone has.
// Screens, navigation and the service classes come from :commons and :commonsUI.
//
// Kotlin/Native compiles this module's Kotlin on any host (`compileKotlinIosSimulatorArm64`), but
// linking the framework needs Xcode, so `link*FrameworkIos*` runs only on a Mac.

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
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
// Checks, per archive: a static archive whose every member is an arm64 Mach-O object built for
// the slice's platform (LC_BUILD_VERSION / LC_VERSION_MIN_*: iOS for the device slice, the iOS
// simulator for the other, so swapped slices fail), whose symbol table exports every function
// arti_ffi.h declares, and which BUILD_INFO describes (its sha256), built from the sources
// tools/arti-build holds now (BUILD_INFO's `<triple>.inputs`, the digest below) with the pinned
// Xcode. So CI fails when a change to tools/arti-build lands without rebuilt archives.
// It streams the archives (headers and load commands only, plus one sha256 pass) and writes a
// stamp, so it is up to date, and cacheable, until one of its inputs changes.
//
// The input digest must stay identical to inputs_digest() in build-arti-ios.sh (README.md ->
// "iOS", same text): sha256 over one line per file, "<hex sha256>  <path>\n", for ARTI_VERSION
// Cargo.lock Cargo.toml IOS_XCODE_VERSION build-arti-ios.sh include/arti_ffi.h repro-env.sh
// rust-toolchain.toml and every src/*.rs, paths relative to tools/arti-build, sorted by path.
// A build followed by this task is the check that both agree: a mismatch fails here at once.
val artiInputNames =
    listOf(
        "ARTI_VERSION",
        "Cargo.lock",
        "Cargo.toml",
        "IOS_XCODE_VERSION",
        "build-arti-ios.sh",
        "include/arti_ffi.h",
        "repro-env.sh",
        "rust-toolchain.toml",
    )
val verifyArtiIosArchives =
    tasks.register("verifyArtiIosArchives") {
        group = "verification"
        description = "Checks the committed Arti archives in iosApp/libs/arti against their platform, exports and sources."
        // Mach-O platforms (<mach-o/loader.h>): PLATFORM_IOS = 2, PLATFORM_IOSSIMULATOR = 7.
        val slices = listOf(artiDeviceTarget to 2, artiSimulatorTarget to 7)
        val archives = slices.associate { (target, _) -> target to artiLibs.file("$target/libarti.a").asFile }
        val buildInfo = artiLibs.file("BUILD_INFO").asFile
        val sourceRoot = artiBuildDir.asFile
        // One property per archive, so swapping the two files changes the inputs.
        archives.forEach { (target, archive) ->
            inputs.files(archive).withPropertyName("archive-$target").withPathSensitivity(PathSensitivity.NONE)
        }
        inputs.files(buildInfo).withPropertyName("buildInfo").withPathSensitivity(PathSensitivity.NONE)
        inputs
            .files(artiBuildDir.asFileTree.matching { include(artiInputNames + "src/*.rs") })
            .withPropertyName("sources")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        val stamp = layout.buildDirectory.file("arti/verifyArtiIosArchives.stamp")
        outputs.file(stamp)
        outputs.cacheIf { true }
        doLast {
            val fix = "Rebuild with tools/arti-build/build-arti-ios.sh (README.md -> \"iOS\") and commit iosApp/libs/arti."

            fun fail(message: String): Nothing = throw GradleException("$message $fix")

            fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

            fun sha256(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                return hex(digest.digest())
            }

            val sourceFiles =
                (
                    artiInputNames +
                        (sourceRoot.resolve("src").listFiles().orEmpty())
                            .filter { it.isFile && it.name.endsWith(".rs") }
                            .map { "src/${it.name}" }
                ).sorted()
            val inputsDigest =
                hex(
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(sourceFiles.joinToString("") { "${sha256(sourceRoot.resolve(it))}  $it\n" }.toByteArray()),
                )

            val header = sourceRoot.resolve("include/arti_ffi.h")
            val declared = Regex("""\b(arti_[a-z_]+)\(""").findAll(header.readText()).map { it.groupValues[1] }.toSet()
            val platformNames = mapOf(1 to "macOS", 2 to "iOS", 3 to "tvOS", 4 to "watchOS", 6 to "Mac Catalyst", 7 to "iOS simulator")

            fun RandomAccessFile.bytesAt(
                offset: Long,
                count: Int,
            ): ByteBuffer {
                val bytes = ByteArray(count)
                seek(offset)
                readFully(bytes)
                return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            }

            slices.forEach { (target, platform) ->
                val archive = archives.getValue(target)
                if (!archive.isFile) fail("Missing $archive.")
                val platformsSeen = sortedMapOf<Int, Int>()
                var exported: Set<String>? = null
                RandomAccessFile(archive, "r").use { file ->
                    val length = file.length()
                    if (length < 8 || String(file.bytesAt(0, 8).array(), Charsets.ISO_8859_1) != "!<arch>\n") {
                        fail("$archive is not a static archive.")
                    }
                    var pos = 8L
                    while (pos + 60 <= length) {
                        // The 60-byte BSD ar member header; "#1/<n>" puts an n-byte name before the data.
                        val memberHeader = String(file.bytesAt(pos, 60).array(), Charsets.ISO_8859_1)
                        if (!memberHeader.endsWith("`\n")) fail("$archive: bad archive member header at $pos.")
                        val rawName = memberHeader.substring(0, 16).trimEnd()
                        val size = memberHeader.substring(48, 58).trim().toLong()
                        var dataStart = pos + 60
                        var dataSize = size
                        val name =
                            if (rawName.startsWith("#1/")) {
                                val nameLength = rawName.substring(3).toInt()
                                dataStart += nameLength
                                dataSize -= nameLength
                                String(file.bytesAt(pos + 60, nameLength).array(), Charsets.ISO_8859_1).trimEnd('\u0000')
                            } else {
                                rawName.removeSuffix("/")
                            }
                        if (name.startsWith("__.SYMDEF")) {
                            // ranlib table: size, (strx, offset) pairs, string table size, strings.
                            val wide = name.startsWith("__.SYMDEF_64")
                            val table = file.bytesAt(dataStart, dataSize.toInt())
                            val word = if (wide) 8 else 4
                            fun read(at: Int) = if (wide) table.getLong(at).toInt() else table.getInt(at)
                            val ranlibBytes = read(0)
                            val stringsStart = word + ranlibBytes + word
                            exported =
                                (0 until ranlibBytes / (2 * word))
                                    .map { i ->
                                        var end = stringsStart + read(word + i * 2 * word)
                                        val start = end
                                        while (table.get(end) != 0.toByte()) end++
                                        String(table.array(), start, end - start, Charsets.ISO_8859_1)
                                    }.toSet()
                        } else {
                            val machHeader = file.bytesAt(dataStart, 32)
                            // MH_MAGIC_64, CPU_TYPE_ARM64.
                            if (machHeader.getInt(0) != 0xfeedfacf.toInt() || machHeader.getInt(4) != 0x0100000c) {
                                fail("$archive: member $name is not an arm64 Mach-O object.")
                            }
                            val commandCount = machHeader.getInt(16)
                            val commands = file.bytesAt(dataStart + 32, machHeader.getInt(20))
                            var at = 0
                            var memberPlatform: Int? = null
                            repeat(commandCount) {
                                val command = commands.getInt(at)
                                memberPlatform =
                                    when (command) {
                                        0x32 -> commands.getInt(at + 8) // LC_BUILD_VERSION
                                        0x25 -> 2 // LC_VERSION_MIN_IPHONEOS
                                        0x24 -> 1 // LC_VERSION_MIN_MACOSX
                                        0x2F -> 3 // LC_VERSION_MIN_TVOS
                                        0x30 -> 4 // LC_VERSION_MIN_WATCHOS
                                        else -> memberPlatform
                                    }
                                at += commands.getInt(at + 4)
                            }
                            val found = memberPlatform ?: fail("$archive: member $name names no platform.")
                            platformsSeen[found] = (platformsSeen[found] ?: 0) + 1
                        }
                        pos = dataStart + dataSize
                        if (pos % 2 == 1L) pos++
                    }
                }
                if (platformsSeen.keys != setOf(platform)) {
                    val seen = platformsSeen.entries.joinToString { (p, n) -> "$n ${platformNames[p] ?: "platform $p"}" }
                    fail("$archive is the $target slice, so its objects must all be built for ${platformNames[platform]}; found $seen objects (swapped slices?).")
                }
                val symbols = exported ?: fail("$archive has no symbol table (__.SYMDEF).")
                val missing = declared.filterNot { "_$it" in symbols }
                if (missing.isNotEmpty()) fail("$archive does not export $missing.")
            }

            if (!buildInfo.isFile) fail("Missing $buildInfo.")
            val info =
                buildInfo
                    .readLines()
                    .mapNotNull { line -> line.indexOf(": ").takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 2) } }
                    .toMap()
            val xcodePin = sourceRoot.resolve("IOS_XCODE_VERSION").readText().filterNot { it.isWhitespace() }
            if (info["xcode"] != xcodePin) fail("$buildInfo says Xcode ${info["xcode"]} built the archives; IOS_XCODE_VERSION pins $xcodePin.")
            slices.forEach { (target, _) ->
                val archive = archives.getValue(target)
                val sha = sha256(archive)
                if (info[target] != sha) fail("$buildInfo records sha256 ${info[target]} for $target, but $archive is $sha.")
                val recorded = info["$target.inputs"]
                if (recorded != inputsDigest) {
                    fail(
                        "The $target archive was built from other sources than tools/arti-build holds now: BUILD_INFO " +
                            "records inputs $recorded, the sources digest to $inputsDigest (over $sourceFiles).",
                    )
                }
            }

            stamp.get().asFile.apply {
                parentFile.mkdirs()
                writeText(slices.joinToString("") { (target, _) -> "$target: ${info[target]}\n" } + "inputs: $inputsDigest\n")
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
