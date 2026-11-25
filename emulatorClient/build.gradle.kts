import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// An unofficial Kotlin client for the Android Emulator's gRPC `EmulatorController` service: it
// finds running emulators through their discovery files, starts an AVD headless with gRPC on, and
// speaks gRPC itself over OkHttp's cleartext HTTP/2 (no gRPC or protobuf library, no codegen) to
// stream the screen and send touch, mouse and key input. It bundles nothing from the Android SDK.
// Kept free of Amethyst and Nostr types so it can move to its own repository.
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
}

// Built by the JDK Gradle runs on (JDK 25), as Java 21 bytecode, like the other modules: no
// separate JDK 21 toolchain to install.
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
    api(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Apache-2.0 (its POM on Maven Central). A real HTTP/2 server for the end-to-end gRPC tests.
    testImplementation(libs.okhttp.mockwebserver3)
}

// RealEmulatorTest runs only with EMULATOR_IT=1, read from the gradlew call (the test JVM inherits
// the daemon's environment, not the shell's).
tasks.test {
    listOf("EMULATOR_IT", "EMULATOR_IT_LAUNCH", "EMULATOR_IT_ARGS", "EMULATOR_IT_KEEP", "ANDROID_I_WANT_MY_TCG").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
