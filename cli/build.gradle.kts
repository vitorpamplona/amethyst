plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.serialization)
    application
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
    test {
        kotlin.srcDir("src/test/kotlin")
        resources.srcDir("src/test/resources")
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.vitorpamplona.amethyst.cli.MainKt")
}

dependencies {
    // Quartz Nostr library (JVM target)
    implementation(project(":quartz"))

    // CLI argument parsing
    implementation(libs.clikt)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Networking (relay connections)
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)

    // JSON
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Bitcoin secp256k1 bindings
    implementation(libs.secp256k1.kmp.jni.jvm)

    // SQLite for state storage
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled.jvm)

    // Logging
    implementation(libs.slf4j.nop)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
