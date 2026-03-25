import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
    id("ir.mahozad.vlc-setup") version "0.1.0"
}

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

        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/jvmMain/appResources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "Amethyst"
            packageVersion = "1.0.0"
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
