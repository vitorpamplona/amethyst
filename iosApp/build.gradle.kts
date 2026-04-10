import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "AmethystIos"
            isStatic = true
            binaryOption("bundleId", "com.vitorpamplona.amethyst.ios")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":quartz"))
                implementation(project(":commons"))

                // Compose Multiplatform
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(compose.material3)
                implementation(compose.components.resources)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Image loading
                implementation(libs.coil.compose)
                implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil.get()}")
                implementation("io.ktor:ktor-client-darwin:3.1.3")

                // Collections
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.androidx.collection)
            }
        }
    }
}
