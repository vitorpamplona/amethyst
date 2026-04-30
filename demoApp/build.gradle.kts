plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jetbrainsComposeCompiler)
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
}

dependencies {
    // Quartz: Nostr protocol, signer, NostrClient, SQLite event store
    implementation(project(":quartz"))

    // Bundled SQLite driver (JVM): the BundledSQLiteDriver picked up by
    // SQLiteEventStore needs the native library at runtime.
    implementation(libs.androidx.sqlite.bundled.jvm)

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)

    // ViewModel + viewModelScope for the feed state
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Ktor — drives the relay WebSocket
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)

    // Quiet SLF4J warnings from Ktor/transitives
    implementation(libs.slf4j.nop)
}

compose.desktop {
    application {
        mainClass = "com.vitorpamplona.amethyst.demo.MainKt"
        jvmArgs += "-Xmx512m"
    }
}
