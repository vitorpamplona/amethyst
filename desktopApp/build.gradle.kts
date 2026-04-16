import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.nio.file.Files

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
    id("ir.mahozad.vlc-setup") version "0.1.0"
}

// RPM rejects dashes in version strings — strip prerelease suffix for Linux RPM only.
// Other formats accept full semver (DEB uses ~rc1, DMG/MSI accept bare versions).
val appVersion: String = project.version.toString()
val appVersionRpm: String = appVersion.substringBefore("-")

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
    implementation(libs.jetbrains.compose.material.icons.extended)
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

    // Video playback
    implementation(libs.vlcj)

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
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"
        jvmArgs += "--add-opens=java.base/java.nio=ALL-UNNAMED"

        jvmArgs += "-Xmx2g"

        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/jvmMain/appResources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            modules("java.management") // Required by kmp-tor TorRuntime

            packageName = "Amethyst"
            packageVersion = appVersion
            description = "Nostr client for desktop"
            vendor = "Amethyst Contributors"

            macOS {
                bundleID = "com.vitorpamplona.amethyst.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
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
                rpmLicenseType = "MIT"
                // RPM version field rejects dashes; strip prerelease suffix for RPM builds.
                rpmPackageVersion = appVersionRpm
            }
        }
    }
}

vlcSetup {
    vlcVersion.set("3.0.21")
    shouldCompressVlcFiles.set(true)
    shouldIncludeAllVlcFiles.set(true)
    pathToCopyVlcLinuxFilesTo.set(file("src/jvmMain/appResources/linux/vlc"))
    pathToCopyVlcMacosFilesTo.set(file("src/jvmMain/appResources/macos/vlc"))
    pathToCopyVlcWindowsFilesTo.set(file("src/jvmMain/appResources/windows/vlc"))
}

tasks.named("spotlessKotlin") {
    mustRunAfter("vlcSetup")
}

// --- AppImage packaging (Linux) ---
//
// Compose Multiplatform's TargetFormat.AppImage is known-broken in 1.10.x (CMP-7101).
// Instead: wrap `createReleaseDistributable` output with `linuxdeploy` (which
// auto-bundles libraries, handles rpath, and calls appimagetool internally).
//
// Build inputs live in packaging/appimage/:
//   - AppRun              shell launcher (sets LD_LIBRARY_PATH including bundled VLC)
//   - amethyst.desktop    XDG desktop entry
//   - amethyst.png        512x512 icon
//
// linuxdeploy binary is fetched by CI (SHA-verified) into packaging/appimage/
// as linuxdeploy-x86_64.AppImage. BUILDING.md documents local-dev fetch.
val createReleaseAppImage by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Bundle createReleaseDistributable output into a Linux AppImage via linuxdeploy."
    dependsOn("createReleaseDistributable")

    val distDir = layout.buildDirectory.dir("compose/binaries/main-release/app/Amethyst")
    val appDir = layout.buildDirectory.dir("appimage/Amethyst.AppDir")
    val outFile = layout.buildDirectory.file("appimage/Amethyst-$appVersion-x86_64.AppImage")
    val toolRoot = layout.projectDirectory.dir("../packaging/appimage")
    val linuxdeployTool = toolRoot.file("linuxdeploy-x86_64.AppImage")

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

        if (!linuxdeployTool.asFile.canExecute()) {
            linuxdeployTool.asFile.setExecutable(true)
        }
    }

    commandLine(
        linuxdeployTool.asFile.absolutePath,
        "--appdir", appDir.get().asFile.absolutePath,
        "--output", "appimage",
        "--desktop-file", "${appDir.get().asFile}/amethyst.desktop",
        "--icon-file", "${appDir.get().asFile}/amethyst.png",
    )
    environment("OUTPUT", outFile.get().asFile.absolutePath)
    environment("ARCH", "x86_64")
    // Suppress linuxdeploy's verbose library-scanner output; keep errors.
    environment("LINUXDEPLOY_OUTPUT_VERSION", appVersion)
}
