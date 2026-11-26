pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Magpie is published only to the Plugin Portal. Asking Central first costs a 404, and when a
        // rate-limited Central answers with a 429 Gradle fails the build instead of trying the Portal.
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeGroup("com.vitorpamplona.magpie") }
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

// Quartz, its bindings (marmot-kmp, cordn-kmp, mls-kmp), quic-kmp, kiko-kmp, djot-kmp, crypto-kmp,
// negentropy-kmp and libschnorr256k1 are released on Nostr + Blossom: the Magpie settings plugin
// serves their groups, trusting only the release key vitorpamplona.com names as `releases`, and
// gradle/magpie.lock pins that key and every resolved version's files: commit its changes with the
// version bump, and ask before editing it by hand, since a lock failure means a key or a release
// changed. It needs Gradle on Java 21 or newer; the build runs on JDK 25 (BUILDING.md).
plugins {
    id("com.vitorpamplona.magpie") version "0.2.3"
}

magpie {
    group("com.vitorpamplona.quartz")
    group("com.vitorpamplona.marmot")
    group("com.vitorpamplona.cordn")
    group("com.vitorpamplona.mls")
    group("com.vitorpamplona.quic")
    group("com.vitorpamplona.kiko")
    group("com.vitorpamplona.crypto")
    group("com.vitorpamplona.negentropy")
    group("com.vitorpamplona.schnorr256k1")
    group("com.vitorpamplona.djot")
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
    // JitPack rewrites the file names in the Gradle module metadata it serves and drops their
    // classifiers, so the iOS targets' metadata variant names `<module>-<version>.jar`, which does
    // not exist, instead of the `<module>-<version>-metadata.jar` it stored. The iOS metadata compiles
    // need that variant: point it at the real file. Only for the compose-richtext fork's iOS modules.
    components {
        listOf("richtext-ui", "richtext-ui-material3").forEach { library ->
            listOf("iosarm64" to "iosArm64", "iossimulatorarm64" to "iosSimulatorArm64").forEach { (suffix, target) ->
                withModule("com.github.vitorpamplona.compose-richtext:$library-$suffix") {
                    val version = id.version
                    withVariant("${target}MetadataElements-published") {
                        withFiles {
                            removeAllFiles()
                            addFile("$library-$suffix-$version-metadata.jar")
                        }
                    }
                }
            }
        }
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
include(":iosApp")
include(":nestsClient")
include(":desktopApp")
include(":cli")
include(":relayBench")
include(":agentCore")
include(":claudeCodeClient")
include(":codexClient")
include(":deviceClientCore")
include(":androidDeviceClient")
include(":iosSimulatorClient")

// quartz lives in its own repo (vitorpamplona/quartz) and comes from Nostr + Blossom through Magpie
// (above), like every library below. To change it alongside the app, point Gradle at a local
// checkout and it builds
// that checkout instead of the published artifact, for every module and for the libraries' own
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

// quic lives in its own repo (vitorpamplona/quic-kmp) and comes through Magpie. To change
// it alongside nestsClient, point Gradle at a local checkout and it builds that
// checkout instead of the published artifact:
//   ./gradlew -PquicKmpDir=../quic-kmp :nestsClient:jvmTest
providers.gradleProperty("quicKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.quic:quic")).using(project(":quic"))
        }
    }
}

// The fantasy-console cart runtime lives in its own repo (vitorpamplona/kiko-kmp) and comes through
// Magpie. To change it alongside the cart player in commons and commonsUI, build a local checkout:
//   ./gradlew -PkikoKmpDir=../kiko-kmp :commons:jvmTest
providers.gradleProperty("kikoKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.kiko:kiko")).using(project(":kiko"))
            substitute(module("com.vitorpamplona.kiko:kiko-player")).using(project(":kiko-player"))
            substitute(module("com.vitorpamplona.kiko:kiko-compose")).using(project(":kiko-compose"))
        }
    }
}

// The Djot parser lives in its own repo (vitorpamplona/djot-kmp) and comes through Magpie. To change
// it alongside the wiki rendering in commons, build a local checkout:
//   ./gradlew -PdjotKmpDir=../djot-kmp :commons:jvmTest
providers.gradleProperty("djotKmpDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.djot:djot")).using(project(":djot"))
        }
    }
}

// The MLS engine lives in its own repo (vitorpamplona/mls-kmp) and comes through Magpie, like
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
