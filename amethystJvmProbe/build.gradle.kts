/*
 * JVM readiness gate for the Android app's sources.
 *
 * Compiles `amethyst/src/main/java` — unmoved, in place — for the JVM against
 * :androidStubs and the shared modules. It ships nothing; its only product is
 * the compiler's error list, which is the authoritative, always-current
 * inventory of what still stands between the Android app and a desktop build.
 *
 * Why a probe instead of migrating package by package: the module's package
 * graph is one strongly connected component of 505 packages (98% of its files),
 * so there is no dependency order to migrate in. The work has to be driven by
 * "what does not yet resolve on the JVM", which is exactly what this reports.
 *
 * `amy jvmReadiness` prints the summary; see :amethystJvmProbe:jvmReadiness.
 */
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(21)
    // The whole point is to see the failures, not to stop at the first one.
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }
}

// The probe is expected to FAIL to compile until the port is finished, so it
// must never participate in an ordinary build. Disabling `build` and `check` is
// not enough — Gradle still runs a disabled task's dependencies, so
// `./gradlew test` would reach compileKotlin through testClasses -> classes.
//
// Instead the source set is empty unless explicitly switched on, so a normal
// build compiles nothing here. `jvmReadiness` sets the flag on its own
// invocation.
val readinessEnabled = providers.gradleProperty("amethyst.jvmReadiness").isPresent

sourceSets {
    main {
        kotlin.setSrcDirs(
            if (readinessEnabled) {
                listOf(rootProject.layout.projectDirectory.dir("amethyst/src/main/java"))
            } else {
                emptyList<Any>()
            },
        )
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    compileOnly(project(":androidStubs"))
    implementation(project(":androidxStubs"))
    implementation(project(":quartz"))
    implementation(project(":commons"))
    implementation(project(":amethystShared"))
    // The audio-room client is first-party and already builds for the JVM.
    // :nappletHost is deliberately NOT here: it is an Android-only library, so
    // the napplet sandbox really is a platform gap, not a missing dependency.
    implementation(project(":nestsClient"))

    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.components.resources)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.collection)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.okhttp)
    implementation(libs.okhttpCoroutines)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.zxing)
    implementation(libs.markdown.ui)
    implementation(libs.markdown.ui.material3)
    implementation(libs.markdown.commonmark)
    implementation(libs.highlights)
    // DataStore is multiplatform from 1.1; the app needs no stub for it, just the artifact.
    implementation(libs.androidx.datastore.preferences)
    // AndroidX Navigation ships Android-only; JetBrains publishes the same
    // androidx.navigation.* API for JVM. Apache-2.0.
    implementation(libs.jetbrains.navigation.compose)
}

/**
 * The migration's burn-down: compiles the probe in a separate Gradle
 * invocation (a red compile IS the report, so it must not fail this build) and
 * prints how much of the Android app already compiles for the JVM, plus the
 * unresolved symbols ranked by how many references each one would fix.
 *
 *   ./gradlew :amethystJvmProbe:jvmReadiness
 */
val jvmReadiness by tasks.registering {
    group = "verification"
    description = "Report how much of the Android app already compiles for the JVM, and what blocks the rest."

    val logFile = layout.buildDirectory.file("reports/jvm-readiness.log")
    val reportFile = layout.buildDirectory.file("reports/jvm-readiness.txt")
    val sourceRoot = rootProject.layout.projectDirectory.dir("amethyst/src/main/java")
    val launcher = rootProject.file("gradlew")
    val rootDir = rootProject.projectDir
    outputs.upToDateWhen { false }
    outputs.files(reportFile, logFile)

    doLast {
        val log = logFile.get().asFile
        log.parentFile.mkdirs()
        val process =
            ProcessBuilder(
                launcher.absolutePath,
                "--console=plain",
                "-q",
                "-Pamethyst.jvmReadiness=true",
                ":amethystJvmProbe:compileKotlin",
            ).directory(rootDir)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        process.waitFor()

        val text = log.readText()
        val matches = Regex("""^e: file://(\S+?):(\d+):(\d+) (.*)$""", RegexOption.MULTILINE).findAll(text).toList()
        val unresolvedRe = Regex("""Unresolved reference '([^']+)'""")
        val total = sourceRoot.asFile.walkTopDown().count { it.isFile && it.extension == "kt" }
        val broken = matches.mapTo(HashSet()) { it.groupValues[1] }.size
        val unresolved =
            matches
                .mapNotNull { unresolvedRe.find(it.groupValues[4])?.groupValues?.get(1) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }

        // A build that fails before the compiler runs (dependency resolution, a
        // bad build script) emits no `e: file://` lines at all. Without this
        // check the report would read as a flawless 100%, which is the most
        // dangerous thing a gate can say.
        val compilerRan = matches.isNotEmpty() || text.contains("BUILD SUCCESSFUL")
        if (!compilerRan) {
            val tail = text.lines().takeLast(40).joinToString("\n")
            throw GradleException(
                "jvmReadiness could not measure anything: the probe build failed before the Kotlin " +
                    "compiler ran, so there are no per-file errors to count. Tail of " +
                    "${logFile.get().asFile}:\n$tail",
            )
        }

        val report =
            buildString {
                appendLine("JVM readiness of amethyst/src/main/java")
                appendLine("=".repeat(52))
                appendLine("files compiling clean : ${total - broken} / $total (${if (total == 0) 0 else (total - broken) * 100 / total}%)")
                appendLine("files with errors     : $broken")
                appendLine("total errors          : ${matches.size}")
                appendLine()
                appendLine("Top unresolved symbols - each is one stub, shim or seam:")
                unresolved.take(40).forEach { appendLine("  ${it.value.toString().padStart(6)}  ${it.key}") }
            }
        reportFile.get().asFile.writeText(report)
        println(report)
    }
}


