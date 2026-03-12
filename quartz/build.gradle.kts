@file:OptIn(ExperimentalSpmForKmpFeature::class)

import com.vanniktech.maven.publish.KotlinMultiplatform
import io.github.frankois944.spmForKmp.swiftPackageConfig
import io.github.frankois944.spmForKmp.utils.ExperimentalSpmForKmpFeature
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.frankois944.spmForKmp)
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
        namespace = "com.vitorpamplona.quartz"
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

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

        withHostTest {}

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "quartz-kmpKit"
    val libsodiumPath = project.file("src/nativeInterop/libsodium")
    val libsodiumHeaderFilesPath = project.file("$libsodiumPath/include/sodium")

    // This generates the Libsodium definition file, necessary for creating native bindings(a Kotlin API) for libsodium(for iOS).
//    val headersTask = tasks.register<Exec>("ExtractLibsodiumHeaders") {
//        val unpackDirectory = "$libsodiumPath/include"
//        unzipTo(
//            project.file(unpackDirectory),
//            project.file("$unpackDirectory/sodium.zip")
//        )
//    }

    val libsodiumDefFileGeneration = tasks.register<Exec>("GenerateSodiumCinteropFile") {
        val definitionFile = project.file("src/nativeInterop/cinterop/Clibsodium.def")
        if (!definitionFile.exists()){
            definitionFile.writeText("package = Clibsodium")
            definitionFile.appendText("\n" + "staticLibraries = libsodium.a libsodium-simulator.a")
            definitionFile.appendText("\n" + "libraryPaths = ${libsodiumPath}/ios/lib ${libsodiumPath}/ios-simulators/lib")
        }
    }

    listOf(
        iosArm64(),
        iosX64(),
        iosSimulatorArm64(),
    ).forEach { target ->

        target.compilations.getByName("main") {
//            headersTask.get()
            libsodiumDefFileGeneration.get()
            val Clibsodium by cinterops.creating {
                assert(project.file("Clibsodium.def").exists())
                definitionFile.set(project.file("Clibsodium.def"))
                includeDirs("${libsodiumPath}/ios-simulators/lib")
                //compilerOpts("-I${libsodiumPath}/ios-simulators/lib")

                headers(
                    "$libsodiumHeaderFilesPath/crypto_aead_xchacha20poly1305.h",
                    "$libsodiumHeaderFilesPath/crypto_core_hchacha20.h",
                    "$libsodiumHeaderFilesPath/crypto_stream_chacha20.h"
                )
            }
        }

        target.swiftPackageConfig(cinteropName = "swiftbridge") {
            minIos = "17"
            minMacos = "14"
            dependency {
                remotePackageVersion(
                    url = uri("https://github.com/swift-standards/swift-rfc-3986.git"),
                    packageName = "swift-rfc-3986",
                    products = {
                        add("RFC 3986")
                    },
                    version = "0.1.0",
                )
            }
        }
    }

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

    // This makes sure that the resource file directory is visible for iOS tests.
    val rootDir = "${rootProject.rootDir.path}/quartz/src/iosTest/resources"

    tasks.withType<Test>().configureEach {
        environment("TEST_RESOURCES_ROOT", rootDir)
    }

    tasks.withType<KotlinNativeTest>().configureEach {
        environment("TEST_RESOURCES_ROOT", rootDir)
        // This is necessary to have the variable propagated on iOS
        environment("SIMCTL_CHILD_TEST_RESOURCES_ROOT", rootDir)
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
                implementation(libs.kotlinx.coroutines.core)

                // For LruCache
                implementation(libs.androidx.collection)

                // @Immutable and @Stable
                implementation(libs.androidx.compose.runtime.annotation)

                // Bitcoin secp256k1 bindings
                api(libs.secp256k1.kmp.common)

                // Kotlin serialization for the times where we need the Json tree and performance is not that important.
                implementation(libs.kotlinx.serialization.json)

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
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())

                dependencies {
                    // Normalizes URLs
                    api(libs.rfc3986.normalizer)

                    // Performant Parser of JSONs into Events
                    api(libs.jackson.module.kotlin)

                    // Websockets API
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)

                    // Chess engine for move validation and legal move generation
                    // NOTE: 1.0.4+ uses Java 21's removeLast() which crashes on Android API < 34
                    // TODO: Test if 1.0.0 works, or fork library to fix
                    implementation(libs.kchesslib)
                }
            }

        // Must be defined before androidMain and jvmMain
        val jvmAndroidTest =
            create("jvmAndroidTest") {
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
                implementation(libs.secp256k1.kmp.jni.jvm)

                // LibSodium for ChaCha encryption (NIP-44)
                implementation(libs.lazysodium.java)
                implementation(libs.jna)
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

                // Bitcoin secp256k1 bindings to Android
                api(libs.secp256k1.kmp.jni.android)

                // LibSodium for ChaCha encryption (NIP-44)
                implementation("com.goterl:lazysodium-android:5.2.0@aar")
                implementation("net.java.dev.jna:jna:5.18.1@aar")
            }
        }

        getByName("androidHostTest") {
            dependencies {
                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        getByName("androidDeviceTest") {
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
                implementation(libs.charlietap.cachemap)
                implementation(libs.net.thauvin.erik.urlencoder.lib)
                implementation(libs.dev.whyoleg.cryptography.provider.apple.optimal)
                implementation("io.github.andreypfau:kotlinx-crypto-hmac:0.0.4")
                implementation("io.github.andreypfau:kotlinx-crypto-sha2:0.0.4")
            }
        }

        val iosX64Main by getting {
            dependsOn(iosMain.get())
        }

        val iosArm64Main by getting {
            dependsOn(iosMain.get())
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain.get())
        }

        iosTest {
            dependsOn(commonTest.get())
            dependencies {
            }
        }

        val iosX64Test by getting {
            dependsOn(iosTest.get())
        }

        val iosArm64Test by getting {
            dependsOn(iosTest.get())
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest.get())
        }
    }
}

mavenPublishing {
    // sources publishing is always enabled by the Kotlin Multiplatform plugin
    configure(
        KotlinMultiplatform(
            // whether to publish a sources jar
            sourcesJar = true,
        ),
    )

    coordinates(
        groupId = "com.vitorpamplona.quartz",
        artifactId = "quartz",
        version = "1.05.1",
    )

    // Configure publishing to Maven Central
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
