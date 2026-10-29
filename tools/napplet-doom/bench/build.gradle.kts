import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
    kotlin("multiplatform") version "2.4.21"
}

val chasmVersion = "2.3.0"
val chicoryVersion = "1.7.5"

kotlin {
    jvm()
    linuxX64 {
        binaries {
            executable {
                entryPoint = "bench.main"
            }
        }
    }

    compilerOptions {
        // Chasm's host-function API is written with context parameters.
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.charlietap.chasm:chasm:$chasmVersion")
        }
        jvmMain.dependencies {
            implementation("com.dylibso.chicory:runtime:$chicoryVersion")
            implementation("com.dylibso.chicory:compiler:$chicoryVersion")
        }
    }
}

val built = layout.projectDirectory.dir("../build")

// ./gradlew -p tools/napplet-doom/bench benchJvm -Pruntimes=chicory-aot,chasm -Ptics=2100 -Pwad=/path/doom1.wad
tasks.register<JavaExec>("benchJvm") {
    val main = kotlin.jvm().compilations.getByName("main") as KotlinJvmCompilation
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("bench.MainKt")
    maxHeapSize = "2g"
    args(
        built.file("doom.wasm").asFile.path,
        providers.gradleProperty("wad").getOrElse(built.file("freedoom-0.13.0/freedoom1.wad").asFile.path),
        providers.gradleProperty("tics").getOrElse("2100"),
        providers.gradleProperty("runtimes").getOrElse("chicory-aot,chicory,chasm"),
    )
}
