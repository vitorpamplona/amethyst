import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    application
}

application {
    mainClass.set("com.vitorpamplona.quartz.relay.MainKt")
    applicationName = "quartz-relay"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
    }
    test {
        kotlin.srcDir("src/test/kotlin")
    }
}

dependencies {
    api(project(":quartz"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.module.kotlin)

    // Bundled SQLite driver — Relay's default in-memory EventStore creates
    // an in-memory DB at runtime.
    implementation(libs.androidx.sqlite.bundled.jvm)

    // Ktor server engine + WebSocket plugin so Relay can serve real ws://
    // traffic. CIO is the coroutine-based engine — lighter than Netty.
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.websockets)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)
    testImplementation(libs.okhttp)
}
