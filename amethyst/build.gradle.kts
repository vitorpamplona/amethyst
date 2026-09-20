import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.androidxBaselineProfile)
}

fun getCurrentBranch(workingDir: java.io.File): String =
    try {
        val process =
            ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
        val branch =
            process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()
        val exitCode = process.waitFor()
        if (exitCode != 0) "unknown" else branch
    } catch (e: Exception) {
        println("Could not determine git branch: ${e.message}")
        "unknown"
    }

fun generateVersionName(
    baseVersion: String,
    workingDir: java.io.File,
): String {
    val currentBranch = getCurrentBranch(workingDir)

    if (currentBranch == "main" || currentBranch == "master" || currentBranch == "unknown" || currentBranch == "HEAD") {
        return baseVersion
    }

    // Clean branch name for version (replace special characters)
    var cleanBranch = currentBranch.replace(Regex("[^a-zA-Z0-9\\-_]"), "-")

    // Limit branch name to maximum 20 characters
    if (cleanBranch.length > 20) {
        cleanBranch = cleanBranch.substring(0, 20)
    }

    return "$baseVersion-$cleanBranch"
}

// Workaround: stability.analyzer plugin doesn't declare task dependencies properly for Gradle 9.x
afterEvaluate {
    val stabilityNames = tasks.names.filter { it.contains("StabilityCheck") }
    val compileNames = tasks.names.filter { it.matches(Regex("compile.*UnitTestKotlin")) }
    stabilityNames.forEach { scName ->
        compileNames.forEach { ctName ->
            tasks.named(scName).configure { mustRunAfter(tasks.named(ctName)) }
        }
    }
}

// Every ABI we split the APK for, and therefore every ABI that needs its own copy of each
// library we build and commit ourselves under src/main/jniLibs/. The lists drifted once: the
// splits shipped four ABIs while Arti was built for two, so the armeabi-v7a and x86 APKs
// installed and ran with the dependencies' native libraries all present (secp256k1's JNI ships
// every ABI) and Tor alone dead for the life of the install. `verifyNativeAbis` below keeps
// them in step.
val shippedAbis = listOf("x86", "x86_64", "arm64-v8a", "armeabi-v7a")

// The libraries that guard covers, and how to rebuild one when it is missing. Both are built
// from source by tools/ rather than pulled prebuilt, so both can go missing the same way — and
// a QR scanner that cannot load is as silently broken on that install as a dead Tor.
val committedNativeLibs =
    mapOf(
        "libarti_android.so" to { abi: String, triple: String ->
            "./tools/arti-build/build-arti.sh --target=$triple"
        },
        "libzxingcpp_android.so" to { abi: String, _: String ->
            "./tools/zxing-cpp-build/build-zxingcpp.sh --abi $abi"
        },
    )

android {
    namespace = "com.vitorpamplona.amethyst"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    // Packaging toolchain: AGP runs the NDK's llvm-strip over everything that
    // lands in jniLibs — our committed libarti_android.so included — so the
    // NDK revision is a build input for the APK, not just for whoever compiles
    // Arti. Left unset it silently follows AGP's own default (28.2.13676358 on
    // AGP 9.4.0), which moves with every AGP bump and is a different toolchain
    // from the one that produced the .so, while a machine with no NDK at all
    // packages the library unstripped ("Unable to strip the following
    // libraries") — three different APKs from the same source, which is
    // exactly what F-Droid's rebuild verification cannot have.
    //
    // Read straight from the pin rather than copied into the version catalog:
    // the two can then never drift, and bumping ANDROID_NDK_VERSION (which also
    // means rebuilding the .so files) moves the packaging toolchain with it.
    // That one file is the repo's only NDK pin -- tools/arti-build/build-arti.sh
    // and tools/zxing-cpp-build/build-zxingcpp.sh read it too, so every
    // committed .so is produced and stripped by the same revision. It lives
    // under tools/arti-build for history; it is not Arti's alone. See
    // tools/arti-build/README.md → "Reproducible builds".
    ndkVersion =
        providers
            .fileContents(layout.settingsDirectory.file("tools/arti-build/ANDROID_NDK_VERSION"))
            .asText
            .orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("tools/arti-build/ANDROID_NDK_VERSION is missing or empty — it pins the NDK that strips src/main/jniLibs")

    defaultConfig {
        applicationId = "com.vitorpamplona.amethyst"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode =
            libs.versions.appCode
                .get()
                .toInt()
        versionName = generateVersionName(libs.versions.app.get(), rootDir)
        buildConfigField("String", "RELEASE_NOTES_ID", "\"f7914e7a7e293988485439eb2bea29c09c388d54c452c4a19f89e106dbf1969e\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    androidResources {
        localeFilters +=
            listOf(
                "ar",
                "ar-rSA",
                "bn-rBD",
                "cs",
                "cs-rCZ",
                "cy-rGB",
                "da-rDK",
                "de",
                "de-rDE",
                "el-rGR",
                "en-rGB",
                "eo",
                "eo-rUY",
                "es",
                "es-rES",
                "es-rMX",
                "es-rUS",
                "et-rEE",
                "fa",
                "fa-rIR",
                "fi-rFI",
                "fo-rFO",
                "fr",
                "fr-rCA",
                "fr-rFR",
                "gu-rIN",
                "hi-rIN",
                "hr-rHR",
                "hu",
                "hu-rHU",
                "in",
                "in-rID",
                "it-rIT",
                "iw-rIL",
                "ja",
                "ja-rJP",
                "kk-rKZ",
                "ko-rKR",
                "ks-rIN",
                "ku-rTR",
                "lt-rLT",
                "ne-rNP",
                "nl",
                "nl-rBE",
                "nl-rNL",
                "pcm-rNG",
                "pl-rPL",
                "pt-rBR",
                "pt-rPT",
                "ru",
                "ru-rRU",
                "ru-rUA",
                "sa-rIN",
                "sl-rSI",
                "so-rSO",
                "sr-rSP",
                "ss-rZA",
                "sv-rSE",
                "sw-rKE",
                "sw-rTZ",
                "ta",
                "ta-rIN",
                "th",
                "th-rTH",
                "tr",
                "tr-rTR",
                "uk",
                "uk-rUA",
                "ur-rIN",
                "uz-rUZ",
                "vi-rVN",
                "zh",
                "zh-rCN",
                "zh-rHK",
                "zh-rSG",
                "zh-rTW",
            )
    }

    // Opt-in fast-build flags. Default behavior is unchanged.
    //
    //   -PdisableAbiSplits=true       skip per-ABI APK splits; produces a single
    //                                 APK per (flavor, buildType) instead of 5.
    //                                 Cuts ~600 MB of intermediates and several
    //                                 minutes off CI.
    //   -PdisableUniversalApk=true    when ABI splits are enabled, skip the
    //                                 extra universal APK output. (No effect
    //                                 when disableAbiSplits is also set, since
    //                                 there are no splits to add to.)
    //   -Pamethyst.skipMapping=true   disable R8 minification on release and
    //                                 benchmark. APK is larger, but builds are
    //                                 much faster and outputs/mapping/ (~260MB)
    //                                 is not produced. Local-dev and PR-CI use
    //                                 only — release pipelines must not set it.
    val disableAbiSplits =
        providers
            .gradleProperty("disableAbiSplits")
            .map { it.toBoolean() }
            .getOrElse(false)
    val disableUniversalApk =
        providers
            .gradleProperty("disableUniversalApk")
            .map { it.toBoolean() }
            .getOrElse(false)
    val skipMapping =
        providers
            .gradleProperty("amethyst.skipMapping")
            .map { it.toBoolean() }
            .getOrElse(false)

    buildTypes {
        getByName("release") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = !skipMapping
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "@string/app_name_debug")
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-BENCHMARK"
            resValue("string", "app_name", "@string/app_name_benchmark")
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // TODO: remove this when lightcompressor uses one MP4 parser only
    packaging {
        resources {
            pickFirsts.add("builddef.lst")
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")
        }
    }

    flavorDimensions += "channel"

    productFlavors {
        create("play") {
            isDefault = true
            dimension = "channel"
            buildConfigField("boolean", "IS_CASTING_AVAILABLE", "true")
        }

        create("fdroid") {
            dimension = "channel"
            buildConfigField("boolean", "IS_CASTING_AVAILABLE", "false")
        }
    }

    splits {
        abi {
            isEnable = !disableAbiSplits
            reset()
            include(*shippedAbis.toTypedArray())
            isUniversalApk = !disableUniversalApk
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // Reproducible builds: keep AGP from embedding the dependency-metadata blob
    // in the APK/AAB. That blob is a protobuf of the resolved dependency tree
    // encrypted with a Google public key; the ciphertext is non-deterministic,
    // so its presence makes every release artifact impossible to reproduce
    // bit-for-bit. Dropping it (F-Droid's documented recommendation) lets
    // F-Droid / Zapstore independently rebuild and verify our developer-signed
    // APKs.
    //
    // Play-channel trade-off: with includeInBundle = false the uploaded .aab no
    // longer carries this metadata, so Play Console's app-dependency insights /
    // known-vulnerability SDK alerts go unpopulated. Uploads still succeed; only
    // that advisory feature is lost.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "**/libscrypt.dylib")
        }

        jniLibs {
            // Reproducible builds, part two: ship the Arti library exactly as
            // tools/arti-build produced it. Its Cargo release profile already
            // strips it (no .symtab, no .debug_*), so AGP's
            // `llvm-strip --strip-unneeded` pass has nothing left to remove — but it
            // still rewrites the file: llvm-strip rebuilds .comment, the section that
            // records the rustc / clang / lld version stamps, which measurably changes
            // 273 bytes on arm64-v8a. That made the packaged bytes a function of
            // whichever NDK did the stripping, so the .so in an APK could never be
            // compared against the committed, independently reproducible one.
            // Excluding it from the strip step costs nothing in size (there are no
            // symbols to drop) and makes that comparison exact. Dependency .so files
            // are still stripped, with the NDK pinned by ndkVersion above.
            keepDebugSymbols += "**/libarti_android.so"

            // Same guarantee for the QR decoder, for a different reason. Unlike Arti's, this
            // library is *not* currently rewritten by AGP's pass -- verified by running the
            // pinned NDK's `llvm-strip --strip-unneeded` over the committed file and getting
            // identical bytes -- because tools/zxing-cpp-build strips it with that very same
            // llvm-strip, which makes a second pass idempotent. That idempotence is a property
            // of one NDK revision, though, and reading it back from the APK should not depend
            // on a strip pass staying a no-op across bumps. Excluding it makes
            // `unzip -p app.apk lib/<abi>/libzxingcpp_android.so | sha256sum` match
            // src/main/jniLibs by construction, at no size cost.
            keepDebugSymbols += "**/libzxingcpp_android.so"
        }
    }

    lint {
        disable += "MissingTranslation"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Lets TorArtiNativeIntegrationTest's System.loadLibrary("arti_android")
        // find the desktop-host build of our Arti JNI shim. The Android .so
        // variants live in src/main/jniLibs/<abi>/ — one per ABI in [shippedAbis]
        // — and are loaded on-device; this Linux x86_64 .so is just for JVM
        // unit-test runs.
        // -Pamethyst.arti.integration=true opts the (slow, network-dependent)
        // tests in; see TorArtiNativeIntegrationTest.kdoc.
        unitTests.all { test ->
            test.systemProperty(
                "java.library.path",
                "$projectDir/src/test/native-libs/x86_64-linux",
            )
            project
                .findProperty("amethyst.arti.integration")
                ?.let { test.systemProperty("amethyst.arti.integration", it.toString()) }
            // Opts QrCorpusBaselineTest into rewriting the QR decode corpus under
            // src/androidTest/assets/qr. Off by default so an ordinary run never dirties
            // the working tree; Gradle forks the test JVM, so -D alone would not reach it.
            project
                .findProperty("amethyst.qr.corpus.export")
                ?.let { test.systemProperty("amethyst.qr.corpus.export", it.toString()) }
        }
    }
}

// Every ABI split must carry Arti, or it ships an APK that is whole except for
// Tor. Nothing else catches that: AGP happily assembles a split out of whatever
// .so files the dependencies provide, the APK installs and runs, and the gap
// only surfaces at System.loadLibrary time on a user's device — where
// TorManager's flow swallows the UnsatisfiedLinkError and leaves the status Off
// forever. Checked at build time instead, against the same list the splits use.
val verifyNativeAbis =
    tasks.register("verifyNativeAbis") {
        group = "verification"
        description = "Checks that every ABI in the APK splits has each committed native library, built for that architecture."

        val jniLibs = file("src/main/jniLibs")
        val abis = shippedAbis
        val libs = committedNativeLibs
        // Per ABI: the Rust target triple (so an Arti failure names the exact build command) and
        // the ELF identity every library must have — 32/64-bit class (header byte 4) and
        // e_machine (bytes 18-19, little-endian on every Android ABI we ship). Existence alone is
        // not enough: a truncated file, an empty placeholder, or arm64's .so copied into x86/ all
        // load as nothing on device, which is the same silent failure this task exists to prevent
        // — and unlike a missing file, those look fine in git.
        val expected =
            mapOf(
                "arm64-v8a" to Triple("aarch64-linux-android", 2, 0xB7),
                "x86_64" to Triple("x86_64-linux-android", 2, 0x3E),
                "armeabi-v7a" to Triple("armv7-linux-androideabi", 1, 0x28),
                "x86" to Triple("i686-linux-android", 1, 0x03),
            )

        doLast {
            val bitness = mapOf(1 to "32-bit", 2 to "64-bit")
            val problems = mutableListOf<Triple<String, String, String>>()

            libs.keys.forEach { libName ->
                abis.forEach { abi ->
                    val lib = File(jniLibs, "$abi/$libName")
                    val want = expected[abi]
                    val header = ByteArray(20)
                    val read = if (lib.isFile) lib.inputStream().use { it.read(header) } else -1

                    val problem =
                        when {
                            !lib.isFile -> "no $libName"
                            want == null -> "no expected ELF identity recorded for this ABI"
                            read < header.size ||
                                header[0] != 0x7F.toByte() ||
                                header[1] != 'E'.code.toByte() ||
                                header[2] != 'L'.code.toByte() ||
                                header[3] != 'F'.code.toByte() -> "not an ELF file (truncated or corrupt)"
                            header[4].toInt() != want.second ->
                                "${bitness[header[4].toInt()] ?: "unknown-class"} ELF, expected ${bitness[want.second]}"
                            else -> {
                                val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
                                if (machine != want.third) {
                                    "built for ELF machine 0x%02x, expected 0x%02x".format(machine, want.third)
                                } else {
                                    null
                                }
                            }
                        }

                    if (problem != null) problems += Triple(libName, abi, problem)
                }
            }

            if (problems.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("Committed native libraries are missing or wrong for ${problems.size} (library, ABI split) pair(s):")
                        problems.forEach { (libName, abi, problem) -> appendLine("    $libName / $abi: $problem") }
                        appendLine("Those APK splits would install with that library permanently unavailable.")
                        appendLine("Rebuild them:")
                        problems.forEach { (libName, abi, _) ->
                            val triple = expected[abi]?.first ?: "<add the Rust target for $abi>"
                            val rebuild = libs[libName]?.invoke(abi, triple) ?: "<no rebuild command recorded for $libName>"
                            appendLine("    $rebuild")
                        }
                        append("…or drop the ABI from `shippedAbis` in amethyst/build.gradle.kts.")
                    },
                )
            }
        }
    }

tasks.named("preBuild") {
    dependsOn(verifyNativeAbis)
}

// androidx.appfunctions-compiler runs in a per-module mode by default,
// emitting only the dispatcher Kotlin code. The aggregator that builds
// the `app_functions.xml` asset (which the system reads to discover our
// @AppFunction methods) is gated behind this KSP argument — without it,
// the manifest's `android.app.appfunctions` property points at a file
// that doesn't exist and the System UI logs "Unable to resolve
// AppFunctionMetadata." Set on the app module only; library modules
// (commons/quartz) would set it to "false".
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

// TODO: until google merges and unifiedpush updates https://github.com/tink-crypto/tink-java-apps/pull/5
configurations.all {
    val tink = "com.google.crypto.tink:tink-android:1.23.0"
    resolutionStrategy {
        force(tink)
        dependencySubstitution {
            substitute(module("com.google.crypto.tink:tink")).using(module(tink))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Gradle schedules Kotlin compilations of different variants of this module
// concurrently (e.g. playDebug + playBenchmark when CI runs unit tests, lint,
// and assembleBenchmark in one invocation), but they all share a single Kotlin
// daemon whose heap (kotlin.daemon.jvmargs) cannot fit two full :amethyst
// codegen passes — CI runs died with "GC overhead limit exceeded" inside the
// daemon. This no-op shared build service with maxParallelUsages = 1 tells the
// scheduler to run this module's Kotlin compile tasks one at a time; other
// projects' tasks (JVM tests, lint analysis, packaging) still run in parallel.
//
// CI-only: the OOM needs a cache-cold compile of several variants at once,
// which local builds (incremental, usually one variant) don't produce.
abstract class AmethystKotlinCompileLimiter : BuildService<BuildServiceParameters.None>

if (System.getenv("CI") != null) {
    val kotlinCompileLimiter =
        gradle.sharedServices.registerIfAbsent("amethystKotlinCompileLimiter", AmethystKotlinCompileLimiter::class) {
            maxParallelUsages.set(1)
        }

    tasks.withType<KotlinCompile>().configureEach {
        usesService(kotlinCompileLimiter)
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

baselineProfile {
    // One profile for the whole app rather than per-flavour: the ingest path being
    // captured is identical in play and fdroid, and a shared profile is what the
    // fdroid build needs — it never receives Play Cloud Profiles.
    mergeIntoMain = true

    // Keep the generated profile in source control so release builds do not depend on
    // a device being attached at build time.
    saveInSrc = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Compose composition tracing — DEBUG ONLY, profiling aid (not shipped). Makes each
    // recomposition show up as a NAMED slice in Perfetto system traces so we can see which
    // composable recomposes (e.g. during the cold-start feed first-paint). All Apache-2.0.
    // Usage: runtime-enable, then capture a Perfetto trace with the `track_event` data source:
    //   adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING \
    //     -n com.vitorpamplona.amethyst.debug/androidx.tracing.perfetto.TracingReceiver
    debugImplementation(libs.androidx.compose.runtime.tracing)
    debugImplementation(libs.androidx.tracing.perfetto)
    debugImplementation(libs.androidx.tracing.perfetto.binary)

    implementation(project(":quartz"))
    implementation(project(":commons"))
    implementation(project(":commonsUI"))
    implementation(project(":nestsClient"))
    // Agent text stream previews: the raw-QUIC binding plus the QUIC
    // stack under it (for the certificate validator it requires).
    implementation(project(":marmotQuic"))
    implementation(project(":quic"))
    implementation(project(":nappletHost"))
    // Compose Multiplatform resources runtime, so app-side screens that share a
    // string with a commons renderer can read commons' generated `Res` directly
    // instead of duplicating the key in the Android res tree.
    implementation(libs.jetbrains.compose.components.resources)
    implementation(libs.androidx.core.ktx)

    // Installs assets/dexopt/baseline.prof on first run. Play applies the profile at
    // install time via the .dm, but F-Droid builds have no store-side profile delivery
    // and no Cloud Profiles at all — there, this library is the only thing that gets
    // the shipped baseline profile into ART.
    implementation(libs.androidx.profileinstaller)

    // Profile produced by :baselineprofile from a real cold-start + ingest journey.
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)

    // Hardened WebView host for sandboxed napplet/nsite rendering (origin-restricted message bridge).
    implementation(libs.androidx.webkit)

    // Client side of the cross-process UI embedding: renders the sandboxed browser surface (hosted in
    // the keyless `:napplet` process) inside a Compose component in the main app.
    implementation(libs.androidx.privacysandbox.ui.core)
    implementation(libs.androidx.privacysandbox.ui.client)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Needs this to open gallery / image upload
    implementation(libs.androidx.fragment.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Material 3 Design
    implementation(libs.androidx.material3)

    // Adaptive Layout / Two Pane
    implementation(libs.androidx.material3.windowSize)
    implementation(libs.accompanist.adaptive)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Zoomable images
    implementation(libs.zoomable)

    // Biometrics
    implementation(libs.androidx.biometric.ktx)

    // Background Work
    implementation(libs.androidx.work.runtime.ktx)

    // Reads workouts from Android Health Connect (Samsung Health, Google Fit, Fitbit, Garmin, …)
    implementation(libs.androidx.health.connect.client)

    // Websockets API
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)

    // Encrypted Key Storage
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.datastore.preferences)

    // view videos
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.media3.session)

    // important for proxy / tor
    implementation(libs.androidx.media3.datasource.okhttp)

    // Load images from the web.
    implementation(libs.coil.compose)
    // view gifs
    implementation(libs.coil.gif)
    // view svgs
    implementation(libs.coil.svg)
    // enables network for coil
    implementation(libs.coil.okhttp)
    // loads thumbnails for media3
    // TODO: Replace this to the FrameExtractor in media 3
    // when FrameExtractor accepts custom data sources.
    implementation(libs.coil.video)

    // Permission to upload pictures:
    implementation(libs.accompanist.permissions)

    // For QR generation
    // ZXing core encodes the QR codes we display. Decoding is zxing-cpp, which we build
    // from source ourselves -- see tools/zxing-cpp-build -- rather than pulling a prebuilt
    // AAR nobody in this tree could verify; the .so lives in src/main/jniLibs and its
    // Kotlin wrapper is vendored at src/main/java/zxingcpp.
    implementation(libs.zxing)

    // OpenStreetMap tiles for road event location maps (kind 1315/1316)
    implementation(libs.osmdroid.android)

    // Markdown
    // implementation "com.halilibo.compose-richtext:richtext-ui:0.16.0"
    // implementation "com.halilibo.compose-richtext:richtext-ui-material:0.16.0"
    // implementation "com.halilibo.compose-richtext:richtext-commonmark:0.16.0"

    // Markdown (With fix for full-image bleeds)
    implementation(libs.markdown.ui)
    implementation(libs.markdown.ui.material3)
    implementation(libs.markdown.commonmark)

    // Syntax highlighting for the git repository code browser (Apache-2.0)
    implementation(libs.highlights)

    // LaTeX math rendering ($...$ and $$...$$ inline equations)
    implementation(libs.jlatexmath.android)
    implementation(libs.jlatexmath.font.greek)
    implementation(libs.jlatexmath.font.cyrillic)

    // Language picker and Theme chooser
    implementation(libs.androidx.appcompat)

    // Dynamically adjust between phone and tablet UI
    implementation(libs.androidx.window.core.android)

    // Local model for language identification
    "playImplementation"(libs.google.mlkit.language.id)

    // Google services model the translate text
    "playImplementation"(libs.google.mlkit.translate)

    // On-device AI writing assistance (Gemini Nano via AICore)
    "playImplementation"(libs.google.mlkit.genai.proofreading)
    "playImplementation"(libs.google.mlkit.genai.prompt)
    "playImplementation"(libs.google.mlkit.genai.rewriting)

    // On-device alt-text suggestions: genai image description (preferred, descriptive sentences)
    // with image-labeling as a keyword-join fallback for devices without AICore.
    "playImplementation"(libs.google.mlkit.genai.image.description)

    // PushNotifications
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.messaging)

    // PushNotifications(FDroid)
    "fdroidImplementation"(libs.unifiedpush)

    // Google Cast SDK — Chromecast support. Play flavor only because the
    // framework hard-depends on Google Play services, which is unavailable
    // on de-Googled / GrapheneOS devices that ship the F-Droid build.
    "playImplementation"(libs.play.services.cast.framework)

    // androidx.appfunctions — Gemini App Functions adapter. Pre-stable
    // (alpha) as of May 2026 — scoped to the play channel so the F-Droid
    // build stays free of Google AI dependencies. Surface is an
    // AppFunctionService registered in amethyst/src/play/AndroidManifest.xml,
    // generated at compile time by the KSP-driven appfunctions-compiler.
    "playImplementation"(libs.androidx.appfunctions)
    "playImplementation"(libs.androidx.appfunctions.service)
    "kspPlay"(libs.androidx.appfunctions.compiler)

    // Charts
    implementation(libs.vico.charts.compose)
    implementation(libs.vico.charts.m3)

    // Waveform visualizer
    implementation(libs.audiowaveform)

    // Video compression lib
    implementation(libs.abedElazizShe.video.compressor.fork)
    // Image compression lib
    implementation(libs.zelory.image.compressor)

    // EXIF metadata stripping
    implementation(libs.androidx.exifinterface)

    // WebRTC for voice/video calls
    implementation(libs.stream.webrtc.android)

    // Cbor for cashuB format
    implementation(libs.kotlinx.serialization.cbor)

    // Kotlin serialization for the times where we need the Json tree and performance is not that important.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)

    // In-process Nostr relay (geode) so unit tests that drive a real
    // NostrClient talk to an embedded relay instead of a public one. Same
    // wiring quartz uses for its jvmAndroidTest source set: the engine, its
    // testFixtures (RelayClientTest base, preload/publish helpers) and the
    // JVM SQLite driver the in-memory EventStore needs on a host JVM.
    testImplementation(project(":geode"))
    testImplementation(testFixtures(project(":geode")))
    testImplementation(libs.androidx.sqlite.bundled.jvm)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
}

// AGP 9.4.0's PerModuleBundleTask refuses to write an AAB entry whose name contains a colon:
//
//   Entry name contains invalid characters: root/META-INF/zoomable-root:zoomable.kotlin_module
//
// A .kotlin_module is named after the Gradle project path that produced it, colons included, and
// 14 of the 98 merged into this app carry one -- zoomable, Negentropy, vico, seven coil3 artifacts
// and four of ours. The entries are identical under 9.3.1, which writes them without complaint, so
// 9.4.0 added the rejection rather than the names.
//
// packaging.resources.excludes cannot remove them: with minification on, R8 emits the java
// resources and PerModuleBundleTask.addHybridFolder hands JarFlinger its own predicate, so those
// filters are never consulted. Nothing in the AAB reads a .kotlin_module either -- it exists for
// the Kotlin compiler to resolve top-level declarations across modules at COMPILE time.
//
// So drop them from R8's java-res jar in the moment before the bundle task opens it, then put the
// jar back exactly as R8 left it. The strip is doFirst on the CONSUMER rather than doLast on R8, so
// a build-cache hit on R8 cannot skip it; the restore is what keeps R8 up to date next build --
// without it Gradle sees a modified output and re-runs R8 every time, which measured ~2 min a build
// here for no work.
fun stripColonNamedEntries(jar: File): Int {
    val offenders = ZipFile(jar).use { zip -> zip.entries().toList().count { ':' in it.name } }
    if (offenders == 0) return 0

    val rewritten = File(jar.parentFile, "${jar.name}.stripped")
    ZipFile(jar).use { zip ->
        ZipOutputStream(rewritten.outputStream().buffered()).use { out ->
            zip.entries().asSequence().filterNot { ':' in it.name }.forEach { entry ->
                out.putNextEntry(ZipEntry(entry.name))
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
    rewritten.copyTo(jar, overwrite = true)
    rewritten.delete()
    return offenders
}

androidComponents.onVariants { variant ->
    val variantName = variant.name
    val capitalized = variantName.replaceFirstChar { it.uppercase() }
    tasks.matching { it.name == "build${capitalized}PreBundle" }.configureEach {
        val javaResDir = layout.buildDirectory.dir("intermediates/merged_java_res/$variantName")
        val backups = mutableMapOf<File, File>()

        doFirst {
            javaResDir.get().asFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "jar" }
                .forEach { jar ->
                    val backup = File(jar.parentFile, "${jar.name}.orig")
                    jar.copyTo(backup, overwrite = true)
                    val dropped = stripColonNamedEntries(jar)
                    if (dropped > 0) {
                        backups[jar] = backup
                        logger.lifecycle("Stripped $dropped colon-named entries from ${jar.name}")
                    } else {
                        backup.delete()
                    }
                }
        }

        doLast {
            backups.forEach { (jar, backup) ->
                backup.copyTo(jar, overwrite = true)
                backup.delete()
            }
            backups.clear()
        }
    }
}
