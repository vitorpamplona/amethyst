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
    }
}

rootProject.name = "Amethyst"
include(":amethyst")
include(":nappletHost")
include(":benchmark")
include(":baselineprofile")
include(":quartz")
include(":geode")
include(":commons")
include(":commonsUI")
include(":nestsClient")
include(":marmotQuic")
include(":desktopApp")
include(":cli")
include(":relayBench")
include(":marmotBench")

// quic lives in its own repo (vitorpamplona/quic-kmp) and comes from Maven Central. To change
// it alongside nestsClient or marmotQuic, point Gradle at a local checkout and it builds that
// checkout instead of the published artifact:
//   ./gradlew -PquicKmpDir=../quic-kmp :nestsClient:jvmTest
providers.gradleProperty("quicKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.quic:quic")).using(project(":quic"))
        }
    }
}

// The MLS engine lives in its own repo (vitorpamplona/mls-kmp) and comes from Maven Central, like
// quic. To change it alongside Marmot or cordn, build a local checkout instead of the artifact:
//   ./gradlew -PmlsKmpDir=../mls-kmp :quartz:jvmTest
providers.gradleProperty("mlsKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.mls:mls")).using(project(":mls"))
        }
    }
}
