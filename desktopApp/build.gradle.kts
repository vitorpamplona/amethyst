import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.Files
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
}

// RPM rejects dashes in version strings — replace with tilde (~) which RPM uses
// for prerelease ordering: 1.08.0~rc1 < 1.08.0 per RPM version comparison rules.
val appVersion: String = project.version.toString()

sourceSets {
    main {
        kotlin.srcDir("src/jvmMain/kotlin")
        resources.srcDir("src/jvmMain/resources")
    }
    test {
        kotlin.srcDir("src/jvmTest/kotlin")
        resources.srcDir("src/jvmTest/resources")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.components.resources)

    // Quartz Nostr library (will use JVM target)
    implementation(project(":quartz"))

    // Commons library
    implementation(project(":commons"))

    // Lifecycle ViewModel (needed to access ViewModel supertype from commons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Networking
    implementation(libs.okhttp)

    // JSON
    implementation(libs.jackson.module.kotlin)

    // Image loading (Coil3 — explicit because commons uses implementation, not api)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)

    // Video / audio playback — MIT, OS-native backends (MF / AVFoundation / GStreamer)
    // composemediaplayer 0.10.0 leaks kotlinx-coroutines-test as a *runtime* dependency
    // in its published POM. That jar ships a META-INF/services registration for
    // kotlinx.coroutines.CoroutineExceptionHandler -> ExceptionCollectorAsService; the
    // release ProGuard pass strips the (unreferenced) provider class but keeps the
    // services file, so the packaged dmg crashes at startup with a
    // ServiceConfigurationError the first time the coroutine exception handler loads.
    // It is test-only code that must never be on the production classpath — exclude it.
    implementation(libs.composemediaplayer) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-test")
    }

    // Thumbnail extraction — JCodec (pure-Java H.264). LGPL FFmpeg subprocess
    // for non-H.264 / HLS fallback is invoked via plain ProcessBuilder; no
    // wrapper library needed (see VideoThumbnailCache.runFfmpegToImage).
    implementation(libs.jcodec)
    implementation(libs.jcodec.javase)

    // EXIF stripping (lossless)
    implementation(libs.commons.imaging)

    // Collections
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.collection)

    // Tor daemon (desktop embedded via kmp-tor)
    implementation(libs.kmp.tor.runtime)
    implementation(libs.kmp.tor.resource.exec.tor)

    // SLF4J no-op — silence "No SLF4J providers" warnings from transitive deps
    implementation(libs.slf4j.nop)

    // QR code generation (ZXing core)
    implementation(libs.zxing)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp)

    // Compose UI testing (createComposeRule / onNodeWithText / etc.)
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"

        jvmArgs += "-Xmx2g"

        // Forward platform-preview overrides from the gradle invocation to the
        // launched app's JVM so `./gradlew :desktopApp:run -Damethyst.platform=GNOME`
        // works in addition to the env-var form (`AMETHYST_PLATFORM=GNOME`).
        listOf("amethyst.platform", "amethyst.appearance", "amethyst.accent").forEach { key ->
            System.getProperty(key)?.let { jvmArgs += "-D$key=$it" }
        }

        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/jvmMain/appResources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            // Output of ./gradlew suggestRuntimeModules (+ java.management already present)
            modules(
                "java.instrument",   // Runtime instrumentation (agent/profiler hooks)
                "java.management",   // Required by kmp-tor TorRuntime
                "java.prefs",        // java.util.prefs (desktop persistence)
                "java.sql",          // JDBC metadata (Jackson, SQLite driver)
                "jdk.security.auth", // JAAS authentication callbacks
                "jdk.unsupported",   // sun.misc.Unsafe (secp256k1-kmp-jni-jvm, JNA)
            )

            packageName = "Amethyst"
            packageVersion = appVersion
            description = "Nostr client for desktop"
            vendor = "Amethyst Contributors"

            macOS {
                bundleID = "com.vitorpamplona.amethyst.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))

                // --- Developer ID code signing + notarization ---
                // Required for Homebrew's main cask (unsigned casks rejected after
                // 2026-09-01) and to clear macOS Gatekeeper without the right-click
                // dance. Gated on the signing-identity env var so local dev builds
                // and PR CI keep producing plain UNSIGNED DMGs exactly as before —
                // signing only kicks in when the release workflow exports these
                // (which it does only when the Apple secrets are present):
                //
                //   AMETHYST_MAC_SIGN_IDENTITY  "Developer ID Application: NAME (TEAMID)"
                //   AMETHYST_NOTARY_APPLE_ID    Apple ID email of the notary account
                //   AMETHYST_NOTARY_PASSWORD    app-specific password for that Apple ID
                //   AMETHYST_NOTARY_TEAM_ID     10-char Apple Developer Team ID
                //
                // The Developer ID Application certificate must already be in the
                // build host's keychain (CI imports it from a base64 .p12 secret).
                // Compose ships default hardened-runtime entitlements that permit
                // the JVM's JIT, so no custom entitlements file is needed.
                val macSignIdentity = System.getenv("AMETHYST_MAC_SIGN_IDENTITY")
                if (!macSignIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(macSignIdentity)
                    }
                    notarization {
                        appleID.set(System.getenv("AMETHYST_NOTARY_APPLE_ID"))
                        password.set(System.getenv("AMETHYST_NOTARY_PASSWORD"))
                        teamID.set(System.getenv("AMETHYST_NOTARY_TEAM_ID"))
                    }
                }
            }

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                menuGroup = "Amethyst"
                upgradeUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
                menuGroup = "Network"
                appCategory = "Network"
                debMaintainer = "vitor@vitorpamplona.com"
                // SPDX compound expression. Bundled components:
                //   MIT                  — Amethyst + kdroidFilter ComposeMediaPlayer
                //   LGPL-2.1-or-later    — FFmpeg (LGPL build, bundled per OS for thumbnail fallback) +
                //                          GStreamer (Linux runtime dep, system-installed)
                //   BSD-2-Clause         — JCodec
                //   Apache-2.0           — Jaffree + many transitive Java libraries
                rpmLicenseType = "MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0"
                // RPM version: replace dashes with tilde (1.08.0~rc1 < 1.08.0 per RPM ordering).
                rpmPackageVersion = appVersion.replace("-", "~")
            }
        }

        // Compose Multiplatform 1.11.0 wired ProGuard 7.7.0 into the release
        // build. The Android (mobile) module already solved the same class of
        // problems with `-dontobfuscate` plus global `-keepnames` / `-keep enum`
        // rules (see `amethyst/proguard-rules.pro`). We mirror that strategy in
        // `compose-rules.pro` so the desktop release survives JNI callbacks
        // (secp256k1-kmp, sqlite-bundled, jkeychain, kdroidFilter native)
        // and reflection-heavy libraries (Jackson, JNA) without renaming.
        //
        // Shrink and optimize stay ON. One ProGuard optimize sub-pass is
        // disabled in `compose-rules.pro` to avoid a generated okio bridge
        // whose declared return type the JVM verifier rejects (R8 doesn't hit
        // this — it generates bridges differently from ProGuard).
        buildTypes.release.proguard {
            version.set("7.9.1") // Kotlin 2.3 metadata support
            configurationFiles.from(project.file("compose-rules.pro"))
        }
    }
}

// --- AppImage packaging (Linux) ---
//
// Compose Multiplatform's TargetFormat.AppImage is known-broken in 1.10.x (CMP-7101).
// Instead: wrap `createReleaseDistributable` output with `appimagetool`, which
// just packages an AppDir as-is. We deliberately avoid `linuxdeploy` here —
// linuxdeploy auto-walks every binary in the AppDir with ldd to bundle deps,
// but jpackage already ships a self-contained tree we don't want it touching
// (the bundled JRE has libjvm.so under usr/lib/runtime/lib/server/ while sibling
// libs use $ORIGIN RPATH — ldd can't resolve without help).
// appimagetool sidesteps that — it only embeds the AppDir into a SquashFS,
// runtime-prepended, signed AppImage. AppRun handles LD_LIBRARY_PATH at launch.
//
// kdroidFilter (video/audio) links against system GStreamer at runtime — the
// AppImage does not bundle GStreamer; the host system must have it installed.
//
// Build inputs live in desktopApp/packaging/appimage/:
//   - AppRun              shell launcher
//   - amethyst.desktop    XDG desktop entry
//   - amethyst.png        512x512 icon
//
// appimagetool binary is fetched by CI (SHA-verified) into
// desktopApp/packaging/appimage/ as appimagetool-x86_64.AppImage.
// BUILDING.md documents local-dev fetch.
val createReleaseAppImage by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Package createReleaseDistributable output into a Linux AppImage via appimagetool."
    dependsOn("createReleaseDistributable")

    val distDir = layout.buildDirectory.dir("compose/binaries/main-release/app/Amethyst")
    val appDir = layout.buildDirectory.dir("appimage/Amethyst.AppDir")
    val outFile = layout.buildDirectory.file("appimage/Amethyst-$appVersion-x86_64.AppImage")
    val toolRoot = layout.projectDirectory.dir("packaging/appimage")
    val appimagetool = toolRoot.file("appimagetool-x86_64.AppImage")

    inputs.dir(distDir)
    inputs.dir(toolRoot)
    outputs.file(outFile)

    doFirst {
        val dir = appDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        copy {
            from(distDir) { into("usr") }
            from(toolRoot.file("AppRun")) {
                rename { "AppRun" }
                filePermissions { unix("0755") }
            }
            from(toolRoot.file("amethyst.desktop"))
            from(toolRoot.file("amethyst.png"))
            into(dir)
        }
        // DirIcon is used by desktop integrations (file managers, AppImageLauncher)
        val dirIcon = File(dir, ".DirIcon")
        if (dirIcon.exists()) dirIcon.delete()
        Files.createSymbolicLink(dirIcon.toPath(), File("amethyst.png").toPath())

        if (!appimagetool.asFile.canExecute()) {
            appimagetool.asFile.setExecutable(true)
        }
    }

    commandLine(
        appimagetool.asFile.absolutePath,
        appDir.get().asFile.absolutePath,
        outFile.get().asFile.absolutePath,
    )
    environment("ARCH", "x86_64")
    // Bypass FUSE requirement on CI runners (ubuntu-latest lacks libfuse.so.2).
    // AppImage standard env var: extracts + runs without mounting.
    environment("APPIMAGE_EXTRACT_AND_RUN", "1")
}

// ============================================================================
// Native lib regression guard
// ============================================================================
// `pt.davidafsilva.apple:jkeychain` ships its `osxkeychain.so` native library
// as a resource at the JAR root, and `OSXKeychain.loadSharedObject()` reaches
// it with `getResourceAsStream("/osxkeychain.so")`. If a future ProGuard
// upgrade, dep swap, or build-config change strips this resource from the
// release distributable, every macOS user is forced back to the login screen
// on every cold boot (the Keychain backend can't load, `SecureKeyStorage`
// falls back to its password-prompted encrypted file, which never prompts
// in a GUI cold boot → keys silently null → bunker / nsec / NWC unrecoverable).
//
// As of the current build the resource DOES survive ProGuard (it ships in the
// proguarded jkeychain-1.1.0-*.jar with all 117 KB intact), so this task is
// a regression guard, not a workaround. It's wired onto every release task so
// it fails the build immediately if the .so disappears.
val verifyJkeychainNativeSurvivesProguard by tasks.registering {
    description = "Fail the release build if osxkeychain.so is stripped from proguarded output (would break macOS Keychain at runtime)"
    group = "verification"
    dependsOn("proguardReleaseJars")
    val proguardDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    inputs.dir(proguardDir).withPropertyName("proguardOutput").withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("verify-jkeychain-native.ok")
    outputs.file(marker)
    doLast {
        val dir = proguardDir.get().asFile
        require(dir.isDirectory) { "ProGuard output dir missing: $dir" }
        val jars = dir.listFiles { f -> f.extension == "jar" } ?: emptyArray()
        require(jars.isNotEmpty()) { "No proguarded jars under $dir" }
        val foundIn = jars.firstOrNull { jar ->
            val zip = ZipFile(jar)
            try {
                val entries = zip.entries()
                var found = false
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().name == "osxkeychain.so") {
                        found = true
                        break
                    }
                }
                found
            } finally {
                zip.close()
            }
        }
        require(foundIn != null) {
            "REGRESSION: osxkeychain.so missing from every proguarded jar in $dir.\n" +
                "OSXKeychain.loadSharedObject() calls getResourceAsStream(\"/osxkeychain.so\") — if this\n" +
                "resource is not at classpath root in the release distributable, every macOS user is\n" +
                "forced to re-log-in on every cold boot of the release DMG."
        }
        marker.get().asFile.writeText("verified osxkeychain.so survived ProGuard in ${foundIn.name}\n")
        println("verifyJkeychainNativeSurvivesProguard: osxkeychain.so present in ${foundIn.name}")
    }
}

// Gate every release-packaging task on the verification so a stripped .so
// never reaches a release artifact.
listOf(
    "packageReleaseDmg",
    "packageReleaseMsi",
    "packageReleaseDeb",
    "packageReleaseRpm",
    "packageReleaseDistributionForCurrentOS",
    "packageReleaseUberJarForCurrentOS",
    "createReleaseDistributable",
    "runReleaseDistributable",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach {
        dependsOn(verifyJkeychainNativeSurvivesProguard)
    }
}
