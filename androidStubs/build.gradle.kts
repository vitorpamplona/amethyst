/*
 * JVM-side stand-ins for the handful of `android.*` framework classes that
 * shared Amethyst code references.
 *
 * The point of this module is that a single source file in a `jvmAndroid`
 * source set can write `import android.content.Context` and have it resolve
 * from `android.jar` on the Android target and from this module on the JVM
 * target — no expect/actual, no import rewrite, no per-file edit.
 *
 * Written in Java, not Kotlin, on purpose: Kotlin sees Java members as
 * platform types (`String!`), exactly as it sees the real `android.jar`.
 * Kotlin stubs would instead declare hard non-null/nullable types and would
 * silently change what compiles in the shared source set.
 *
 * This module is NEVER on the Android runtime or dex classpath — it is a
 * compileOnly dependency of the shared source set and an implementation
 * dependency of the JVM target only.
 */
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
