pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeModule("com.github.UnifiedPush", "android-connector")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
        maven { url = uri("https://mvn.0110.be/releases") }
    }
}

rootProject.name = "Amethyst"
include(":amethyst")
include(":benchmark")
include(":quartz")
include(":geode")
include(":commons")
include(":quic")
include(":nestsClient")
include(":desktopApp")
include(":cli")
include(":quic-interop")
project(":quic-interop").projectDir = file("quic/interop")
