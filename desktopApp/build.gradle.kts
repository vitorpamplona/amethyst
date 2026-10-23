import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask
import java.io.DataInputStream
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.serialization)
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

// apksig reads APKs for `amy releases publish` (commons' ApkInspector); the desktop app never
// runs that, so it does not ship it. compose-rules.pro tells ProGuard not to look for it.
configurations.configureEach {
    exclude(group = "com.android.tools.build", module = "apksig")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.components.resources)

    // Quartz Nostr library (will use JVM target)
    implementation(libs.quartz)
    implementation(libs.cordn)

    // Commons library (headless) + its Compose UI half
    implementation(project(":commons"))
    implementation(project(":commonsUI"))

    // Marmot's raw-QUIC transport for agent text stream previews, as on Android
    implementation(project(":marmotQuic"))
    implementation(libs.quic)

    // The shared preference files (DataStore), as commons opens them
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle ViewModel (needed to access ViewModel supertype from commons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Image loading (Coil3 — explicit because commons uses implementation, not api)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)

    // Unicode emoji picker (Kodein Emoji.kt — MIT; offline system-font rendering on desktop)
    implementation(libs.kodein.emoji.compose.m3)

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
    // Audio-only playback (music, podcasts, voice notes) — MIT, same author; plays through Rodio
    // (nucleus.rodio, MIT) on every desktop OS. The video engine above needs a video track on
    // macOS and Windows and fails every audio file there. Its slf4j-simple would compete with
    // slf4j-nop below and print the library's logs, so it is left out.
    implementation(libs.composemediaplayer.audio) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
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

    // Reads the X server's resources (Xft.dpi) for the fractional scale AWT ignores on X11.
    // Already shipped (nestsClient's Opus bindings); declared so desktopApp can compile against it.
    implementation(libs.jna)

    // Hunspell spell checking for the composers where the OS has no checker (Linux) or it fails.
    // Lucene's pure-Java port; Apache-2.0, and its only dependency is lucene-core (Apache-2.0).
    implementation(libs.lucene.analysis.common)

    // QR code generation (ZXing core)
    implementation(libs.zxing)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp)

    // Compose UI testing (createComposeRule / onNodeWithText / etc.)
    testImplementation(libs.jetbrains.compose.ui.test.junit4)
}

// Previews another OS's look on this machine: `./gradlew :desktopApp:run -Damethyst.platform=MACOS
// -Damethyst.appearance=light`. Only the dev `run` gets them, never a packaged app.
// Added at execution: the Compose plugin sets the task's jvmArgs after this script configures it.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    val overrides = listOf("amethyst.platform", "amethyst.appearance", "amethyst.accent")
    val dockIcon = project.file("src/jvmMain/resources/icon.icns")
    doFirst {
        overrides.forEach { key -> System.getProperty(key)?.let { jvmArgs("-D$key=$it") } }
        // macOS: names the Dock's entry for the plain JVM `run` starts (the packaged app takes its
        // name from its Info.plist). The app switcher still says "java": it reads the name of the
        // bundle the binary sits in, and the JDK's java is its own bundle.
        if (System.getProperty("os.name").startsWith("Mac")) {
            jvmArgs("-Xdock:name=Amethyst", "-Xdock:icon=${dockIcon.absolutePath}")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.app.AmethystDesktopKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"
        // Lets the app name its X11 windows "Amethyst" (WM_CLASS), which the Linux desktop entries
        // declare as StartupWMClass. A no-op on macOS and Windows, and on native Wayland, where
        // WaylandDisplay names the window through its app_id instead.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // Lets the app ask a Wayland compositor whether it draws the window's title bar
        // (WaylandDisplay.preferServerSideDecorations). Only the JetBrains Runtime has the package,
        // and a JDK without it warns at every start, so only builds on JBR (the JDK Gradle runs on,
        // which is the one the packages bundle) get it.
        if (File(System.getProperty("java.home"), "lib/libawt_wlawt.so").isFile) {
            jvmArgs += "--add-exports=java.desktop/sun.awt.wl=ALL-UNNAMED"
        }

        jvmArgs += "-Xmx2g"

        // The shared app reads its version from here (there is no BuildConfig on desktop).
        jvmArgs += "-Damethyst.version=$appVersion"

        // Forward overrides from the gradle invocation to the launched app's JVM, so
        // `./gradlew :desktopApp:run -Damethyst.dataDir=/tmp/amy` keeps a test profile apart.
        listOf("amethyst.dataDir", "amethyst.debug", "amethyst.update.devCheck", "amethyst.update.pretendVersion").forEach { key ->
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
                "jdk.localedata",    // CLDR data for the shared app's date and number formatters
                "jdk.httpserver",    // Loopback relay that streams Tor-routed video through OkHttp
            )

            packageName = "Amethyst"
            packageVersion = appVersion
            description = "Nostr client for desktop"
            vendor = "Amethyst Contributors"

            macOS {
                bundleID = "com.vitorpamplona.amethyst.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))

                // LaunchServices hands a clicked nostr:/amethyst: link to the running app as an
                // AWT OpenURIEvent (DesktopDeepLinks.installMacOsOpenUriHandler) — but only for
                // schemes the packaged Info.plist declares here.
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>Nostr</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>nostr</string>
                                    <string>amethyst</string>
                                </array>
                            </dict>
                        </array>
                    """.trimIndent()
                }

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
                        // Compose's MacSigner maps the identity to a certificate via
                        // `security find-certificate`, which doesn't reliably resolve
                        // the CI-imported cert through the keychain search list (fails
                        // with "Could not find certificate ... in keychain []"). When
                        // the workflow exports the throwaway keychain path, point the
                        // lookup straight at it. Empty/unset on local builds — Compose
                        // then falls back to the default search list as before.
                        System.getenv("AMETHYST_MAC_SIGN_KEYCHAIN")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { keychain.set(it) }
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
                // AUMID persistence for Windows Toast notifications — without a
                // Start Menu shortcut the AUMID isn't registered and toasts
                // disappear from Windows Settings → Notifications after reboot.
                menu = true
                shortcut = true
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

// --- The packaged runtime ---
//
// jlink and jpackage run from the JDK Gradle runs on, and the runtime image bundles that JDK. Linux
// packages must be built on the JetBrains Runtime: only its AWT draws natively on Wayland
// (WLToolkit, lib/libawt_wlawt.so; see platform/WaylandDisplay.kt). CI installs it with
// actions/setup-java (`distribution: jetbrains`); a build on a stock JDK still works, X11 and
// XWayland only.
//
// Compose marks the tasks' JDK @Internal, so the build cache restored a runtime image jlinked on
// Temurin into a JetBrains Runtime build. The JDK's release file (vendor and version) keys it now.
tasks.withType<AbstractJvmToolOperationTask>().configureEach {
    inputs.property("jdkRelease", javaHome.map { File(it, "release").takeIf(File::isFile)?.readText().orEmpty() })
}
tasks.withType<AbstractJLinkTask>().configureEach {
    val jdk = javaHome
    doFirst {
        if (System.getProperty("os.name").startsWith("Linux") && !File(jdk.get(), "lib/libawt_wlawt.so").isFile) {
            logger.warn("w: ${jdk.get()} has no Wayland toolkit: this runtime draws through X11/XWayland only. Build Linux packages on the JetBrains Runtime.")
        }
    }
}

// --- GStreamer in the .rpm (Linux) ---
//
// The media player (kdroidFilter) drives the system GStreamer from a native library it unpacks
// from its jar when the first video plays, so neither jpackage nor rpmbuild sees it and the .rpm
// required no GStreamer: on a system without it every video said "Failed to create native
// player". Neither jpackage nor the Compose DSL can add a Requires. jpackage does take its spec
// from --resource-dir when a <package>.spec is there, but the Compose task empties that directory
// right before it runs jpackage, and deletes its inputs after. So the rpm tasks finish by
// repackaging the matching app image (createDistributable's, the same content) with the
// task's own package options and a resource dir holding jpackage's template, read from the JDK it
// packages with, plus these lines. The .deb gets the same packages from
// scripts/add-deb-gstreamer-deps.sh in CI. The same resource dir also adds StartupWMClass to the
// desktop entry (scripts/fix-deb-desktop-entry.sh does that for the .deb).
//
// base: playbin, appsink, volume; good: HTTP, MP4, HLS, audio output; libav/openh264: H.264 and
// AAC decoding. The boolean forms take Fedora's or openSUSE's name; the decoders are weak
// deps, since a distribution may keep them in a separate repository.
val rpmGStreamerDeps =
    """
    Requires: (gstreamer1-plugins-base or gstreamer-plugins-base)
    Requires: (gstreamer1-plugins-good or gstreamer-plugins-good)
    Recommends: (gstreamer1-plugin-libav or gstreamer-plugins-libav)
    Recommends: gstreamer1-plugin-openh264
    """.trimIndent()

// jpackage's template sets Autoreq: 0 (rpm would otherwise require the bundled runtime's own
// sonames, which Autoprov: 0 leaves unprovided) and fills in the system libraries itself, but it can
// only name their packages on an rpm-based host. Built on Ubuntu, the .rpm required none of them
// and only got them through what the GStreamer packages happen to pull in. These are the sonames
// the launcher, the bundled runtime and skiko link (DT_NEEDED) that the package doesn't ship, the
// same set the .deb gets from dpkg-shlibdeps; soname requires resolve on both distributions.
// The arm64 skiko also links libEGL (see scripts/add-deb-libegl-dep.sh). The arch is the packaging
// host's: jpackage only packages for the machine it runs on.
val rpmLibraryDeps =
    (
        listOf(
            "libasound.so.2", "libfontconfig.so.1", "libGL.so.1", "libstdc++.so.6", "libX11.so.6",
            "libXext.so.6", "libXi.so.6", "libXrender.so.1", "libXtst.so.6",
        ) + (if (System.getProperty("os.arch") == "aarch64") listOf("libEGL.so.1") else emptyList())
    ).joinToString("\n") { "Requires: $it()(64bit)" }

// The JetBrains Runtime's Wayland toolkit: what lib/libawt_wlawt.so links, and libxkbcommon, which it
// opens with dlopen. Only a runtime that has it needs them (scripts/add-deb-wayland-deps.sh, likewise).
val rpmWaylandDeps =
    listOf("libwayland-client.so.0", "libwayland-cursor.so.0", "libxkbcommon.so.0").joinToString("\n") { "Requires: $it()(64bit)" }

// Compose lays out text with the system's fonts and throws "Could not load font" when fontconfig
// has none, which is how a minimal openSUSE install comes: fontconfig there requires no font.
// Debian's fontconfig pulls in DejaVu, so the .deb needs no such line; this asks for the same font
// by Fedora's or openSUSE's name. Not the generic font(:lang=en): zypper satisfied that with the X11
// bitmap fonts, which fontconfig doesn't list, and an alternative that generic leaves the pick to
// the solver.
val rpmFontDeps = "Requires: (dejavu-sans-fonts or dejavu-fonts)"

// jpackage's .rpm registers the menu entry from %post with `xdg-desktop-menu install` and removes
// it from %preun. On a system without /usr/share/desktop-directories (a minimal openSUSE) that
// exits 3: %post only warns, but a failed %preun aborts the erase, so the package could not be
// removed. And RPM runs the old package's %preun after the new one's %post, so every upgrade
// unregistered the entry the new version had just registered. Like the .deb
// (scripts/fix-deb-desktop-entry.sh), the spec now ships the entry as a file the package owns and
// drops those calls. Two scriptlets cover upgrades from a version that still has them:
//  - %pre creates /usr/share/desktop-directories, so the old %preun can't fail and abort the upgrade.
//  - %posttrans, which runs after the old %preun, puts the entry back where that %preun deleted it
//    (it removes the copy in the first writable of /usr/local/share and /usr/share).
fun rpmSpec(
    template: String,
    requires: String,
): String {
    fun String.edit(
        pattern: String,
        change: (String) -> String,
    ): String {
        val match = Regex(pattern, RegexOption.MULTILINE).find(this)
        checkNotNull(match) { "jpackage's spec template no longer matches /$pattern/; update rpmSpec()" }
        return replaceRange(match.range, change(match.value))
    }
    // Shell variables in the scriptlets below; `$` would otherwise start a Kotlin template.
    val entry = "${'$'}entry"
    val name = "${'$'}{entry##*/}"
    return template
        .edit("^Autoreq: 0$") { "$it\n$requires" }
        // The package must not own the system directories the filesystem package does. Built
        // without an rpm database, jpackage falls back to its own list of them, but echoes it on
        // one line, which `comm` then never matches, so the .rpm owned /opt. One per line, plus the
        // directories the menu entry goes into.
        .edit("^%define default_filesystem .*$") { "$it /usr/share /usr/share/applications" }
        .edit("^\\{ rpm -ql filesystem \\|\\| echo %\\{default_filesystem}; }") {
            "{ rpm -ql filesystem || echo %{default_filesystem} | tr ' ' '\\n'; }"
        }
        .edit("^cp -r %\\{_sourcedir}APPLICATION_DIRECTORY/\\* %\\{buildroot}APPLICATION_DIRECTORY$") {
            """
            $it
            for entry in %{buildroot}APPLICATION_DIRECTORY/lib/*.desktop; do
              install -p -D -m 644 "$entry" "%{buildroot}%{_datadir}/applications/$name"
            done
            """.trimIndent()
        }.edit("^DESKTOP_COMMANDS_INSTALL\n") { "" }
        .edit("^DESKTOP_COMMANDS_UNINSTALL\n") { "" }
        .edit("^%pre\npackage_type=rpm$") { "$it\nif [ \"${'$'}1\" -gt 1 ]; then mkdir -p /usr/share/desktop-directories; fi" }
        .edit("^%clean$") {
            """
            %posttrans
            for entry in APPLICATION_DIRECTORY/lib/*.desktop; do
              [ -e "%{_datadir}/applications/$name" ] || install -p -m 644 "$entry" "%{_datadir}/applications/$name"
            done

            $it
            """.trimIndent()
        }
}

// What an app image already holds; jpackage refuses these next to --app-image.
val appImageOptions =
    setOf(
        "--input", "--runtime-image", "--main-jar", "--main-class", "--java-options", "--arguments",
        "--resource-dir", "--app-content", "--add-launcher", "--add-modules", "--module", "--module-path",
        "--jlink-options",
    )

listOf("packageRpm" to "main", "packageReleaseRpm" to "main-release").forEach { (rpmTask, buildType) ->
    tasks.withType<AbstractJPackageTask>().matching { it.name == rpmTask }.configureEach {
        dependsOn(if (buildType == "main") "createDistributable" else "createReleaseDistributable")
        val packagingJdk = javaHome
        val appName = packageName
        val rpmPackageName = linuxPackageName.orElse(packageName.map { it.lowercase() })
        val composeArgs = layout.buildDirectory.file("compose/tmp/$rpmTask.args.txt")
        val ourArgs = layout.buildDirectory.file("compose/tmp/$rpmTask.gstreamer.args.txt")
        val specDir = layout.buildDirectory.dir("compose/tmp/$rpmTask-rpm-resources")
        val appImages = layout.buildDirectory.dir("compose/binaries/$buildType/app")
        val skip = appImageOptions
        doLast {
            val jdk = packagingJdk.get()
            val waylandDeps = if (File(jdk, "lib/libawt_wlawt.so").isFile) listOf(rpmWaylandDeps) else emptyList()
            val deps = (listOf(rpmGStreamerDeps, rpmLibraryDeps, rpmFontDeps) + waylandDeps).joinToString("\n")
            val (template, desktopTemplate) =
                FileSystems.newFileSystem(URI.create("jrt:/"), mapOf("java.home" to jdk)).use { jrt ->
                    val resources = "/modules/jdk.jpackage/jdk/jpackage/internal/resources"
                    Files.readString(jrt.getPath("$resources/template.spec")) to
                        Files.readString(jrt.getPath("$resources/template.desktop"))
                }
            val resources = specDir.get().asFile.apply { deleteRecursively(); mkdirs() }
            File(resources, "${rpmPackageName.get()}.spec").writeText(rpmSpec(template, deps))
            // Docks and app switchers match the window's WM_CLASS ("Amethyst", set in AmethystDesktop.kt)
            // to the desktop entry's StartupWMClass, which jpackage's template lacks. jpackage takes
            // <app name>.desktop from the resource dir in its place and still fills in its fields.
            // StartupWMClass so docks match the window (AmethystDesktop.kt); MimeType + %u so
            // the desktop offers the app for nostr:/amethyst: links and passes the URI to the
            // launcher (DesktopDeepLinks.extractFromArgs).
            val desktopEntry =
                desktopTemplate.trimEnd()
                    .replace(Regex("^Exec=(\\S.*)$", RegexOption.MULTILINE)) { "Exec=${it.groupValues[1]} %u" }
                    .plus("\nStartupWMClass=Amethyst\nMimeType=x-scheme-handler/nostr;x-scheme-handler/amethyst;\n")
            File(resources, "${appName.get()}.desktop").writeText(desktopEntry)

            // The task's own options, one per line with quoted values, minus the app-image ones.
            val lines = composeArgs.get().asFile.readLines()
            val args = mutableListOf<String>()
            var i = 0
            while (i < lines.size) {
                val takesValue = i + 1 < lines.size && !lines[i + 1].startsWith("--")
                if (lines[i] !in skip) {
                    args += lines[i]
                    if (takesValue) args += lines[i + 1]
                }
                i += if (takesValue) 2 else 1
            }
            val appImage = appImages.get().dir(appName.get()).asFile
            check(appImage.isDirectory) { "No app image at $appImage to repackage the .rpm from" }
            args += listOf("--app-image", "\"${appImage.absolutePath}\"", "--resource-dir", "\"${resources.absolutePath}\"")

            val dest = File(args[args.indexOf("--dest") + 1].trim('"'))
            dest.listFiles { f -> f.name.endsWith(".rpm") }?.forEach { it.delete() }
            val argFile = ourArgs.get().asFile.apply { writeText(args.joinToString("\n")) }
            val process =
                ProcessBuilder(File(jdk, "bin/jpackage").absolutePath, "@${argFile.absolutePath}")
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "jpackage could not repackage the .rpm with its dependencies:\n$output" }
            logger.lifecycle("Repackaged ${dest.listFiles { f -> f.name.endsWith(".rpm") }?.joinToString { it.name }} with its dependencies")
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
// desktopApp/packaging/appimage/ as appimagetool-<arch>.AppImage.
// The arch is selected at task-execution time from the host JVM's os.arch, so
// the same task builds the correct AppImage on both x86_64 and aarch64 hosts.
// BUILDING.md documents local-dev fetch.
val createReleaseAppImage = tasks.register<Exec>("createReleaseAppImage") {
    group = "compose desktop"
    description = "Package createReleaseDistributable output into a Linux AppImage via appimagetool."
    dependsOn("createReleaseDistributable")

    // AppImage's ARCH env accepts the Linux kernel arch names: x86_64 / aarch64
    // / armhf / i686. jpackage produces host-native binaries, so mirror the
    // host JVM arch. Do not read the property inside doFirst — it needs to be
    // resolved at configuration time so outputs.file() below is stable.
    val hostArch = when (val a = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> a
    }

    val distDir = layout.buildDirectory.dir("compose/binaries/main-release/app/Amethyst")
    val appDir = layout.buildDirectory.dir("appimage/Amethyst.AppDir")
    // Named as the release asset (scripts/asset-name.sh), because the .zsync file appimagetool
    // writes next to it points at the AppImage by this file name.
    val outFile = layout.buildDirectory.file("appimage/amethyst-desktop-$appVersion-$hostArch.AppImage")
    // Update information (AppImage spec): AppImageUpdate, AppImageLauncher and Gear Lever find
    // the newest release's .zsync on GitHub and download only the blocks that changed. The .zsync
    // is written only when zsyncmake is installed (CI installs the zsync package).
    val updateInformation = "gh-releases-zsync|vitorpamplona|amethyst|latest|amethyst-desktop-*-$hostArch.AppImage.zsync"
    val toolRoot = layout.projectDirectory.dir("packaging/appimage")
    val appimagetool = toolRoot.file("appimagetool-$hostArch.AppImage")

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
        "-u",
        updateInformation,
        appDir.get().asFile.absolutePath,
        outFile.get().asFile.absolutePath,
    )
    environment("ARCH", hostArch)
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
val verifyJkeychainNativeSurvivesProguard = tasks.register("verifyJkeychainNativeSurvivesProguard") {
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

// ProGuard rewrites the classes of every jar it processes, so a signed jar's
// digests (META-INF/*.SF) stop matching and the JVM refuses its classes with a
// SecurityException. at.favre.lib:hkdf is signed and the Linux keyring needs it
// to open its Secret Service session (secret-service's TransportEncryption):
// with its classes refused, java-keyring found no Linux backend at all. The
// signatures cannot survive shrinking, so drop them from the output jars.
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    val proguardDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    doLast {
        val signature = Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)|META-INF/SIG-[^/]+")
        proguardDir.get().asFile.listFiles { f -> f.extension == "jar" }?.forEach { jar ->
            val stale = ZipFile(jar).use { zip -> zip.entries().toList().map { it.name }.filter { signature.matches(it) } }
            if (stale.isNotEmpty()) {
                FileSystems.newFileSystem(jar.toPath()).use { fs -> stale.forEach { Files.delete(fs.getPath(it)) } }
                println("Dropped the jar signature of ${jar.name}: ${stale.joinToString()}")
            }
        }
    }
}

// The Linux keyring's D-Bus transport is found through ServiceLoader, so a
// shrink with no keep rule empties its jar and every Linux release silently
// stops using the OS keyring (see compose-rules.pro). Guard it like the .so.
val verifyDbusTransportSurvivesProguard = tasks.register("verifyDbusTransportSurvivesProguard") {
    description = "Fail the release build if ProGuard strips dbus-java's socket transport (would break the Linux keyring at runtime)"
    group = "verification"
    dependsOn("proguardReleaseJars")
    val proguardDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    inputs.dir(proguardDir).withPropertyName("proguardOutput").withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("verify-dbus-transport.ok")
    outputs.file(marker)
    doLast {
        val required = "org/freedesktop/dbus/transport/jre/NativeTransportProvider.class"
        val jars = proguardDir.get().asFile.listFiles { f -> f.extension == "jar" } ?: emptyArray()
        val foundIn = jars.firstOrNull { jar -> ZipFile(jar).use { it.getEntry(required) != null } }
        require(foundIn != null) {
            "REGRESSION: $required missing from every proguarded jar.\n" +
                "dbus-java loads it through ServiceLoader; without it java-keyring finds no Linux backend\n" +
                "and the app silently stops keeping private keys in the OS keyring."
        }
        marker.get().asFile.writeText("verified $required survived ProGuard in ${foundIn.name}\n")
    }
}

// A native library that binds its methods from JNI_OnLoad with RegisterNatives fails to load if
// any method in its table is missing from the class, and the JVM then unloads it. Shrink strips
// a native declaration nothing in Java calls, so the library breaks in the release build only:
// ComposeMediaPlayer's DLL registers WindowsNativeBridge.nGetPlaybackSpeed, which no Kotlin code
// calls, and with it stripped the v1.18.0 Windows release had no video or audio at all ("Video
// playback unavailable"). A class ProGuard keeps must keep every native it declared.
val verifyJniNativesSurviveProguard = tasks.register("verifyJniNativesSurviveProguard") {
    description = "Fail the release build if ProGuard strips a native method from a class it keeps (breaks RegisterNatives libraries at load)"
    group = "verification"
    dependsOn("proguardReleaseJars")
    val proguardDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    val originalJars: FileCollection = configurations.getByName("runtimeClasspath")
    inputs.dir(proguardDir).withPropertyName("proguardOutput").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(originalJars).withPropertyName("originalJars").withNormalizer(ClasspathNormalizer::class.java)
    val marker = layout.buildDirectory.file("verify-jni-natives.ok")
    outputs.file(marker)
    doLast {
        val shrunkJars = proguardDir.get().asFile.listFiles { f -> f.extension == "jar" }.orEmpty().toList()
        require(shrunkJars.isNotEmpty()) { "No proguarded jars under ${proguardDir.get().asFile}" }
        // Every class ProGuard kept, natives or not: a kept class that lost all of them still breaks.
        val keptClasses = HashSet<String>()
        shrunkJars.forEach { jar -> ZipFile(jar).use { zip -> zip.entries().asSequence().forEach { keptClasses += it.name } } }
        val shrunk = nativeMethodsByClass(shrunkJars, keptClasses)
        require(shrunk.isNotEmpty()) { "No native methods found in the proguarded jars under ${proguardDir.get().asFile}" }
        // A class ProGuard dropped whole is never loaded, so nothing registers natives on it.
        val original = nativeMethodsByClass(originalJars.filter { it.extension == "jar" }.files.toList(), keptClasses)
        val stripped = original.flatMap { (cls, methods) -> (methods - shrunk[cls].orEmpty()).map { "${cls.removeSuffix(".class")}.$it" } }
        require(stripped.isEmpty()) {
            "REGRESSION: ProGuard stripped native methods from classes it kept:\n" +
                stripped.sorted().joinToString("\n") { "  $it" } + "\n" +
                "A library that binds its natives with RegisterNatives fails to load without them.\n" +
                "Check the -keepclassmembers native rule in desktopApp/compose-rules.pro."
        }
        val count = shrunk.values.sumOf { it.size }
        marker.get().asFile.writeText("verified $count native methods in ${shrunk.size} classes survived ProGuard\n")
        println("verifyJniNativesSurviveProguard: $count native methods in ${shrunk.size} classes kept")
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
        dependsOn(verifyDbusTransportSurvivesProguard)
        dependsOn(verifyJniNativesSurviveProguard)
    }
}

/**
 * Each class entry in [jars] that is in [only] and declares native methods, mapped to them as
 * `name(descriptor)`. A class in two jars counts once, from the first, as ProGuard keeps only that one.
 */
fun nativeMethodsByClass(
    jars: List<File>,
    only: Set<String>,
): Map<String, Set<String>> {
    val seen = HashSet<String>()
    val result = HashMap<String, Set<String>>()
    jars.forEach { jar ->
        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") && it.name in only && seen.add(it.name) }
                .forEach { entry ->
                    val natives = zip.getInputStream(entry).use { readNativeMethods(DataInputStream(it.buffered())) }
                    if (natives.isNotEmpty()) result[entry.name] = natives.toSet()
                }
        }
    }
    return result
}

/** The `name(descriptor)` of each ACC_NATIVE method in one class file (JVMS 4.1). */
fun readNativeMethods(input: DataInputStream): List<String> {
    if (input.readInt() != 0xCAFEBABE.toInt()) return emptyList()
    input.skipNBytes(4) // minor + major version
    val poolSize = input.readUnsignedShort()
    val utf8 = arrayOfNulls<String>(poolSize)
    var i = 1
    while (i < poolSize) {
        when (val tag = input.readUnsignedByte()) {
            1 -> utf8[i] = input.readUTF()
            7, 8, 16, 19, 20 -> input.skipNBytes(2)
            15 -> input.skipNBytes(3)
            3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
            5, 6 -> {
                input.skipNBytes(8)
                i++ // longs and doubles take two pool slots
            }
            else -> throw GradleException("Unknown constant pool tag $tag")
        }
        i++
    }
    input.skipNBytes(6) // access flags, this class, super class
    input.skipNBytes(2L * input.readUnsignedShort()) // interfaces
    fun skipMembers(collect: MutableList<String>?) {
        repeat(input.readUnsignedShort()) {
            val access = input.readUnsignedShort()
            val name = utf8[input.readUnsignedShort()]
            val descriptor = utf8[input.readUnsignedShort()]
            repeat(input.readUnsignedShort()) {
                input.skipNBytes(2)
                input.skipNBytes(input.readInt().toLong() and 0xFFFFFFFFL)
            }
            if (collect != null && access and 0x0100 != 0) collect += "$name$descriptor"
        }
    }
    skipMembers(null) // fields
    return ArrayList<String>().also { skipMembers(it) }
}

// macOS notarization: the bundled jars (secp256k1, jna, sqlite-bundled, skiko,
// jkeychain, kdroidFilter mediaplayer) carry Mach-O natives that Compose's
// .app codesign never reaches — codesign doesn't descend into jars, but Apple's
// notary service does and rejects any unsigned Mach-O ("Invalid"). Sign those
// natives inside the *proguarded* jars (the exact artifacts Compose then copies
// into the .app) so the subsequent bundle signing seals already-signed code.
// Runs only on macOS with the Developer ID identity exported — a no-op on every
// other leg and on unsigned local/PR builds.
val signMacJarNatives = tasks.register("signMacJarNatives") {
    description = "Codesign macOS Mach-O natives embedded in bundled jars before the .app is sealed + notarized"
    group = "build"
    dependsOn("proguardReleaseJars")
    val proguardDir = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    val script = rootProject.file("scripts/sign-macos-jar-natives.sh")
    val identity = providers.environmentVariable("AMETHYST_MAC_SIGN_IDENTITY")
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    onlyIf { isMac && !identity.orNull.isNullOrBlank() }
    doLast {
        val proc =
            ProcessBuilder("bash", script.absolutePath, proguardDir.get().asFile.absolutePath)
                .redirectErrorStream(true)
                .also { it.environment()["SIGN_IDENTITY"] = identity.get() }
                .start()
        proc.inputStream.bufferedReader().forEachLine { println(it) }
        val code = proc.waitFor()
        if (code != 0) throw GradleException("sign-macos-jar-natives.sh failed with exit code $code")
    }
}

// Must run after ProGuard writes the jars and before Compose assembles + signs
// the .app, so wire it onto createReleaseDistributable (which the DMG packaging
// depends on transitively).
tasks.matching { it.name == "createReleaseDistributable" }.configureEach {
    dependsOn(signMacJarNatives)
}
