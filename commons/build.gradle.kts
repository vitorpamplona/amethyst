import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.mokoResources)
}

android {
    namespace = "com.vitorpamplona.amethyst.commons"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xstring-concat=inline")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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

                // Moko Resources for KMP string resources
                api(libs.moko.resources)
                api(libs.moko.resources.compose)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        // Shared JVM code for both Android and Desktop
        val jvmAndroid = create("jvmAndroid") {
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

        androidUnitTest {
            dependencies {
                implementation(libs.junit)
            }
        }

        androidInstrumentedTest {
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }
    }
}

multiplatformResources {
    resourcesPackage.set("com.vitorpamplona.amethyst.commons")
    resourcesClassName.set("SharedRes")
}
