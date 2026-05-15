# ProGuard rules consumed by Compose Multiplatform's `proguardReleaseJars`
# task (see `compose.desktop.application.buildTypes.release.proguard` wiring
# in build.gradle.kts).
#
# Compose Multiplatform 1.11.0 ships a stricter default ruleset than 1.10.x
# and now treats unresolved references as fatal. The warnings below come from
# third-party libraries that have always carried optional code paths; they
# were silently tolerated on 1.10.3 and broke desktop packaging on 1.11.0.
#
# This file MUST keep rules for libraries that rely on reflection at runtime,
# in addition to the dontwarn rules for compile-time noise. Otherwise the
# release build will package classes whose reflectively-required methods
# (`values()`/`valueOf()` on enums, JNA `Structure` field accessors, VLCj
# discovery providers loaded via Class.forName) have been stripped or
# renamed, and the app crashes at startup. See the v1.09.1 desktop-DMG
# regression for an example: Jackson's `MapperConfig.collectFeatureDefaults`
# called `SerializationFeature.values()` reflectively, got
# `NoSuchMethodException` (because ProGuard had removed it as "unused"),
# and the JVM failed to launch.

# ============================================================================
# Jackson — required to keep app launchable
# ============================================================================
# Jackson uses reflection extensively at construction time (ObjectMapper,
# JsonMapper, SerializationConfig, DeserializationConfig). It enumerates
# feature enums via `Class.getEnumConstants()` -> `values()`, scans for
# `@JsonProperty`/`@JsonCreator`/etc. annotations on user types, and looks
# up sub-classes by name through `Class.forName`. None of that survives an
# optimize+shrink pass without explicit keep rules.
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.module.kotlin.** { *; }
-keepclassmembers enum com.fasterxml.jackson.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
# Jackson kotlin-module 2.21.x value-class converters call
# `MethodHandle.invokeExact(...)` with polymorphic signatures that ProGuard
# cannot resolve against the abstract MethodHandle declaration. Safe to
# suppress: only used reflectively when (de)serializing Kotlin `value class`
# types.
-dontwarn com.fasterxml.jackson.module.kotlin.**

# ============================================================================
# JNA + JNA platform — required by VLCj, kmp-tor, and any native binding
# ============================================================================
# JNA loads native libraries by reflecting on Structure subclasses (field
# order discovered via `getDeclaredFields`), reads JNA callback interfaces by
# name, and uses `Native.register` against class metadata. Without -keep,
# `Native.<clinit>` and `Structure.getFields` both fail at runtime.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
}
# Suppress the inner-class accessor warnings from the new strict ruleset.
# `com.sun.jna.internal.Cleaner$CleanerThread` references package-private
# synthetic accessors (`access$100` …) that ProGuard's class member analysis
# cannot follow through nested inner classes. The accessors exist at runtime.
-dontwarn com.sun.jna.internal.Cleaner
-dontwarn com.sun.jna.internal.Cleaner$*

# ============================================================================
# VLCj — discovery providers loaded reflectively via java.util.ServiceLoader
# ============================================================================
# VLCj enumerates implementations of
# `uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider`
# via the ServiceLoader mechanism, which reads class names from
# META-INF/services/* and resolves them with `Class.forName(...).newInstance()`.
# ProGuard's default settings strip both the service files and the impl
# classes (since nothing references them by name in user code). Keep
# everything VLCj ships; VLCj is reflection-heavy throughout.
-keep class uk.co.caprica.vlcj.** { *; }
-keepclassmembers class * implements uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider {
    public <init>(...);
}
-keepclassmembers class * implements uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryProvider {
    public <init>(...);
}

# ============================================================================
# OkHttp — optional TLS adapters discovered via Class.forName
# ============================================================================
# okhttp ships TLS adapters for GraalVM native-image, Conscrypt, BouncyCastle,
# OpenJSSE, and Jetty boot. None of these are on the desktop classpath; okhttp
# falls back to the JDK default via `Class.forName(...)`-style probes that
# catch NoClassDefFoundError. Suppress the warnings without keeping the
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

# ============================================================================
# SLF4J / logging — used reflectively by transitive deps
# ============================================================================
# jackson-databind and a few other deps `Class.forName("org.slf4j.LoggerFactory")`
# to detect logging. We ship slf4j-nop; keep it intact so detection succeeds.
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
