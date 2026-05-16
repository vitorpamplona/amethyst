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
-keep class com.vitorpamplona.ammolite.** { *; }

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

# ============================================================================
# Optimize sub-pass — disable the one that produces invalid okio bytecode
# ============================================================================
# ProGuard's `method/specialization/returntype` pass specialised
# `Okio__OkioKt.buffer(Source): BufferedSource` to a synthetic bridge
# `buffer$<hash>` whose declared return type was the more concrete
# `RealBufferedSource` while the bytecode body returned the
# `BufferedSource` super-interface. The JVM verifier rejected the bridge:
#
#     VerifyError: Bad return type
#       Exception Details:
#         Location: okio/Okio__OkioKt.buffer$5ae116e(...)
#         Reason:   Type 'okio/BufferedSource' is not assignable to
#                   'okio/RealBufferedSource'.
#
# Disabling just this sub-pass keeps merging, inlining, peephole and
# dead-code optimizations on. Everything else in `optimize` stays.
-optimizations !method/specialization/returntype

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
