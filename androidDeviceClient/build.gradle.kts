import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A Kotlin/JVM client for Android devices and the Android emulator, as Android Studio drives them:
// `adb` for every device (install, launch, input, screenshots, logcat, the UI tree), and the
// emulator's own gRPC control service for a live screen and low-latency input. It starts the SDK
// tools the user installed and bundles nothing from them. Kept free of Amethyst and Nostr types,
// like claudeCodeClient, so `amy` or another tool can use it and it can move to its own repository.
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
    // The device vocabulary and gRPC channel every device client shares.
    api(project(":deviceClientCore"))
    // The emulator's gRPC endpoint is plain HTTP/2 on localhost: OkHttp speaks it with prior
    // knowledge, so no grpc-java or protobuf runtime (the few messages are encoded by hand).
    implementation(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
