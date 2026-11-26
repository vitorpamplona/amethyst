import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A Kotlin/JVM client for the iOS Simulator, as Xcode and idb drive it: Apple's `xcrun simctl` for
// the simulators' lives (list, boot, clone, erase, install, launch, logs), and idb's companion
// (`idb_companion`, Meta's, MIT) over gRPC for what simctl has no command for: the live screen,
// touches, keys, typing, rotation, the accessibility tree and crash reports. It starts the tools the
// user installed and bundles nothing from them. Kept free of Amethyst and Nostr types, like
// androidDeviceClient, whose device vocabulary (DeviceScreen, UiTree, the gRPC channel) it shares
// so the device panel drives an iPhone simulator and an Android emulator the same way.
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
}

// Built by the JDK Gradle runs on (JDK 25), as Java 21 bytecode, like the other modules.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    // DeviceScreen, ScreenFrame, DeviceKey, UiTree and the hand-rolled gRPC channel: one vocabulary
    // for every device the panel shows.
    api(project(":androidDeviceClient"))
    implementation(libs.okhttp)
    // simctl and idb answer in JSON: parsed as trees, no generated serializers.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
