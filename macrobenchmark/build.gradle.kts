import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidTest)
}

android {
    namespace = "com.vitorpamplona.amethyst.macrobenchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark requires API 23+; we use the app's minSdk which is higher.
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // The macro test apk is instrumentation code, not shipped — no need to
        // minify it. The :amethyst `benchmark` build type is what we actually
        // measure, and it already sets isProfileable=true + debug signing.
        create("benchmark") {
            isDebuggable = false
            isMinifyEnabled = false
            // Match :amethyst's `benchmark` build type so dependency resolution
            // picks the right variant.
            matchingFallbacks += listOf("release")
        }
    }

    // :amethyst has product flavors (play, fdroid); pick one for the target apk.
    targetProjectPath = ":amethyst"
    flavorDimensions += "channel"
    productFlavors {
        create("play") {
            dimension = "channel"
        }
        create("fdroid") {
            dimension = "channel"
        }
    }

    // Tells AGP to build & install the target app's `benchmark` variant.
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.runner)
    implementation(libs.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Macrobenchmark only runs against the `benchmark` build type.
        it.enable = it.buildType == "benchmark"
    }
}
