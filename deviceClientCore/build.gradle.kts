import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What every device client shares, whatever the device: running a tool (`CommandRunner`), gRPC
// over HTTP/2 without grpc-java (TCP or a Unix socket, unary, server, client and duplex streams;
// messages encoded by hand), the live-view contract (`DeviceScreen`, `ScreenFrame`), keys, the
// screen as a numbered tree (`UiTree`) and crashes (`DeviceCrash`). androidDeviceClient and
// iosSimulatorClient build on it; it knows neither Android nor iOS. No Amethyst or Nostr types.
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
    // gRPC is plain HTTP/2 to a local endpoint: OkHttp speaks it with prior knowledge.
    api(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
