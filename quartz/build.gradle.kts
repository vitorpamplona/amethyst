plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.vitorpamplona.quartz"
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
            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    packaging {
        resources {
            excludes.add("**/libscrypt.dylib")
        }
    }
    publishing {
        singleVariant("release")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xstring-concat=inline")
    }
    jvm()

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidTarget {
        publishLibraryVariants("release")
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "quartz-kmpKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(project.dependencies.platform(libs.androidx.compose.bom))

                // @Immutable and @Stable
                implementation(libs.androidx.compose.runtime)

                api(libs.secp256k1.kmp.common)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmMain {
            dependencies {
                // Bitcoin secp256k1 bindings
                api(libs.secp256k1.kmp.jni.jvm)

                // Performant Parser of JSONs into Events
                api(libs.jackson.module.kotlin)

                // immutable collections to avoid recomposition
                api(libs.kotlinx.collections.immutable)

                // Parses URLs from Text:
                api(libs.url.detector)

                // Normalizes URLs
                api(libs.rfc3986.normalizer)

                // Websockets API
                implementation(libs.okhttp)
            }
        }

        jvmTest {
            dependencies {
                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)

                // @Immutable and @Stable
                implementation(libs.androidx.compose.runtime)

                // Bitcoin secp256k1 bindings to Android
                api(libs.secp256k1.kmp.jni.android)

                // LibSodium for ChaCha encryption (NIP-44)
                implementation ("com.goterl:lazysodium-android:5.2.0@aar")
                implementation ("net.java.dev.jna:jna:5.17.0@aar")

                // Performant Parser of JSONs into Events
                api(libs.jackson.module.kotlin)

                // immutable collections to avoid recomposition
                api(libs.kotlinx.collections.immutable)

                // Parses URLs from Text:
                api(libs.url.detector)

                // Normalizes URLs
                api(libs.rfc3986.normalizer)

                // Websockets API
                implementation(libs.okhttp)
            }
        }

        androidUnitTest.configure {
            dependencies {
                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        androidInstrumentedTest {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }
}