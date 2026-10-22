# ProGuard rules consumed by Compose Multiplatform's `proguardReleaseJars`
# task (see `compose.desktop.application.buildTypes.release.proguard` wiring
# in build.gradle.kts).
#
# Strategy mirrors the Android (mobile) module's `amethyst/proguard-rules.pro`:
#
#   - shrink:    ON   (size win, removes whole unused classes, no renames)
#   - optimize:  ON   (speed win; one bridge-generating sub-pass disabled below
#                       because ProGuard's specialized return-type bridges trip
#                       the JVM verifier on okio — R8 doesn't hit this)
#   - obfuscate: OFF  (via `-dontobfuscate`; every native library we ship
#                       resolves Java callbacks by *class name* through JNI
#                       `FindClass` / `GetMethodID`. Renaming breaks them.)
#
# The Android module solved the exact same JNI-rename problem with:
#
#     -dontobfuscate
#     -keepnames class ** { *; }
#     -keep enum ** { *; }
#
# Plus per-library `-keep` rules for JNA, libsodium, libscrypt, and the
# first-party (`com.vitorpamplona.**`) JSON-mapped types. We replicate that
# verbatim here, then add desktop-only rules for Jackson, VLCj, OkHttp,
# and SLF4J that the mobile build does not need (those code paths are
# Android-stripped on mobile but compiled on desktop).

# ============================================================================
# Mirror amethyst/proguard-rules.pro
# ============================================================================
# Preserve the line number information for debugging stack traces, and keep
# every class name + every enum intact so JNI / reflection / ServiceLoader
# lookups continue to resolve after the optimize pass.
-dontobfuscate
-keepattributes LocalVariableTable
-keepattributes LocalVariableTypeTable
-keepattributes *Annotation*
-keepattributes SourceFile
-keepattributes LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes MethodParameters
-keepparameternames

-keepdirectories libs

# Keep all names — belt-and-suspenders against any rename ProGuard might
# still attempt with obfuscate off (e.g. on synthetic inner classes).
-keepnames class ** { *; }

# Keep all enums whole — Jackson, Kotlin serialization, and many libraries
# enumerate features via `Class.getEnumConstants()` -> `values()`. Stripping
# `values()` / `valueOf(String)` returns null and crashes initialisation.
-keep enum ** { *; }

# preserve access to native classes (mirrors mobile)
-keep class fr.acinq.secp256k1.** { *; }

# JNA For Libsodium (mirrors mobile)
-keep class com.goterl.lazysodium.** { *; }

# libscrypt (mirrors mobile)
-keep class com.lambdaworks.codec.** { *; }
-keep class com.lambdaworks.crypto.** { *; }
-keep class com.lambdaworks.jni.** { *; }

-keep class info.guardianproject.** { *; }

# JSON-mapped first-party types (mirrors mobile)
-keep class com.vitorpamplona.quartz.** { *; }
-keep class com.vitorpamplona.amethyst.** { *; }

# ============================================================================
# Desktop-only JNI keep rules — libraries the mobile module doesn't ship
# ============================================================================
# `-keepnames class ** { *; }` above prevents renames, but does NOT prevent
# the shrink pass from removing members whose only callers are native (JNI
# `FindClass` + `GetStaticMethodID` from a bundled .dylib/.so/.dll). Each of
# these libraries was the source of a v1.09.1 release crash:
#
#   - androidx.sqlite (sqlite-bundled): nativeThreadSafeMode() stripped,
#     so `BundledSQLiteDriver.threadingMode` threw NoSuchMethodError on the
#     first relay-store query (LocalRelayStore.refreshStats).
#   - pt.davidafsilva.apple (jkeychain): macOS Keychain JNI for nsec storage.
#     Note: this lives in the dependency graph as a transitive runtime dep of
#     `com.github.javakeyring:java-keyring` — `ModernOsxKeychainBackend` holds
#     a `private pt.davidafsilva.apple.OSXKeychain` field. Keep both the
#     class and its private native methods (loadSharedObject / the
#     _addGenericPassword / _findGenericPassword bridge).
#
# secp256k1-kmp is already covered by the `-keep class fr.acinq.secp256k1.**`
# rule mirrored from mobile above.
-keep class androidx.sqlite.** { *; }
-keepclassmembers class androidx.sqlite.** {
    native <methods>;
    static <methods>;
}
-keep class pt.davidafsilva.apple.** { *; }
-keepclassmembers class pt.davidafsilva.apple.** {
    native <methods>;
}

# java-keyring's Linux backend: the Secret Service (GNOME Keyring, KWallet's
# Secret Service) over D-Bus, through de.swiesend:secret-service
# (org.freedesktop.secret) and dbus-java (org.freedesktop.dbus). Shrinking
# breaks it three ways (the signed hkdf jar it also needs is handled in
# build.gradle.kts, after the ProGuard task):
#   - dbus-java finds its socket transport through ServiceLoader
#     (META-INF/services/...ITransportProvider -> NativeTransportProvider), so
#     shrink saw no caller and emptied the transport jar. With no transport the
#     backend failed to open, java-keyring reported no backend at all, and every
#     Linux release build fell back to the console-password file store, which a
#     GUI app cannot answer: no keyring prompt, and no key kept.
#   - D-Bus calls go through reflective proxies of the remote interfaces, and
#     structs are (de)serialised from their fields by reflection.
#   - java-keyring builds each backend by reflection (getConstructor()).
-keep class com.github.javakeyring.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class org.freedesktop.secret.** { *; }
# SimpleCollection also talks to GNOME Keyring's own D-Bus interface (creating and
# unlocking a collection), a reflective proxy like the ones above.
-keep class org.gnome.keyring.** { *; }

# Rodio (nucleus.rodio, behind composemediaplayer-audio): the native player reports playback
# events and errors back through JNI callbacks that shrinking would see as unused.
-keep class dev.nucleusframework.rodio.** { *; }

# kmp-tor — loads native Tor daemon via JNI reflection
-keep class io.matthewnelson.** { *; }

# Coil image loader — uses ServiceLoader for decoder/fetcher registration
-keep class coil3.** { *; }

# OkHttp/Okio — platform detection and I/O via reflection
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================================
# Optimization — disabled entirely
# ============================================================================
# ProGuard's optimize pass causes multiple runtime crashes in the release
# build that are invisible at compile time:
#
# 1. `method/specialization/returntype` generates invalid okio bytecode:
#    VerifyError on Okio__OkioKt.buffer() (bridge return type mismatch).
#
# 2. kmp-tor's `AsyncFs.of()` gets transformed (instance → static or
#    inlined call-site mismatch), causing IncompatibleClassChangeError
#    at launch. Discovered by CI smoke test (release-deb-launch job).
#
# Disabling individual sub-passes (`!method/specialization/returntype`,
# `!method/marking/static`) did not fully resolve (2). The interaction
# between multiple optimization passes is the root cause. Shrink (dead
# code removal) remains ON and provides the bulk of the size win.
-dontoptimize

# ============================================================================
# Desktop-only: Jackson — mobile module does not ship Jackson on Android
# ============================================================================
# Jackson uses reflection extensively at construction time (ObjectMapper,
# JsonMapper, SerializationConfig, DeserializationConfig). The
# `-keep enum **` rule above already covers `values()`/`valueOf()`; this
# block also keeps the annotation/databind/core/kotlin-module classes
# intact and silences value-class MethodHandle warnings.
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.module.kotlin.** { *; }
-dontwarn com.fasterxml.jackson.module.kotlin.**

# ============================================================================
# Desktop-only: full JNA — mobile narrows JNA because Android has no AWT
# ============================================================================
# On desktop we run a real JVM with AWT, so VLCj / kmp-tor / etc. can use
# the full JNA surface. Keep it wholesale rather than the narrowed mobile
# allowlist.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
}
-dontwarn com.sun.jna.internal.Cleaner
-dontwarn com.sun.jna.internal.Cleaner$*

# ============================================================================
# Desktop-only: Lucene Hunspell — the built-in spell checker
# ============================================================================
# lucene-core picks implementations at runtime (MethodHandles lookups of the
# multi-release Panama classes, SPI for codecs), which shrinking cannot see.
# The Hunspell path is small, but kept whole so a release build cannot drop
# what only reflection reaches. The vector API is an incubator module the
# packaged runtime does not include; Lucene falls back without it.
-keep class org.apache.lucene.** { *; }
-dontwarn org.apache.lucene.**
-dontwarn jdk.incubator.vector.**
# lucene-analysis-common also bundles the Snowball stemmers, which spell
# checking never reaches; shrinking drops them, so their MethodHandle use
# needs no keep.
-dontwarn org.tartarus.snowball.**

# ============================================================================
# Desktop-only: VLCj — discovery providers loaded via ServiceLoader
# ============================================================================
# VLCj enumerates implementations of
# `uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider`
# via META-INF/services and resolves them with `Class.forName(...).newInstance()`.
-keep class uk.co.caprica.vlcj.** { *; }
-keepclassmembers class * implements uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider {
    public <init>(...);
}
-keepclassmembers class * implements uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryProvider {
    public <init>(...);
}

# ============================================================================
# Desktop-only: kdroidfilter ComposeMediaPlayer — JNI natives registered
# from the bundled Swift dylib's JNI_OnLoad via a fixed RegisterNatives
# table. Shrink sees no Java callsites for some natives (e.g. nGetVolume)
# and strips the declarations, so RegisterNatives fails with
# NoSuchMethodError during System.load() at first video init on macOS.
# ============================================================================
-keep class io.github.kdroidfilter.** { *; }
-keepclassmembers class io.github.kdroidfilter.** {
    native <methods>;
}

# Any library can bind its natives from JNI_OnLoad with RegisterNatives, which
# fails for the whole table when one declaration is missing, and shrink cannot
# see those callers. Keep every native of every class that is kept anyway; the
# declarations cost nothing. verifyJniNativesSurviveProguard checks it holds.
-keepclassmembers class * {
    native <methods>;
}

# ============================================================================
# Desktop-only: vico charts — Kotlin interface default-method super-calls.
# MutableCartesianMeasuringContext implements both MeasuringContext and
# CartesianMeasuringContext (which extends it), and its getPixels()/isLtr()
# bodies are `invokespecial MeasuringContext.<default>` calls. The JVM
# requires an invokespecial interface-method target to be a *direct*
# superinterface of the class — but shrink prunes the "redundant" direct
# MeasuringContext implements edge (reachable via CartesianMeasuringContext),
# producing bytecode the verifier rejects at class-load time:
#   java.lang.VerifyError: Bad invokespecial instruction: interface method
#   reference is in an indirect superinterface
# on the first notifications-chart composition. Confirmed by diffing the
# proguarded bundle against the pristine com.patrykandpatrick.vico:
# compose-desktop:3.3.1 artifact. Package-wide keep pins the hierarchy;
# which edge the shrinker deems redundant can shift with any vico bump.
# ============================================================================
-keep class com.patrykandpatrick.vico.** { *; }

# ============================================================================
# Desktop-only: OkHttp / Conscrypt / BouncyCastle / OpenJSSE / Graal
# ============================================================================
# okhttp ships TLS adapters for GraalVM native-image, Conscrypt, BouncyCastle,
# OpenJSSE, and Jetty boot. None are on the desktop classpath; okhttp falls
# back to the JDK default via `Class.forName(...)`-style probes that catch
# NoClassDefFoundError. Suppress the warnings without keeping the unused
# classes.
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
# Desktop-only: SLF4J — used reflectively by transitive deps
# ============================================================================
# jackson-databind and a few other deps `Class.forName("org.slf4j.LoggerFactory")`
# to detect logging. We ship slf4j-nop; keep it intact so detection succeeds.
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# ============================================================================
# Desktop-only: PDFBox (PDF rendering) and its commons-logging
# ============================================================================
# PDFBox logs through Apache commons-logging, which ships adapters for log4j 1/2, Avalon,
# LogKit and servlet containers. None of those are on our classpath; commons-logging only
# uses an adapter whose backend it finds at runtime, and otherwise falls back to
# java.util.logging. It discovers the implementation by class name, so keep it whole.
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.avalon.**
-dontwarn org.apache.log.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn javax.servlet.**
-dontwarn jakarta.servlet.**
# IOUtils unmaps buffers through MethodHandle.invokeExact, a signature-polymorphic call
# ProGuard cannot resolve against the library class.
-dontwarn org.apache.pdfbox.io.IOUtils

# ============================================================================
# Kotlin 2.3 stdlib stubs — compile-time classes with no JVM runtime class
# ============================================================================
-dontwarn kotlin.concurrent.atomics.**
-dontwarn kotlin.jvm.internal.EnhancedNullability

# apksig is left out of the desktop app (build.gradle.kts): only the CLI's release publishing
# (commons' ApkInspector, kept by the com.vitorpamplona.amethyst.** rule) refers to it.
-dontwarn com.android.apksig.**
