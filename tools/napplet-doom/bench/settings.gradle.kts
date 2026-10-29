// A standalone build, deliberately not part of Amethyst's: run it with
//   ./gradlew -p tools/napplet-doom/bench benchJvm
rootProject.name = "doom-wasm-bench"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
