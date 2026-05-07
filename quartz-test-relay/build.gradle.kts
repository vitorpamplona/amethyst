import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
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

    // Bundled SQLite driver — EventStore(null) creates an in-memory DB at runtime.
    implementation(libs.androidx.sqlite.bundled.jvm)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)
}
