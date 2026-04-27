import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.nestsclient"
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
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(project(":quartz"))
                implementation(project(":quic"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(libs.okhttp)
                    implementation(libs.okhttpCoroutines)
                }
            }

        jvmMain {
            dependsOn(jvmAndroid)
        }

        androidMain {
            dependsOn(jvmAndroid)
            // Kwik QUIC + Flupke HTTP/3 dependencies are NOT yet declared.
            // See KwikWebTransportFactory.kt for the integration plan and
            // validated Maven coordinates / minimum versions before adding.
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }
    }
}

// Forward the nostrnests interop opt-in property from the Gradle JVM
// to test workers. Without this, `-DnestsInterop=true` on the Gradle
// command line never reaches `NostrNestsHarness.isEnabled()` (which
// reads it via `System.getProperty`), so every interop test silently
// skips. See `nestsClient/src/jvmTest/.../interop/NostrNestsHarness.kt`.
tasks.withType<Test>().configureEach {
    System.getProperty("nestsInterop")?.let { systemProperty("nestsInterop", it) }
    System.getProperty("nestsInteropRev")?.let { systemProperty("nestsInteropRev", it) }
    System.getProperty("nestsInteropMoqRev")?.let { systemProperty("nestsInteropMoqRev", it) }
    System.getProperty("nestsInteropExternal")?.let { systemProperty("nestsInteropExternal", it) }
    System.getProperty("nestsInteropDebug")?.let { systemProperty("nestsInteropDebug", it) }
}
