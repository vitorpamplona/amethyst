import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    application
    alias(libs.plugins.kotlinxKover)
}

application {
    mainClass.set("com.vitorpamplona.relaybench.MainKt")
    applicationName = "relaybench"
    // Percentile latencies get skewed by GC pauses in the *client* — give the
    // harness enough heap that it never becomes the bottleneck being measured.
    // The sync phase also materializes the whole corpus as Event objects (plus
    // a dedup map) to derive the 80% slices, so a million-event run needs more
    // than 2g or `SyncBenchmark.effectiveEvents` OOMs. -Xmx is a ceiling, not a
    // reservation, so small runs don't pay for it. Override with JAVA_OPTS.
    applicationDefaultJvmArgs = listOf("-Xmx8g")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // Event model, Schnorr signing, Filter + wire-format JSON. The harness
    // reuses quartz so the corpus is byte-identical to what Amethyst emits.
    implementation(project(":quartz"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)

    // JNI secp256k1 backend quartz needs at runtime on plain JVM.
    runtimeOnly(libs.secp256k1.kmp.jni.jvm)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)
}
