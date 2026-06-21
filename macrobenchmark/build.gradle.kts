import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidTest)
}

android {
    namespace = "com.vitorpamplona.amethyst.macrobenchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        // Macrobenchmark needs a profileable/debuggable target and reliable frame
        // metrics, which require API 28+. The :amethyst benchmark variant is built
        // with isProfileable = true to satisfy this.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Allow running on an emulator / low-battery device. Frame numbers from an
        // emulator are NOT authoritative (its GPU is the whole reason we want a real
        // device) — this is only so the journey can be smoke-tested and run in CI.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"

        // :amethyst declares a "channel" flavor dimension (play/fdroid). The
        // benchmark is flavor-agnostic, so bind it to the play target.
        missingDimensionStrategy("channel", "play")
    }

    buildTypes {
        // Mirrors the app's "benchmark" build type (release-like, debug-signed) so
        // this test module installs and measures against :amethyst's playBenchmark
        // variant rather than a plain debug build.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":amethyst"
    // Run the instrumentation inside the same process partition as required by
    // Macrobenchmark's self-instrumenting model.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only the benchmark variant is meaningful for this module.
        it.enable = it.buildType == "benchmark"
    }
}
