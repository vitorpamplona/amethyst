# Quartz KMP (Legacy Skill — Migration Complete)

> The KMP migration of Quartz is **complete**. This file is kept for historical reference.
>
> For integrating Quartz into external projects, use the **`quartz-integration`** skill instead.
> For working with Quartz internals within Amethyst, use the **`nostr-expert`** skill.

## What was migrated

The Quartz library was successfully converted from Android-only to full KMP supporting:
- **commonMain** — All Nostr protocol logic, events, filters, tags
- **jvmAndroid** — OkHttp WebSocket, Jackson JSON, relay serializers
- **androidMain** — SQLite event store, NIP-55 Android signer
- **jvmMain** — Desktop JVM crypto (lazysodium-java, secp256k1-jni-jvm)
- **iosMain** — iOS targets (XCFramework `quartz-kmpKit`)

## Current artifact

```
com.vitorpamplona.quartz:quartz:1.05.1
```

See `.claude/skills/quartz-integration/SKILL.md` for full integration guide.