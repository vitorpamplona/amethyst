import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    alias(libs.plugins.androidxBaselineProfile)
}

android {
    namespace = "com.vitorpamplona.amethyst.baselineprofile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Baseline Profile generation needs API 28+; non-rooted generation needs API 33+.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // :amethyst has a `channel` dimension (play/fdroid). The recorded journey is the
        // same either way — ingest does not differ by store — so record against play and
        // let the consumer's mergeIntoMain share one profile with both flavours.
        missingDimensionStrategy("channel", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // The app whose journeys are recorded.
    targetProjectPath = ":amethyst"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

baselineProfile {
    // Generate against a connected device. Macrobenchmark 1.2.0-alpha06+ can do this
    // without root on API 33+; below that it needs root or an `aosp` Gradle Managed Device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
