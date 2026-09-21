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

# Nothing keeps libscrypt any more: quartz replaced it with a pure-Kotlin
# implementation and `:quartz:dependencies` shows no com.lambdaworks on any
# configuration. These rules are merged into the R8 config of EVERY app that
# depends on quartz, so a rule we do not need is noise in somebody else's build.

# No Jackson keeps. Every wire format Quartz speaks is now handled by a
# hand-written serializer that names its fields as string literals: the
# StdSerializer/StdDeserializer pairs registered on JacksonMapper (Event, Filter,
# Message, Command, Rumor, EventTemplate, TagArray, the NIP-46 Bunker messages),
# JsonMapperNip55 (IntentResult, Permission), and the kotlinx serializers that
# NIP-47 and CLINK route through on every target. None of it reads a Kotlin
# constructor parameter name at runtime, so none of it has to survive R8.

# Quartz serialises enums by name (Jackson writes/reads Enum.name, and
# Enum.valueOf resolves that string against the static field name), so the
# constants of its own enums have to keep their names. Consumers that persist
# their OWN enums by name need the equivalent rule in their own configuration.
-keepclassmembers enum com.vitorpamplona.quartz.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
