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
    // The groups only JitPack builds (straight from GitHub). Google and Maven Central never have
    // them, so they are told not to be asked: Gradle tried both first, for 404s, and when a
    // rate-limited Central answered one with a 429 it disabled Central for the rest of the build
    // and every other dependency failed to resolve. A JitPack group missing here still resolves,
    // only through those wasted lookups first; mavenLocal() keeps serving a locally published fork.
    val jitpackOnly =
        listOf(
            "cc.ekblad", // cli's TOML parser, 4koma
            "cc.ekblad.konbini", // 4koma's parser combinators
            "com.github.UnifiedPush",
            "com.github.davotoula",
            "com.github.lincollincol",
            "com.github.rikkahub.jlatexmath-android",
            "com.github.vitorpamplona.compose-richtext",
        )
    repositories {
        mavenLocal()
        google { content { jitpackOnly.forEach { excludeGroup(it) } } }
        mavenCentral { content { jitpackOnly.forEach { excludeGroup(it) } } }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Amethyst"
include(":amethyst")
include(":nappletHost")
include(":benchmark")
include(":baselineprofile")
include(":geode")
include(":commons")
include(":commonsUI")
include(":nestsClient")
include(":marmotQuic")
include(":desktopApp")
include(":cli")
include(":relayBench")
include(":marmotBench")

// quartz lives in its own repo (vitorpamplona/quartz) and comes from Maven Central, like crypto
// and quic below. To change it alongside the app, point Gradle at a local checkout and it builds
// that checkout instead of the published artifact, for every module and for quic's own
// dependency on it:
//   ./gradlew -PquartzDir=../quartz :commons:jvmTest
providers.gradleProperty("quartzDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.quartz:quartz")).using(project(":quartz"))
        }
    }
}

// The MLS bindings live in their own repos, built on quartz: Marmot (vitorpamplona/marmot-kmp) and
// cordn (vitorpamplona/cordn-kmp). To change one alongside the app, build a local checkout:
//   ./gradlew -PquartzDir=../quartz -PmarmotKmpDir=../marmot-kmp :commons:jvmTest
providers.gradleProperty("marmotKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.marmot:marmot")).using(project(":marmot"))
        }
    }
}

//   ./gradlew -PquartzDir=../quartz -PcordnKmpDir=../cordn-kmp :commons:jvmTest
providers.gradleProperty("cordnKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.cordn:cordn")).using(project(":cordn"))
        }
    }
}

// crypto-kmp lives in its own repo (vitorpamplona/crypto-kmp) and reaches this build through
// quartz. To change it alongside quartz and the app, point Gradle at a local checkout:
//   ./gradlew -PquartzDir=../quartz -PcryptoKmpDir=../crypto-kmp :commons:jvmTest
providers.gradleProperty("cryptoKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.crypto:crypto")).using(project(":crypto"))
        }
    }
}

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
// quic, and reaches this build through marmot and cordn. To change it alongside Marmot or cordn, build a
// local checkout instead of the artifact:
//   ./gradlew -PquartzDir=../quartz -PmlsKmpDir=../mls-kmp :commons:jvmTest
providers.gradleProperty("mlsKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.mls:mls")).using(project(":mls"))
        }
    }
}
