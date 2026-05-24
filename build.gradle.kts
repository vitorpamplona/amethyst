import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare

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
// Android versionCode stays local in amethyst/build.gradle.kts (must be monotonic int).
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
