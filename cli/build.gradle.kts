import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.internal.os.OperatingSystem
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.serialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
    test {
        kotlin.srcDir("src/test/kotlin")
        resources.srcDir("src/test/resources")
    }
}

// `resources.srcDir(...)` above re-adds the default resource root, so every resource is registered
// twice (identical source + destination). Pick last-wins instead of failing the copy.
tasks.withType<ProcessResources>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// BuzzAgentWrapperSyncTest compares the bundled buzz-agent wrappers against their
// tools/ reference copies. The reference tree lives outside this module, so without
// declaring it Gradle calls :cli:test up-to-date after a tools/-only edit — exactly the
// drift the test exists to catch.
tasks.named<Test>("test") {
    inputs
        .dir(rootProject.layout.projectDirectory.dir("tools/buzz-agent"))
        .withPropertyName("buzzAgentWrapperReference")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.quartz)
    implementation(libs.marmot)
    implementation(libs.cordn)
    implementation(project(":commons"))
    // Agent text stream previews: the raw-QUIC binding, and the QUIC
    // stack under it for the certificate validator the transport requires.
    implementation(project(":marmotQuic"))
    implementation(libs.quic)
    // `amy serve` embeds geode (the standalone Ktor relay built on quartz's
    // relay-server code). geode depends only on quartz, never on :amethyst.
    implementation(project(":geode"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)
    // Reads zapstore.yaml for `amy releases publish` (Apache-2.0).
    implementation(libs.snakeyaml)
    implementation(libs.slf4j.nop)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // The JVM secp256k1 JNI binding so tests can exercise real signing/verification.
    testImplementation(libs.secp256k1.kmp.jni.jvm)
}

// amy is headless. It compiles against zero Compose UI (the Compose deps are
// `implementation` in :commons, so they never reach the CLI compile classpath —
// verified), yet they still ride the *runtime* classpath into the shipped image:
// ~29 MB of Compose desktop render stack, including skiko's native .dylibs that
// needlessly enlarge the macOS notarization surface. None of it is reachable
// from a CLI command, so drop the whole UI layer from the runtime image. Keep
// `androidx.compose.runtime` — snapshot state + the @Stable/@Immutable
// annotations ARE CLI-safe and used by commons models/state holders (see
// commons/ARCHITECTURE.md §1). A regression here is caught by the "no Compose UI
// in the amy image" assertion in .github/workflows/create-release.yml.
configurations.named("runtimeClasspath") {
    // skiko (native renderer + its .dylibs) and the Compose UI bytecode layer.
    // NB: Compose Multiplatform publishes UI under org.jetbrains.compose.* — only
    // `runtime` relocates to androidx.compose.runtime, which we deliberately keep.
    exclude(group = "org.jetbrains.skiko")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.material")
    exclude(group = "org.jetbrains.compose.material3")
    exclude(group = "org.jetbrains.compose.animation")
}

application {
    mainClass.set("com.vitorpamplona.amethyst.cli.MainKt")
    applicationName = "amy"
    // amy is a non-interactive CLI — never spawn AWT GUI threads. Defensive
    // against transitive deps that touch ImageIO / Toolkit during image
    // upload (see commons/.../service/upload/ImageReencoder.kt). Belt-and-
    // braces with the runtime System.setProperty in Main.kt.
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

// Inject `LANG=C.UTF-8` (and the matching Windows code page) into the
// launcher scripts when `LANG`/`LC_ALL` aren't already set. Without this,
// running amy under a POSIX/C locale leaves `sun.jnu.encoding` as
// `ANSI_X3.4-1968` (i.e. ASCII) — JEP 400 pins `file.encoding` to UTF-8
// but deliberately leaves `sun.jnu.encoding` tied to the OS locale, and
// it's read by the JVM *before* any `-D` flag has a chance to apply. So
// `-Dsun.jnu.encoding=UTF-8` does nothing; the encoding has to be set in
// the environment that launches Java.
//
// The symptom this fixes: `marmot message react "$gid" "$id" "🍕"` —
// the shell hands the four UTF-8 bytes (F0 9F 8D 95) to the JVM, the
// JVM decodes each one as ASCII (every byte > 0x7F → U+FFFD), and amy
// then signs a kind:7 whose `content` is four replacement characters.
// Whitenoise rejects it with "Invalid reaction content".
val patchAmyLauncherCharset = tasks.register("patchAmyLauncherCharset") {
    val appName = application.applicationName
    val startScriptsTask = tasks.named("startScripts")
    dependsOn(startScriptsTask)
    // Patch the scripts at their source location so installDist (and any
    // downstream packaging like jpackage) copies the patched copies.
    doLast {
        val unixScript = layout.buildDirectory.file("scripts/$appName").get().asFile
        if (unixScript.exists()) {
            val text = unixScript.readText()
            val marker = "# Default LANG to UTF-8 so the JVM picks UTF-8 for sun.jnu.encoding"
            if (!text.contains(marker)) {
                // Build the snippet with explicit literal `$` characters to avoid
                // regex-replacement-string escapes when we splice it in.
                val injected = buildString {
                    append("\n")
                    append(marker).append("\n")
                    append("# (POSIX/C locales force ANSI_X3.4-1968, which mangles non-ASCII argv).\n")
                    append("if [ -z \"")
                    append("$").append("{LANG-}").append("$").append("{LC_ALL-}")
                    append("\" ]; then\n")
                    append("    export LANG=C.UTF-8\n")
                    append("fi\n")
                }
                // Insert right after the shebang. Use indexOf+substring instead
                // of regex replaceFirst so the `$` chars in `injected` aren't
                // mistaken for backreferences.
                val nl = text.indexOf('\n')
                val patched =
                    if (nl >= 0 && text.startsWith("#!")) {
                        text.substring(0, nl + 1) + injected + text.substring(nl + 1)
                    } else {
                        injected.trimStart('\n') + text
                    }
                unixScript.writeText(patched)
            }
        }
        val batScript = layout.buildDirectory.file("scripts/$appName.bat").get().asFile
        if (batScript.exists()) {
            val text = batScript.readText()
            val marker = "rem Pin code page to UTF-8 so sun.jnu.encoding picks UTF-8"
            if (!text.contains(marker)) {
                val injected =
                    "$marker\r\n" +
                        "chcp 65001 > NUL 2>&1\r\n"
                val anchor = "@if \"%DEBUG%\"==\"\" @echo off"
                val idx = text.indexOf(anchor)
                if (idx >= 0) {
                    val afterAnchor = text.indexOf('\n', idx)
                    if (afterAnchor >= 0) {
                        val patched =
                            text.substring(0, afterAnchor + 1) + injected + text.substring(afterAnchor + 1)
                        batScript.writeText(patched)
                    }
                }
            }
        }
    }
}

tasks.named("installDist") {
    dependsOn(patchAmyLauncherCharset)
}

// Since Compose Multiplatform 1.12 the JetBrains `runtime-desktop` artifact is a class-less
// redirect stub (manifest + license) whose real classes come from
// androidx.compose.runtime:runtime-desktop — and both jars are named
// `runtime-desktop-<ver>.jar`, so installDist/distZip failed on the duplicate lib/ entry.
// Excluding the module from resolution would also drop the androidx jar it pulls in, so only
// the stub's file is left out of the copy; the start script names the androidx jar either way.
distributions.named("main") {
    contents {
        eachFile {
            if (file.invariantSeparatorsPath.contains("/org.jetbrains.compose.runtime/runtime-desktop/")) exclude()
        }
    }
}

// ---------------------------------------------------------------------------
// Native distribution (jlink + jpackage)
//
// Produces a self-contained `amy` bundle with a minimal jlink'd JRE embedded —
// no JDK required on the user machine. Outputs land under cli/build/:
//   - amy-image/amy/        portable, flat directory (bin/ + lib/ + runtime/)
//                           tar this up on every OS → amy-<ver>-<fam>-<arch>.tar.gz
//   - jpackage/amy_*.deb    Debian/Ubuntu package (Linux runners only)
//   - jpackage/amy-*.rpm    Fedora/RHEL package (Linux runners only)
//
// We deliberately build our own app-image instead of using `jpackage --type
// app-image`, because on macOS jpackage produces an `.app` bundle (with the
// binary buried at Contents/MacOS/amy) — awful UX for a CLI. The flat tree we
// build matches the Linux jpackage layout on every OS.
//
// Packaging for release is wired in .github/workflows/create-release.yml.
// See cli/plans/2026-04-21-cli-distribution.md for the overall plan.
// ---------------------------------------------------------------------------

val appVersion: String = project.version.toString()

// RPM rejects dashes in version strings — replace with tilde (~), which RPM
// treats as prerelease-lower-than: 1.08.0~rc1 < 1.08.0.
val rpmVersion: String = appVersion.replace("-", "~")

val mainJarName: String = "cli-$appVersion.jar"
val mainClassName: String = "com.vitorpamplona.amethyst.cli.MainKt"

// Minimal JDK 21 module set for amy. Keep this tight — every module adds
// megabytes to the bundle. jlink does NOT notice a missing module: amy's jars
// are not modular, so a class from an absent module only fails at run time
// (NoClassDefFoundError). `verifyAmyRuntimeModules` below runs jdeps over the
// shipped jars and fails the image build instead. Modules jdeps cannot see
// (loaded by name or as a service) are listed here by hand: java.naming,
// java.net.http, java.sql (the SQLite JDBC driver), jdk.crypto.ec (TLS).
val jlinkModules: String = listOf(
    "java.base",
    // AWT/ImageIO: image re-encoding and metadata for uploads (dm send-file).
    "java.desktop",
    // kotlinx-coroutines' debug agent (AgentPremain); tiny, and keeps the check exact.
    "java.instrument",
    "java.logging",
    // ManagementFactory: Context sizes its socket pool from the fd limit.
    "java.management",
    "java.naming",
    "java.net.http",
    // java.util.prefs: PreferencesIndexRelays, built by every Context.
    "java.prefs",
    "java.sql",
    "java.xml",
    "jdk.crypto.ec",
    // com.sun.net.httpserver: StaticSiteServe (nsite/napplet serve).
    "jdk.httpserver",
    // UnixOperatingSystemMXBean: the fd limit itself.
    "jdk.management",
    // dbus-java, the Linux Secret Service keyring: unix-socket options and SASL auth.
    "jdk.net",
    "jdk.security.auth",
    "jdk.unsupported",
).joinToString(",")

fun javaToolBin(name: String): Provider<String> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map {
        val exe = if (OperatingSystem.current().isWindows) "$name.exe" else name
        it.metadata.installationPath.file("bin/$exe").asFile.absolutePath
    }

val jlinkRuntimeDir = layout.buildDirectory.dir("jlink-runtime")
val amyImageRoot = layout.buildDirectory.dir("amy-image")
val amyImageDir = layout.buildDirectory.dir("amy-image/amy")
val installLibDir = layout.buildDirectory.dir("install/amy/lib")
val jpackageOutDir = layout.buildDirectory.dir("jpackage")

val jlinkRuntime =
    tasks.register<Exec>("jlinkRuntime") {
        group = "distribution"
        description = "Build a minimal JRE for amy via jlink."

        outputs.dir(jlinkRuntimeDir)

        val jlinkBin = javaToolBin("jlink")
        val outDir = jlinkRuntimeDir
        val modules = jlinkModules

        doFirst {
            // jlink refuses to write into an existing directory.
            outDir.get().asFile.deleteRecursively()
            executable = jlinkBin.get()
            args(
                "--add-modules", modules,
                "--no-header-files",
                "--no-man-pages",
                "--strip-debug",
                // JDK 21+: --compress <int> is deprecated; use zip-<level>.
                "--compress", "zip-6",
                "--output", outDir.get().asFile.absolutePath,
            )
        }
    }

/** [jar] itself, or a copy in [dir] without its module-info.class entries when it has any. */
fun withoutModuleInfo(
    jar: File,
    dir: File,
): File {
    ZipFile(jar).use { zip ->
        if (zip.entries().asSequence().none { it.name.endsWith("module-info.class") }) return jar
        val copy = File(dir, jar.name)
        ZipOutputStream(copy.outputStream()).use { out ->
            // Some merged jars hold an entry name twice; a zip stream refuses the second.
            val written = HashSet<String>()
            for (entry in zip.entries()) {
                if (entry.name.endsWith("module-info.class") || !written.add(entry.name)) continue
                out.putNextEntry(ZipEntry(entry.name))
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return copy
    }
}

// jlink cannot tell that a non-modular jar needs a module it was not given, so a
// gap only shows when a command reaches the missing class: v1.18.0 shipped without
// java.management and every command that opened a Context died with
// NoClassDefFoundError. This runs jdeps over every jar the image ships and fails
// when one needs a module the jlink'd runtime does not contain.
val verifyAmyRuntimeModules =
    tasks.register("verifyAmyRuntimeModules") {
        group = "verification"
        description = "Fail if a jar shipped with amy needs a JDK module its jlink'd runtime lacks."

        dependsOn(tasks.named("installDist"), jlinkRuntime)
        inputs.dir(installLibDir)
        inputs.dir(jlinkRuntimeDir)
        // A marker, so an unchanged lib/ and runtime skip the jdeps run as UP-TO-DATE.
        val marker = layout.buildDirectory.file("verifyAmyRuntimeModules/ok")
        outputs.file(marker)

        val jdepsBin = javaToolBin("jdeps")
        val libDir = installLibDir
        val runtimeDir = jlinkRuntimeDir
        val javaExe = if (OperatingSystem.current().isWindows) "java.exe" else "java"

        doLast {
            val runtimeJava = runtimeDir.get().file("bin/$javaExe").asFile.absolutePath
            val listModules = ProcessBuilder(runtimeJava, "--list-modules").redirectErrorStream(true).start()
            val listed = listModules.inputStream.bufferedReader().readLines()
            // A runtime that cannot start lists nothing, and every module would read as missing.
            if (listModules.waitFor() != 0) throw GradleException("the jlink'd runtime did not start (`java --list-modules`):\n" + listed.joinToString("\n"))
            val present = listed.map { it.substringBefore('@').trim() }.toSet()

            // jdeps resolves a jar with a module-info (okhttp3, commons-imaging) as a module
            // and fails on the first `requires` it cannot find, and a module path doesn't help
            // because many of the other jars have no valid automatic module name. amy runs
            // everything from the class path anyway, so analyse copies without module-info:
            // then one jdeps run over all of them works, instead of one JVM per jar.
            val scratch = temporaryDir.also { it.deleteRecursively(); it.mkdirs() }
            val jars = libDir.get().asFile.listFiles { f -> f.extension == "jar" }.orEmpty().sortedBy { it.name }
            val analysed = jars.map { withoutModuleInfo(it, scratch) }
            val jdeps =
                ProcessBuilder(listOf(jdepsBin.get(), "-s", "--ignore-missing-deps", "--multi-release", "21") + analysed.map { it.absolutePath })
                    .redirectErrorStream(true)
                    .start()
            val lines = jdeps.inputStream.bufferedReader().readLines()
            if (jdeps.waitFor() != 0) throw GradleException("jdeps failed:\n" + lines.joinToString("\n"))

            // `-s` prints one `<jar> -> <module>` line per module each jar uses.
            val missing = sortedMapOf<String, MutableSet<String>>()
            for (line in lines) {
                val jar = line.substringBefore("->", "").trim()
                val module = line.substringAfter("->", "").trim()
                if ((module.startsWith("java.") || module.startsWith("jdk.")) && module !in present) {
                    missing.getOrPut(module) { sortedSetOf() }.add(jar)
                }
            }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "amy's jlink runtime lacks modules its jars use; add them to jlinkModules:\n" +
                        missing.entries.joinToString("\n") { (module, users) -> "  $module  (used by ${users.joinToString(", ")})" },
                )
            }
            marker.get().asFile.writeText("every JDK module the amy jars use is in the runtime\n")
        }
    }

// Flat app-image: bin/amy launcher + lib/*.jar + runtime/ (the jlink'd JRE).
// Cross-platform — the release workflow archives this on every OS (tar.gz on
// unix, zip on Windows). We write BOTH a POSIX `amy` shell launcher AND a
// Windows `amy.bat` launcher into `bin/` unconditionally so the same tree is
// runnable on any target after extraction, regardless of which OS built it.
// (The bundled jlink runtime is host-native — you still need to unzip a
// Windows-built image on Windows to actually launch it — but the launcher
// scripts themselves are host-agnostic, which keeps the layout uniform and
// makes ad-hoc cross-machine inspection painless.)
val amyImage =
    tasks.register<Sync>("amyImage") {
        group = "distribution"
        description = "Assemble a portable amy app-image (bin/ + lib/ + runtime/)."

        dependsOn(tasks.named("installDist"), jlinkRuntime, verifyAmyRuntimeModules)

        into(amyImageDir)

        // jars from installDist
        from(installLibDir) {
            into("lib")
        }
        // jlink'd JRE
        from(jlinkRuntimeDir) {
            into("runtime")
        }

        val mainJar = mainJarName
        val mainClass = mainClassName
        val unixLauncher =
            """
            #!/bin/sh
            # amy launcher — uses the bundled jlink'd JRE so no system Java is required.
            DIR="${'$'}(cd "${'$'}(dirname "${'$'}0")/.." && pwd)"
            exec "${'$'}DIR/runtime/bin/java" -Djava.awt.headless=true -cp "${'$'}DIR/lib/*" $mainClass "${'$'}@"
            """.trimIndent() + "\n"
        // Windows launcher. Uses %~dp0 (drive+path of this .bat, always ending in
        // a backslash) so it resolves the app root without depending on CWD, then
        // execs the bundled JRE against lib\*. `chcp 65001` pins the console to
        // UTF-8 so `sun.jnu.encoding` isn't the OS OEM code page — same rationale
        // as the installDist launcher patch above. CRLF line endings so cmd.exe
        // parses it correctly.
        //
        // The `for %%i in (...) do set DIR=%%~fi` trick canonicalises `\bin\..`
        // out of DIR to the parent directory — same idiom Gradle's own
        // installDist .bat uses to resolve APP_HOME. Java tolerates the `..`
        // segment but canonicalising once here keeps every classpath entry and
        // error message clean (and matches the loose-directory layout users see
        // after unzipping the release archive).
        val windowsLauncher =
            "@echo off\r\n" +
                "chcp 65001 > NUL 2>&1\r\n" +
                "setlocal\r\n" +
                "set \"DIR=%~dp0..\"\r\n" +
                "for %%i in (\"%DIR%\") do set \"DIR=%%~fi\"\r\n" +
                "\"%DIR%\\runtime\\bin\\java.exe\" -Djava.awt.headless=true -cp \"%DIR%\\lib\\*\" $mainClass %*\r\n"

        doLast {
            val binDir = amyImageDir.get().asFile.resolve("bin")
            binDir.mkdirs()
            val unix = binDir.resolve("amy")
            unix.writeText(unixLauncher)
            unix.setExecutable(true, false)
            val windows = binDir.resolve("amy.bat")
            windows.writeText(windowsLauncher)
        }
    }

fun registerJpackage(
    taskName: String,
    type: String,
    extraArgs: List<String> = emptyList(),
) = tasks.register<Exec>(taskName) {
    group = "distribution"
    description = "Run jpackage --type $type for amy."

    dependsOn(tasks.named("installDist"), jlinkRuntime, verifyAmyRuntimeModules)

    inputs.dir(installLibDir)
    inputs.dir(jlinkRuntimeDir)
    outputs.dir(jpackageOutDir)

    val jpackageBin = javaToolBin("jpackage")
    val inDir = installLibDir
    val runtimeDir = jlinkRuntimeDir
    val outDir = jpackageOutDir
    val versionArg = if (type == "rpm") rpmVersion else appVersion
    val extra = extraArgs

    doFirst {
        outDir.get().asFile.mkdirs()
        executable = jpackageBin.get()
        args(
            "--type", type,
            "--name", "amy",
            "--app-version", versionArg,
            "--vendor", "Amethyst Contributors",
            "--description", "Amethyst CLI — a non-interactive Nostr client.",
            "--input", inDir.get().asFile.absolutePath,
            "--runtime-image", runtimeDir.get().asFile.absolutePath,
            "--main-jar", mainJarName,
            "--main-class", mainClassName,
            "--dest", outDir.get().asFile.absolutePath,
        )
        args(extra)
    }
}

// .deb for Debian/Ubuntu. Installs under /opt/amy/ with /opt/amy/bin/amy as
// the launcher. We intentionally do NOT request --linux-shortcut (no .desktop
// entry for a CLI).
registerJpackage(
    "jpackageDeb",
    "deb",
    extraArgs = listOf(
        "--linux-package-name", "amy",
        "--linux-deb-maintainer", "vitor@vitorpamplona.com",
    ),
)

// .rpm for Fedora/RHEL/openSUSE.
registerJpackage(
    "jpackageRpm",
    "rpm",
    extraArgs = listOf(
        "--linux-package-name", "amy",
        "--linux-rpm-license-type", "MIT",
    ),
)
