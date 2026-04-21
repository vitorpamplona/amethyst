import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    application
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
        resources.srcDir("src/main/resources")
    }
}

dependencies {
    implementation(project(":quartz"))
    implementation(project(":commons"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.nop)
}

application {
    mainClass.set("com.vitorpamplona.amethyst.cli.MainKt")
    applicationName = "amy"
}
