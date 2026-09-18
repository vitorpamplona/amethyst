# =============================================================================
# R8 configuration for the release build.
#
# Two things are balanced here.
#
#  1. Google Play measures how much of the shipped DEX R8 actually optimized
#     and renamed, and warns (then restricts visibility/publishing) below 25%
#     in either category. This file used to open with `-dontobfuscate` plus
#     `-keepnames class ** { *; }`, and then kept all of `com.vitorpamplona.**`
#     outright. That is the whole app and every library: `-dontobfuscate`
#     turns renaming off globally, and `-keepnames` is shorthand for
#     `-keep,allowshrinking`, which permits shrinking but neither renaming nor
#     optimization. Hence 0% obfuscation / 13% optimization.
#
#  2. Anything the runtime reaches by NAME rather than by reference has to keep
#     that name: JNI symbols, Jackson's reflective data binding, enum constants
#     persisted into DataStore, class names written into a manifest meta-data
#     value or into WorkManager's database.
#
# So every keep below is scoped to (2), and R8 gets everything else. mapping.txt
# is uploaded with each release, so stack traces stay retraceable.
#
# When adding a keep, say in a comment WHAT reads the name at runtime. A keep
# without that is usually a keep that is not needed.
# =============================================================================

# -----------------------------------------------------------------------------
# Attributes
# -----------------------------------------------------------------------------
# SourceFile + LineNumberTable are what let Play (and `retrace`) turn an
# obfuscated stack trace back into real line numbers via mapping.txt.
# -renamesourcefileattribute replaces the real file name with a constant so the
# class name cannot simply be read back off it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations (jackson-module-kotlin reads @kotlin.Metadata; Jackson mixins and
# kotlinx.serialization read their own), generic signatures, and the
# inner/enclosing-class links that R8 requires alongside Signature.
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# LocalVariableTable, LocalVariableTypeTable, MethodParameters and
# -keepparameternames used to be kept here as well. They are debug metadata:
# nothing in the app reads them (jackson-module-kotlin takes parameter names
# from @kotlin.Metadata, not from MethodParameters), and they are pure DEX
# weight in a release build.

-keepdirectories libs

# -----------------------------------------------------------------------------
# JNI — names that live in a .so, not in the DEX
# -----------------------------------------------------------------------------
# proguard-android-optimize.txt already contributes
#   -keepclasseswithmembernames class * { native <methods>; }
# which pins every class that DECLARES a native method (ArtiNative,
# secp256k1's loader, …) together with those methods' names, because the
# exported symbol is Java_<class>_<method>. What that does NOT cover is the
# traffic in the other direction: Java/Kotlin the native side looks up itself.

# libarti_android.so calls back into this interface by name —
# tools/arti-build/src/lib.rs does GetMethodID("onLogLine") on it. Renaming the
# method silently kills all Tor log output.
-keep class com.vitorpamplona.amethyst.ui.tor.ArtiLogCallback { *; }

# secp256k1's JNI layer resolves these from native code.
-keep class fr.acinq.secp256k1.** { *; }

# libscrypt
-keep class com.lambdaworks.codec.** { *; }
-keep class com.lambdaworks.crypto.** { *; }
-keep class com.lambdaworks.jni.** { *; }

-keep class info.guardianproject.** { *; }

# JNA for Libsodium: JNA maps these types onto the C ABI by reflecting over
# their fields and method signatures at runtime.
-keep class com.goterl.lazysodium.** { *; }

# JNA also requires AWT, which Android does not have. So the classes are broken down to filter AWT out
-keep class com.sun.jna.ToNativeConverter { *; }
-keep class com.sun.jna.NativeMapped { *; }
-keep class com.sun.jna.CallbackReference { *; }
-keep class com.sun.jna.ptr.IntByReference { *; }
-keep class com.sun.jna.NativeLong { *; }
-keep class com.sun.jna.Structure { *; }
-keep class com.sun.jna.Structure$* { *; }
-keep class com.sun.jna.Native$ffi_callback { *; }
-keep class * implements com.sun.jna.Structure$* { *; }
-keep class * implements com.sun.jna.Native$* { *; }
-keep class com.sun.jna.Native {
    private static com.sun.jna.NativeMapped fromNative(java.lang.Class, java.lang.Object);
    private static com.sun.jna.NativeMapped fromNative(java.lang.reflect.Method, java.lang.Object);
    private static java.lang.Class nativeType(java.lang.Class);
    private static java.lang.Object toNative(com.sun.jna.ToNativeConverter, java.lang.Object);
    private static java.lang.Object fromNative(com.sun.jna.FromNativeConverter, java.lang.Object, java.lang.reflect.Method);
}

# -----------------------------------------------------------------------------
# Enum constant names
# -----------------------------------------------------------------------------
# An enum constant's NAME is persisted data in this app. The preference stores
# write `enum.name` into DataStore and read it back with `Type.valueOf(string)`
# (see model/preferences/UISharedPreferences.kt, TorSharedPreferences.kt,
# NamecoinSharedPreferences.kt), and Jackson serialises enums by name too.
# Enum.valueOf resolves that string against the static FIELD name, so renaming
# the constants would reset every user's theme/font/Tor/connectivity setting on
# the first launch after an update.
#
# Deliberately blanket rather than a list of the enums that happen to be
# persisted today: the failure mode is silent, release-only, and one new
# `preferences[KEY] = value.name` line away. Only the field names are pinned —
# the enum classes themselves are still renamed and their methods still
# optimized.
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# Jackson — reflective data binding only
# -----------------------------------------------------------------------------
# Most of Quartz's wire format does NOT need a keep. Event, Filter, Message,
# Command, Rumor, EventTemplate, TagArray, the NIP-46 Bunker messages and the
# NIP-55 intent results all go through hand-written StdSerializer/StdDeserializer
# pairs registered on JacksonMapper / JsonMapperNip55, which read and write
# property names as string literals. Renaming their fields changes nothing on
# the wire.
#
# What remains is the code Jackson data-binds REFLECTIVELY — `treeToValue(...)`
# and `readValue<T>()` with no custom deserializer. There the JSON property
# names come from the Kotlin constructor parameter names, so a renamed field is
# a changed wire format.

# NIP-47 Wallet Connect RPC: every *Method / *Params / *SuccessResponse plus
# NwcError, NwcTransaction and the NwcMethod/NwcErrorCode enums are reached via
# treeToValue() from RequestDeserializer / ResponseDeserializer /
# NotificationDeserializer.
-keep class com.vitorpamplona.quartz.nip47WalletConnect.rpc.** { *; }

# CLINK (experimental NIP-XX offers/debits/manage): parsed with
# OptimizedJsonMapper.fromJsonTo<OfferRequest>() and friends — reflective.
-keep class com.vitorpamplona.quartz.experimental.clink.** { *; }

# On-disk JSON written and re-read by the app itself. The field names are the
# file format, so renaming them makes every existing file unreadable.
-keep class com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost { *; }
-keep class com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostFile { *; }
-keep class com.vitorpamplona.amethyst.service.pow.PowJobsFile { *; }
-keep class com.vitorpamplona.amethyst.commons.service.pow.PersistedPoWJob { *; }
-keep class com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageStore$UsageFile { *; }

# -----------------------------------------------------------------------------
# Names referenced from outside the DEX
# -----------------------------------------------------------------------------
# AGP generates keeps from the merged manifest's component `android:name`
# attributes, but NOT from <meta-data android:value>. The Cast framework reads
# this one out of the manifest and Class.forName()s it.
-keep class com.vitorpamplona.amethyst.service.cast.chromecast.AmethystCastOptionsProvider { *; }

# WorkManager stores the worker's class name in its own database at enqueue
# time and instantiates it by name on a later process start — including after
# an app update, when R8 has produced a different mapping.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# androidx.appfunctions: the KSP-generated invokers and the app_functions.xml
# the system reads are keyed off these declarations. One class plus its
# generated neighbours — cheap enough not to be worth proving unnecessary
# against a pre-stable (alpha) library.
-keep class com.vitorpamplona.amethyst.appfunctions.** { *; }
