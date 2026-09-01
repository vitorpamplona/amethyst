import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
}

android {
    namespace = "com.vitorpamplona.amethyst.macrobenchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark needs API 29+; frame metrics from `dumpsys gfxinfo` need 29+.
        minSdk = 29
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The SM-T220 and the Pixel 9 AVD are both legitimate targets here: the emulator
        // is suppressed from erroring out, and the tablet is often not fully charged.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,LOW-BATTERY,DEBUGGABLE,NOT-PROFILEABLE,ENG-BUILD"

        // :amethyst has a `channel` dimension (play/fdroid). Rendering does not differ by
        // store, so measure against play.
        missingDimensionStrategy("channel", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Run against the profileable, R8-optimized `benchmark` variant of the app. Measuring
    // a debug build tells you about ART's interpreter, not about the app's own code.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    targetProjectPath = ":amethyst"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
