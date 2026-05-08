import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.serialization)
    application
    `java-test-fixtures`
}

application {
    mainClass.set("com.vitorpamplona.geode.MainKt")
    applicationName = "geode"
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
    // The `java-test-fixtures` plugin auto-creates a `testFixtures`
    // source set; we just point it at our Kotlin layout so the
    // `geode.fixtures` (synthetic events) and `geode.testing`
    // (RelayClientTest, collectUntilEose) packages don't ship in
    // production jars but are still usable by every consumer's test
    // source via `testImplementation(testFixtures(project(":geode")))`.
    named("testFixtures") {
        kotlin.srcDir("src/testFixtures/kotlin")
    }
}

tasks.withType<Test>().configureEach {
    // Forward `-DrunLoadBenchmark=true` to the test JVM so the
    // perf.LoadBenchmark tests opt in. Off by default — load tests
    // are noisy and slow.
    systemProperty("runLoadBenchmark", System.getProperty("runLoadBenchmark") ?: "false")
    System.getProperty("fanoutScalingEvents")?.let { systemProperty("fanoutScalingEvents", it) }
    System.getProperty("fanoutScalingSubs")?.let { systemProperty("fanoutScalingSubs", it) }
    // Show println output from test JVM so the benchmark numbers are
    // actually visible without grepping the report XML.
    testLogging {
        showStandardStreams =
            (System.getProperty("runLoadBenchmark") == "true")
        events("standard_out")
    }
}

dependencies {
    api(project(":quartz"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)

    // Bundled SQLite driver — Relay's default in-memory EventStore creates
    // an in-memory DB at runtime.
    implementation(libs.androidx.sqlite.bundled.jvm)

    // Ktor server engine + WebSocket plugin so Relay can serve real ws://
    // traffic. CIO is the coroutine-based engine — lighter than Netty.
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.websockets)

    // TOML parsing for the operator config file. Mirrors the section
    // layout of nostr-rs-relay's config.toml so existing operators can
    // port their configs nearly verbatim.
    implementation(libs.fourkoma)

    // testFixtures: code in src/testFixtures/kotlin (RelayClientTest +
    // synthetic event builders). Not shipped in the production jar but
    // exposed to consumers via testImplementation(testFixtures(...)).
    testFixturesApi(project(":quartz"))
    testFixturesApi(libs.junit)
    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.secp256k1.kmp.jni.jvm)
    testImplementation(libs.okhttp)
}
