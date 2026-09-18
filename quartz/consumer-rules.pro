# =============================================================================
# Keep rules Quartz contributes to every app that consumes it (wired through
# `optimization.consumerKeepRules` in build.gradle.kts, so these end up merged
# into the consumer's own R8 configuration).
#
# Scope them tightly. A rule here applies to the CONSUMER's whole program, so
# the `-keepnames class ** { *; }` that used to sit in this file pinned every
# name in every app that depends on Quartz — ours included — and blocked R8
# from optimizing any member anywhere (`-keepnames` is `-keep,allowshrinking`).
# Only list what breaks at runtime if the name changes.
# =============================================================================

-keepdirectories libs

# secp256k1's JNI layer resolves these from native code.
-keep class fr.acinq.secp256k1.** { *; }

# libscrypt
-keep class com.lambdaworks.codec.** { *; }
-keep class com.lambdaworks.crypto.** { *; }
-keep class com.lambdaworks.jni.** { *; }

# Jackson data binding, reflective paths only.
#
# Nearly all of Quartz's wire format is handled by the hand-written
# StdSerializer/StdDeserializer pairs registered on JacksonMapper (Event,
# Filter, Message, Command, Rumor, EventTemplate, TagArray, the NIP-46 Bunker
# messages) and JsonMapperNip55 (IntentResult, Permission). Those read and
# write property names as string literals, so their fields are free to be
# renamed.
#
# These two trees are not: they are data-bound reflectively, via
# `treeToValue(...)` and `OptimizedJsonMapper.fromJsonTo<T>()`, which derive the
# JSON property names from the Kotlin constructor parameter names.
-keep class com.vitorpamplona.quartz.nip47WalletConnect.rpc.** { *; }
-keep class com.vitorpamplona.quartz.experimental.clink.** { *; }

# Quartz serialises enums by name (Jackson writes/reads Enum.name, and
# Enum.valueOf resolves that string against the static field name), so the
# constants of its own enums have to keep their names. Consumers that persist
# their OWN enums by name need the equivalent rule in their own configuration.
-keepclassmembers enum com.vitorpamplona.quartz.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
