import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A headless runtime for 128x128 fantasy-console cartridges (.p8, .p8.png): the Lua dialect,
// memory, graphics, sound and cart loading. Pure Kotlin over the stdlib only, so it can be lifted
// into its own repository and published like the other libraries in AGENTS.md. The Compose
// screen that shows it lives in commonsUI, not here.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.fantasyconsole"
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

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Forwards the opt-in cart smoke test's settings to the test JVM (see CartSmokeTest).
tasks.withType<Test>().configureEach {
    listOf("fantasyConsole.carts", "fantasyConsole.out", "fantasyConsole.frames", "fantasyConsole.only").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    outputs.upToDateWhen { System.getProperty("fantasyConsole.carts") == null }
    testLogging.showStandardStreams = System.getProperty("fantasyConsole.carts") != null
    System.getProperty("fantasyConsole.jfr")?.let { jvmArgs("-XX:StartFlightRecording=filename=$it,settings=profile") }
}
