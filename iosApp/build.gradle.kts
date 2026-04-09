plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AmethystIos"
            isStatic = true
            binaryOption("bundleId", "com.vitorpamplona.amethyst.ios.framework")
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Quartz Nostr library (already has iOS targets)
            implementation(project(":quartz"))

            // Commons library (now has iOS targets)
            implementation(project(":commons"))

            // Compose Multiplatform
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.components.resources)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Image loading (Coil 3 — KMP + Ktor for iOS networking)
            implementation(libs.coil.compose)
            implementation(libs.coil.ktor)

            // Immutable collections
            implementation(libs.kotlinx.collections.immutable)

            // LruCache
            implementation(libs.androidx.collection)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
