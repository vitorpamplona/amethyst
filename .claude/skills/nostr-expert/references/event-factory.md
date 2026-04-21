# EventFactory: Parsing JSON into Typed Events

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt` is the single dispatch point that turns a parsed `(id, pubKey, createdAt, kind, tags, content, sig)` tuple into the correct `Event` subclass.

## What It Does

EventFactory is a giant `when` over `kind` that maps integer kind values to concrete event classes. If a kind isn't recognized, it falls back to the generic base `Event` (so unknown kinds still round-trip). Every NIP that defines a new kind registers its class here.

Typical shape:

```kotlin
object EventFactory {
    fun create(
        id: HexKey,
        pubKey: HexKey,
        createdAt: Long,
        kind: Int,
        tags: TagArray,
        content: String,
        sig: HexKey,
    ): Event = when (kind) {
        MetadataEvent.KIND        -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
        TextNoteEvent.KIND        -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
        ContactListEvent.KIND     -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
        ReactionEvent.KIND        -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
        // …hundreds more…
        else                      -> Event(id, pubKey, createdAt, kind, tags, content, sig)
    }
}
```

Callers are normally upstream of this: `Event.fromJson(...)` / `EventMapper.fromJson(...)` / the relay client's message parser. You rarely call EventFactory directly — you consume typed events it produces.

## Registering a New Event Kind

Adding a NIP is roughly:

1. Create the event class under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nipXX…/` alongside the NIP package.
2. Subclass the right base:
   - `Event` — regular events (stored forever).
   - `BaseReplaceableEvent` — kinds `10000-19999`, `0`, `3`.
   - `BaseAddressableEvent` — kinds `30000-39999` (identified by `kind:pubkey:d-tag`).
   - Ephemeral events extend `Event` but have kind `20000-29999`.
3. Define `companion object { const val KIND = <n> }`.
4. If the event has tag builders, define a `TagArrayBuilder<YourEvent>` DSL in a `TagArrayBuilder` extension — see `nostr-expert/references/tag-patterns.md`.
5. Add a branch to `EventFactory.create(...)` so JSON parsing produces your typed class.
6. If the event is addressable, ensure it exposes a stable `dTag()` and `address()`.
7. Add tests under `quartz/src/commonTest/...`.

## Why a Monolithic when?

- **Zero overhead**: compiled to a dense lookup. No reflection, no registry map.
- **Exhaustive browsing**: every known kind lives at one search location. `grep KIND = 1234 quartz/...` finds everything.
- **Obvious migration path**: adding a kind means adding a case; removing a kind is a grep-and-delete.

The tradeoff is the file is large and every new kind edits the same file — expect merge conflicts in PRs that touch it, and resolve by keeping both branches.

## Supporting Utilities

- `EventAssembler.kt` (crypto/) — higher-level helper that takes a signer and a `kind + tags + content` and produces a fully signed event (id + sig populated).
- `EventTemplate.kt` (signers/) — unsigned-event holder, useful in signer flows.
- `Event.fromJson(...)` / `Event.toJson()` — JSON round-trip using `OptimizedJsonMapper` (Jackson on jvmAndroid).

## Related References

- `event-hierarchy.md` — class hierarchy, Kind ranges
- `nip-catalog.md` — which kind maps to which NIP
- `tag-patterns.md` — `TagArrayBuilder` DSL for writing tags cleanly
