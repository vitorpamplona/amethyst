import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
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
}

dependencies {
    implementation(project(":quartz"))
    implementation(project(":commons"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.nop)
}

application {
    mainClass.set("com.vitorpamplona.amethyst.cli.MainKt")
    applicationName = "amy"
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
// megabytes to the bundle. If a transitive dep needs more, `jlink` fails loudly
// at build time with "module X not found".
val jlinkModules: String = listOf(
    "java.base",
    "java.logging",
    "java.naming",
    "java.net.http",
    "java.sql",
    "java.xml",
    "jdk.crypto.ec",
    "jdk.unsupported",
).joinToString(",")

fun javaToolBin(name: String): Provider<String> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map {
        val exe = if (org.gradle.internal.os.OperatingSystem.current().isWindows) "$name.exe" else name
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

// Flat app-image: bin/amy launcher + lib/*.jar + runtime/ (the jlink'd JRE).
// Cross-platform — the release workflow tars this up on every OS.
val amyImage =
    tasks.register<Sync>("amyImage") {
        group = "distribution"
        description = "Assemble a portable amy app-image (bin/ + lib/ + runtime/)."

        dependsOn(tasks.named("installDist"), jlinkRuntime)

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
            exec "${'$'}DIR/runtime/bin/java" -cp "${'$'}DIR/lib/*" $mainClass "${'$'}@"
            """.trimIndent() + "\n"

        doLast {
            val binDir = amyImageDir.get().asFile.resolve("bin")
            binDir.mkdirs()
            val launcher = binDir.resolve("amy")
            launcher.writeText(unixLauncher)
            launcher.setExecutable(true, false)
        }
    }

fun registerJpackage(
    taskName: String,
    type: String,
    extraArgs: List<String> = emptyList(),
) = tasks.register<Exec>(taskName) {
    group = "distribution"
    description = "Run jpackage --type $type for amy."

    dependsOn(tasks.named("installDist"), jlinkRuntime)

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
