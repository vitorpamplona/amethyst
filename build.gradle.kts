import com.android.build.gradle.tasks.GenerateResValues
import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare
import java.util.Properties

// Local SonarQube analysis is opt-in: it activates only when `sonar.host.url`
// is present in local.properties (gitignored) AND a sonar task was requested,
// so neither developers who haven't opted in nor ordinary builds/IDE syncs of
// opted-in developers resolve or apply the scanner plugin. The Kotlin DSL
// compiles this buildscript {} section in an earlier stage that can't see the
// file's imports (hence the qualified Properties) or share code with the body,
// but it can publish values — the gate is computed once here and read below
// via the project's extra properties.
buildscript {
    val localProperties = File(rootDir, "local.properties")
    val sonarProperties =
        java.util.Properties().apply {
            if (localProperties.exists()) localProperties.inputStream().use { load(it) }
        }
    extra.set("sonarProperties", sonarProperties)
    val sonarEnabled =
        sonarProperties.getProperty("sonar.host.url") != null &&
            gradle.startParameter.taskNames.any { it.substringAfterLast(":") in setOf("sonar", "sonarqube") }
    extra.set("sonarEnabled", sonarEnabled)
    if (sonarEnabled) {
        repositories {
            gradlePluginPortal()
        }
        dependencies {
            // LGPL-3.0, build-time only — never linked into shipped artifacts.
            classpath(libs.sonarqube.gradle.plugin)
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.androidBenchmark) apply false
    alias(libs.plugins.diffplugSpotless)
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.jetbrainsComposeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleKsp) apply false
}

// Shared app version for all subprojects — read from gradle/libs.versions.toml.
// Android versionCode is the `appCode` entry in the same catalog (must be monotonic int).
// Desktop packageVersion inherits via project.version in desktopApp/build.gradle.kts.
val appVersion = libs.versions.app.get()

allprojects {
    version = appVersion

    configurations.configureEach {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }

    apply(plugin = "com.diffplug.spotless")

    if (project === rootProject) {
        spotless {
            predeclareDeps()
        }
        configure<SpotlessExtensionPredeclare> {
            kotlin {
                ktlint("1.7.1")
            }
        }
    } else {
        spotless {
            kotlin {
                target("src/**/*.kt")
                // Third-party sources vendored verbatim keep their own license header and
                // formatting. Stamping our MIT header onto someone else's Apache-2.0 file
                // would misstate its provenance, and reformatting it would make the next
                // re-vendor a merge conflict instead of a copy.
                targetExclude("src/main/java/zxingcpp/**/*.kt")

                ktlint("1.7.1")
                licenseHeaderFile(
                    rootProject.file(".spotless/copyright.kt"),
                    "@file:|package|import|class|object|sealed|open|interface|abstract ",
                )
            }

            kotlinGradle {
                target("*.gradle.kts")
            }
        }
    }
}

subprojects {
    afterEvaluate {
        // The Kotlin Multiplatform plugin never registers a plain `test` task: it creates one
        // task per target (jvmTest, androidUnitTest, linuxX64Test, ...) plus the `allTests`
        // aggregate. A root `./gradlew test` therefore runs `test` only in the Java/Android
        // modules that own one and *silently* skips every KMP module - Gradle only errors when
        // no project at all has the task, and four of them do, so it exits 0 looking healthy.
        // That hid ~8k tests (quartz, commons, commonsUI, quic, nestsClient, marmotQuic), which
        // is most of this repo's suite. Register the alias so the documented command means what
        // it says.
        //
        // It maps to jvmTest, not allTests, on purpose: allTests also drags in androidUnitTest
        // and the native targets, half of which cannot run on a given host (macosArm64Test on
        // Linux) and all of which change what `test` costs. jvmTest is exactly what the
        // pre-push hook and CI already run for these modules, so the alias matches the coverage
        // they expect rather than inventing a third definition of "the tests".
        //
        // The flip side: a KMP module's non-JVM targets stay outside `test`. :quartz's
        // androidHostTest source set (~3.9k tests) is the big one - nothing runs it today and it
        // is red on main, so aliasing onto allTests would have turned `test` red for everyone
        // rather than fixing anything. Tracked in CLAUDE.md's Build Commands section.
        if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") &&
            tasks.findByName("test") == null &&
            tasks.findByName("jvmTest") != null
        ) {
            tasks.register("test") {
                group = "verification"
                description = "Runs the JVM unit tests for this Kotlin Multiplatform module."
                dependsOn("jvmTest")
            }
        }

        try {
            tasks.named("preBuild") {
                dependsOn("spotlessApply")
            }
        } catch (ignored: UnknownTaskException) {
            tasks.matching {
                it.name.startsWith("pre") && it.name.endsWith("Build")
            }.configureEach {
                dependsOn("spotlessApply")
            }
        }
    }
}

// Second half of the opt-in local SonarQube support gated above in buildscript {}.
// All sonar.* entries in local.properties are forwarded as system properties, so
// `./gradlew sonar` behaves exactly like passing them via -Dsonar.xxx=... on the
// command line. sonar.projectKey/projectName default to the root project name
// ("Amethyst") and only need overriding in local.properties if desired.
val sonarEnabled = extra["sonarEnabled"] as Boolean
if (sonarEnabled) {
    val sonarProperties = extra["sonarProperties"] as Properties
    apply(plugin = "org.sonarqube")

    sonarProperties
        .stringPropertyNames()
        .filter { it.startsWith("sonar.") }
        .forEach { System.setProperty(it, sonarProperties.getProperty(it)) }

    // The scanner's sonarResolver task reads AGP's generated-res-values provider
    // but doesn't depend on the task that produces it — wire it up in every
    // module that has both (today only :amethyst enables resValues, but the
    // scanner defect is module-agnostic).
    subprojects {
        tasks.named { it == "sonarResolver" }.configureEach {
            dependsOn(tasks.withType<GenerateResValues>())
        }
    }
}

val installGitHook = tasks.register<Copy>("installGitHook") {
    val dotGit = File(rootProject.rootDir, ".git")
    val hooksDir: File = if (dotGit.isFile) {
        // Git worktree: .git is a file with "gitdir: <path>"
        val gitDir = File(dotGit.readText().trim().replace("gitdir: ", ""))
        File(gitDir, "hooks")
    } else {
        File(dotGit, "hooks")
    }
    from(File(rootProject.rootDir, ".git-hooks/pre-commit"))
    from(File(rootProject.rootDir, ".git-hooks/pre-push"))
    into(hooksDir)
    filePermissions { unix("0777") }
}
tasks.getByPath(":amethyst:preBuild").dependsOn(installGitHook)
