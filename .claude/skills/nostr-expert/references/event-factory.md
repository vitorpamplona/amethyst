# EventFactory and registering a new kind

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt` turns a
parsed `(id, pubKey, createdAt, kind, tags, content, sig)` into the typed `Event`
subclass. Callers normally reach it through `Event.fromJson(...)` or the relay message
parser.

Shape (verbatim structure):

```kotlin
class EventFactory {
    companion object {
        val factories: MutableMap<Int, EventBuilder> = mutableMapOf()   // runtime registrations

        fun <T : Event> create(id, pubKey, createdAt, kind, tags, content, sig): T =
            when (kind) {
                AcceptedBadgeSetEvent.KIND -> AcceptedBadgeSetEvent(id, pubKey, createdAt, tags, content, sig)
                …
                else -> factories[kind]?.build(id, pubKey, createdAt, tags, content, sig)
                    ?: Event(id, pubKey, createdAt, kind, tags, content, sig)
            }

        fun isKnownKind(kind: Int): Boolean   // typed class exists?
        fun probe(kind: Int): Event           // a tagless instance, used by the stores' searchable checks
    }
}
```

Some kinds pick their class from tags (kinds 38000 and 30301 have nested `when`s that
fall back to `UnrecognizedKind…Event`). Unknown kinds round-trip as plain `Event`.

## Adding a kind — three registrations

1. **The class**, in its `nipXX…/` package (or `experimental/…`): subclass `Event`,
   `BaseReplaceableEvent` or `BaseAddressableEvent` (see `event-hierarchy.md`), add
   `companion object { const val KIND = n; fun build(...) }`, tag codecs and builder
   extensions (see `tag-patterns.md`).
2. **`EventFactory.create`** — a branch, or the kind parses as plain `Event`.
3. **`LocalCache` dispatch** — `justConsumeInnerInner` in
   `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/cache/EventCache.kt`
   (`LocalCache` is `object LocalCache : EventCache()`). Without a branch the event hits the
   `else` and is dropped with only a `Log.w("Event Not Supported")` — including the
   account's own freshly signed copy. Add it next to a sibling of the same shape.

Then, if relevant: make it `SearchableEvent` (see the `event-store-semantics` skill), add
it to the subscription filter that should fetch it, and add a test in
`quartz/src/commonTest/...`. Regenerate `nip-catalog.md` with `scripts/nip-catalog.py`.

`EventFactory.kt` is one large file every new kind edits — expect merge conflicts and
keep both sides.
