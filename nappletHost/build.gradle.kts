import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.vitorpamplona.amethyst.napplethost"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // :amethyst defines a `benchmark` build type (release + profileable) for macrobenchmarks.
        // Declare a matching variant here so the app can resolve this library for benchmark builds.
        create("benchmark") {
            initWith(getByName("release"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // The sandbox runtime depends ONLY on the protocol/contract (commons) and Nostr resolution
    // (quartz) — never on :amethyst. This makes it impossible for the `:napplet` process code to
    // reach for Amethyst.instance / LocalCache / Account (which don't exist in that process).
    implementation(project(":commons"))
    implementation(project(":quartz"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)

    // Provider side of the cross-process UI embedding: hosts the browser WebView in this keyless
    // `:napplet` process and ships its rendered surface to the main app via SurfaceControlViewHost.
    implementation(libs.androidx.privacysandbox.ui.core)
    implementation(libs.androidx.privacysandbox.ui.provider)
}
