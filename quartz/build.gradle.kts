import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
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
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "quartz-kmpKit"

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

                // Bitcoin secp256k1 bindings
                api(libs.secp256k1.kmp.common)

                // Kotlin serialization for the times where we need the Json tree and performance is not that important.
                implementation(libs.kotlinx.serialization.json)

                // in your shared module's dependencies block
                // implementation( libs.kotlinx.datetime)

                // immutable collections to avoid recomposition
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Must be defined before androidMain and jvmMain
        val jvmAndroid = create("jvmAndroid") {
            dependsOn(commonMain.get())

            dependencies {
                // For LruCache
                implementation(libs.androidx.collection.jvm)

                // Normalizes URLs
                api(libs.rfc3986.normalizer)

                // Performant Parser of JSONs into Events
                api(libs.jackson.module.kotlin)

                // Parses URLs from Text:
                api(libs.url.detector)

                // Websockets API
                implementation(libs.okhttp)
                implementation(libs.okhttpCoroutines)
            }
        }

        // Must be defined before androidMain and jvmMain
        val jvmAndroidTest = create("jvmAndroidTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                // Bitcoin secp256k1 bindings
                api(libs.secp256k1.kmp.jni.jvm)

                // LibSodium for ChaCha encryption (NIP-44)
                implementation (libs.lazysodium.java)
                implementation (libs.jna)
            }
        }

        jvmTest {
            dependsOn(jvmAndroidTest)
            dependencies {
                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                implementation(libs.androidx.core.ktx)

                // @Immutable and @Stable
                implementation(libs.androidx.compose.runtime)

                // Bitcoin secp256k1 bindings to Android
                api(libs.secp256k1.kmp.jni.android)

                // LibSodium for ChaCha encryption (NIP-44)
                implementation ("com.goterl:lazysodium-android:5.2.0@aar")
                implementation ("net.java.dev.jna:jna:5.18.1@aar")
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
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        iosMain {
            dependsOn(commonMain.get())
            dependencies {

            }
        }

        val iosArm64Main by getting {
            dependsOn(iosMain.get()) // iosArm64Main depends on iosMain
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain.get()) // iosSimulatorArm64Main depends on iosMain
        }

        iosTest {
            dependsOn(commonTest.get())
            dependencies {

            }
        }

        val iosArm64Test by getting {
            dependsOn(iosTest.get()) // iosArm64Main depends on iosMain
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest.get()) // iosSimulatorArm64Main depends on iosMain
        }
    }
}


mavenPublishing {
    // sources publishing is always enabled by the Kotlin Multiplatform plugin
    configure(
        KotlinMultiplatform(
            // whether to publish a sources jar
            sourcesJar = true,
            // configure which Android library variants to publish if this project has an Android target
            // defaults to "release" when using the main plugin and nothing for the base plugin
            androidVariantsToPublish = listOf("release"),
        )
    )

    coordinates(
        groupId = "com.vitorpamplona.quartz",
        artifactId = "quartz",
        version = "1.04.0"
    )

    // Configure publishing to Maven Central
    //publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    publishToMavenCentral(automaticRelease = true)

    // Enable GPG signing for all publications
    signAllPublications()

    pom {
        name = "Quartz: Nostr Library for Kotlin Multiplatform"
        description = "Nostr library ported to Kotlin/Multiplatform for JVM, Android, iOS & Linux"
        inceptionYear = "2025"
        url = "https://github.com/vitorpamplona/amethyst/"
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/vitorpamplona/amethyst/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = "vitorpamplona"
                name = "Vitor Pamplona"
                url = "http://vitorpamplona.com"
                email = "vitor@vitorpamplona.com"
            }
        }
        scm {
            url = "https://github.com/vitorpamplona/amethyst/"
            connection = "https://github.com/vitorpamplona/amethyst/.git"
        }
    }
}