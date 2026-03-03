import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
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
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

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

    // Collections
    implementation(libs.kotlinx.collections.immutable)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.desktop.MainKt"

        nativeDistributions {
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
