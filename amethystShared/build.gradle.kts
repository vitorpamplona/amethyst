import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.amethyst.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        // Compiled into BOTH the Android and the JVM target. Code here may
        // reference `android.*` types: they resolve from android.jar on Android
        // and from :androidStubs on the JVM.
        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    compileOnly(project(":androidStubs"))
                }
            }

        androidMain {
            dependsOn(jvmAndroid)
        }

        jvmMain {
            dependsOn(jvmAndroid)
            dependencies {
                implementation(project(":androidStubs"))
            }
        }
    }
}

