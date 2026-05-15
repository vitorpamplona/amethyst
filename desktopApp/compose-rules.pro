# ProGuard rules consumed by Compose Multiplatform's `proguardReleaseJars`
# task (see `compose.desktop.application.buildTypes.release.proguard` wiring
# in build.gradle.kts).
#
# Compose Multiplatform 1.11.0 ships a stricter default ruleset than 1.10.x
# and now treats unresolved references as fatal. The warnings below come from
# third-party libraries that have always carried optional code paths; they
# were silently tolerated on 1.10.3 and broke desktop packaging on 1.11.0.

# --- Jackson kotlin-module 2.21.x value-class converters ---------------------
# Generated converters call `MethodHandle.invokeExact(...)` with polymorphic
# signatures that ProGuard cannot resolve against the abstract MethodHandle
# declaration. They are only used reflectively by Jackson when (de)serializing
# Kotlin `value class` types — safe to suppress.
-dontwarn com.fasterxml.jackson.module.kotlin.**

# --- okhttp optional runtime platforms --------------------------------------
# okhttp ships TLS adapters for GraalVM native-image, Conscrypt, BouncyCastle,
# OpenJSSE, and Jetty boot. None of these are on the desktop classpath; okhttp
# falls back to the JDK default. Suppress the warnings without keeping the
# unused classes.
-dontwarn okhttp3.internal.graal.**
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform$*
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn okhttp3.internal.platform.ConscryptPlatform$*
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform$*
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**

# --- JNA internal cleaner ---------------------------------------------------
# `com.sun.jna.internal.Cleaner$CleanerThread` references package-private
# synthetic accessors (`access$100` …) that ProGuard's class member analysis
# cannot follow through nested inner classes. The accessors exist at runtime.
-dontwarn com.sun.jna.internal.Cleaner
-dontwarn com.sun.jna.internal.Cleaner$*
