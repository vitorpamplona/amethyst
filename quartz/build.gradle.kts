import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
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

    android {
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

    iosArm64 {
        binaries.framework {
            baseName = xcfName
            isStatic = true
            binaryOption("bundleId", "com.vitorpamplona.quartz")
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
            isStatic = true
            binaryOption("bundleId", "com.vitorpamplona.quartz")
        }
    }

    linuxX64()

    // This makes sure that the resource file directory is visible for iOS tests.
    val rootDir = "${rootProject.rootDir.path}/quartz/src/commonTest/resources"

    tasks.withType<Test>().configureEach {
        maxHeapSize = "4g"
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

                // SQLite KMP driver for event store
                api(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.bundled)

                // Negentropy set reconciliation (NIP-77)
                api(libs.negentropy.kmp)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)

                // SQLite bundled driver for tests
                api(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        // Must be defined before androidMain and jvmMain
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())

                dependencies {
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
            }
        }

        jvmTest {
            dependsOn(jvmAndroidTest)
            dependencies {
                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)

                // SQLite bundled driver for JVM tests
                implementation(libs.androidx.sqlite.bundled.jvm)
            }
        }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                implementation(libs.androidx.core.ktx)

                // Bitcoin secp256k1 bindings to Android
                api(libs.secp256k1.kmp.jni.android)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)

                // Bitcoin secp256k1 bindings
                implementation(libs.secp256k1.kmp.jni.jvm)

                // SQLite bundled driver for Host tests
                implementation(libs.androidx.sqlite.bundled.jvm)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)

                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)

                // Bitcoin secp256k1 bindings to Android
                api(libs.secp256k1.kmp.jni.android)
            }
        }

        // Must be defined before appleMain, linuxMain, etc.
        val nativeMain =
            create("nativeMain") {
                dependsOn(commonMain.get())
            }

        val nativeTest =
            create("nativeTest") {
                dependsOn(commonTest.get())
            }

        // Must be defined before iosMain and macosMain
        val appleMain =
            create("appleMain") {
                dependsOn(nativeMain)
                dependencies {
                    implementation(libs.charlietap.cachemap)
                    implementation(libs.net.thauvin.erik.urlencoder.lib)
                    implementation(libs.dev.whyoleg.cryptography.provider.apple.optimal)
                    implementation("io.github.andreypfau:kotlinx-crypto-hmac:0.0.4")
                    implementation("io.github.andreypfau:kotlinx-crypto-sha2:0.0.4")
                }
            }

        val appleTest =
            create("appleTest") {
                dependsOn(nativeTest)
            }

        iosMain {
            dependsOn(appleMain)
        }

        val iosArm64Main by getting {
            dependsOn(iosMain.get())
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain.get())
        }

        iosTest {
            dependsOn(appleTest)
        }

        val iosArm64Test by getting {
            dependsOn(iosTest.get())
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest.get())
        }

        val linuxMain =
            create("linuxMain") {
                dependsOn(nativeMain)
                dependencies {
                    implementation(libs.net.thauvin.erik.urlencoder.lib)
                    implementation(libs.dev.whyoleg.cryptography.provider.apple.optimal)
                    implementation("io.github.andreypfau:kotlinx-crypto-hmac:0.0.4")
                    implementation("io.github.andreypfau:kotlinx-crypto-sha2:0.0.4")
                }
            }

        val linuxTest =
            create("linuxTest") {
                dependsOn(nativeTest)
            }

        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }

        val linuxX64Test by getting {
            dependsOn(linuxTest)
        }
    }
}

mavenPublishing {
    // sources publishing is always enabled by the Kotlin Multiplatform plugin
    configure(
        KotlinMultiplatform(
            // whether to publish a sources jar
            sourcesJar = SourcesJar.Sources(),
        ),
    )

    coordinates(
        groupId = "com.vitorpamplona.quartz",
        artifactId = "quartz",
        version = "1.08.0",
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
