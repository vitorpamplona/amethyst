# ProGuard rules for :desktopApp release builds.
#
# Compose Multiplatform's `package*Release` tasks chain `proguardReleaseJars`
# before packaging. The plugin ships a sensible set of defaults
# (`default-compose-desktop-rules.pro`) but doesn't know about the
# desktop-specific transitive dependency graph used here.
#
# Without these rules, `proguardReleaseJars` fails with ~50 unresolved class
# references — all of them harmless probes for platform-specific APIs that
# don't exist on the desktop classpath. The libraries themselves handle the
# absence at runtime via reflection-and-catch; ProGuard sees the dynamic
# reference and bails before getting that far.

# ---------------------------------------------------------------------------
# Android-targeting probes from Kotlin Multiplatform deps.
#
# kmp-file / kmp-process / kmp-tor / kotlincrypto all use `Class.forName(...)`
# to detect Android at runtime and select an Android-specific backend. On JVM
# desktop the probe fails, they fall through to the JVM/POSIX path, and
# everything works. ProGuard only sees the dynamic-class string and errors
# out unless we tell it the absence is expected.
# ---------------------------------------------------------------------------
-dontwarn android.**
-dontwarn io.matthewnelson.kmp.file.**
-dontwarn io.matthewnelson.kmp.process.**
-dontwarn io.matthewnelson.kmp.tor.runtime.**
-dontwarn org.kotlincrypto.core.**

# ---------------------------------------------------------------------------
# VLCJ optional Swing overlay probes for `com.sun.awt.AWTUtilities` — a
# Sun-internal class removed in JDK 11. The overlay component is optional and
# the runtime fall-back is a no-op when the class isn't present.
# ---------------------------------------------------------------------------
-dontwarn com.sun.awt.AWTUtilities
-dontwarn uk.co.caprica.vlcj.player.component.overlay.**

# ---------------------------------------------------------------------------
# Apache commons-io probes for the JDK 8 `sun.misc.Cleaner` path before
# falling back to `MethodHandles`-based cleaning on JDK 9+.
# ---------------------------------------------------------------------------
-dontwarn sun.misc.Cleaner
-dontwarn org.apache.commons.io.input.ByteBufferCleaner**

# ---------------------------------------------------------------------------
# Skiko's optional JetBrains-Runtime shared-textures fast path (used for GPU
# texture sharing between Skia and Swing on JBR). Not present on Zulu /
# Temurin / Corretto — Skiko silently falls back to the standard path.
# ---------------------------------------------------------------------------
-dontwarn com.jetbrains.SharedTextures
-dontwarn org.jetbrains.skiko.swing.JbrSharedTexturesAdapter

# ---------------------------------------------------------------------------
# OkHttp ships optional adapters for Bouncy Castle / Conscrypt / OpenJSSE
# security providers and a GraalVM Native Image feature. None of these
# providers are on this app's classpath; OkHttp's platform detector picks
# `Jdk9Platform` at runtime and ignores the missing alternatives.
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn okhttp3.internal.platform.ConscryptPlatform$*
-dontwarn okhttp3.internal.platform.OpenJSSEPlatform
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.graal.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**

# ---------------------------------------------------------------------------
# Jackson Kotlin module's value-class (`inline class`) converters call
# `MethodHandle.invokeExact(...)` with polymorphic signatures. ProGuard 7.7.0
# doesn't resolve `@PolymorphicSignature` method references against
# `java.lang.invoke.MethodHandle` correctly — the calls are valid JVM
# bytecode that the runtime dispatches via the signature on the call site.
# ---------------------------------------------------------------------------
-dontwarn com.fasterxml.jackson.module.kotlin.**ValueClass**
-dontwarn com.fasterxml.jackson.module.kotlin.NoConversionCreatorBoxDeserializer**
-dontwarn com.fasterxml.jackson.module.kotlin.HasConversionCreatorWrapsSpecifiedBoxDeserializer
-dontwarn com.fasterxml.jackson.module.kotlin.WrapsAnyValueClassBoxDeserializer

# ---------------------------------------------------------------------------
# JNA's internal Cleaner uses synthetic package-private accessor methods
# (`access$100`, `access$200`, etc.) that ProGuard's library-class resolver
# doesn't see because JNA ships the synthetic accessors only in its own
# classes file. Harmless — they exist at runtime in the same jar.
# ---------------------------------------------------------------------------
-dontwarn com.sun.jna.internal.Cleaner**
