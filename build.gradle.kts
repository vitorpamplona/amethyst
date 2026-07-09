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
// via `by extra`.
buildscript {
    val localProperties = File(rootDir, "local.properties")
    val sonarProperties by extra(
        java.util.Properties().apply {
            if (localProperties.exists()) localProperties.inputStream().use { load(it) }
        },
    )
    val sonarEnabled by extra(
        sonarProperties.getProperty("sonar.host.url") != null &&
            gradle.startParameter.taskNames.any { it.substringAfterLast(":") in setOf("sonar", "sonarqube") },
    )
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
val sonarEnabled: Boolean by extra
if (sonarEnabled) {
    val sonarProperties: Properties by extra
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
