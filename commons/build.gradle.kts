
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    androidLibrary {
        namespace = "com.vitorpamplona.amethyst.commons"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        withHostTest {}

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":quartz"))

                // Compose Multiplatform
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.runtime)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.uiToolingPreview)

                // Lifecycle ViewModel (KMP since 2.8.0)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)

                // Image loading (Coil 3 - KMP)
                implementation(libs.coil.compose)
                implementation(libs.coil.okhttp)

                // LruCache (KMP-ready)
                implementation(libs.androidx.collection)

                // Immutable collections
                api(libs.kotlinx.collections.immutable)

                // Compose Multiplatform Resources
                implementation(compose.components.resources)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        // Shared JVM code for both Android and Desktop
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    // URL detection (JVM library, works on both)
                    implementation(libs.url.detector)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Desktop-specific Compose
                implementation(compose.desktop.currentOs)
                implementation(compose.uiTooling)

                // Secure key storage via OS keychain (macOS/Windows/Linux)
                implementation(libs.java.keyring)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Android-specific Compose tooling
                implementation(libs.androidx.ui.tooling.preview)

                // Secure key storage via Android Keystore
                implementation(libs.androidx.security.crypto.ktx)
                implementation(libs.androidx.datastore.preferences)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.vitorpamplona.amethyst.commons.resources"
    generateResClass = always
}
