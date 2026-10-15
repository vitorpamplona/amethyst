# EventFactory and registering a new kind

[`EventFactory`](https://github.com/vitorpamplona/quartz/blob/main/core/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt)
(in `quartz-core`) turns a parsed `(id, pubKey, createdAt, kind, tags, content, sig)` into
the typed `Event` subclass. Callers normally reach it through `Event.fromJson(...)` or the
relay message parser.

It is a registry, not a `when`: every Quartz artifact ships an `EventKindProvider`
(`SocialEventKinds`, `PaymentEventKinds`, `MarmotEventKinds`, ...) that registers its
classes, and `EventFactory` loads the providers of the artifacts on the classpath
(by class name on JVM/Android, at binary load on native). Amethyst depends on the `quartz`
umbrella, so every kind is registered here.

```kotlin
object SocialEventKinds : EventKindProvider {
    override fun registerKinds(registry: EventKindRegistry) {
        registry.register(TextNoteEvent.KIND, ::TextNoteEvent)
        …
    }
}

EventFactory.create<T>(id, pubKey, createdAt, kind, tags, content, sig)
EventFactory.isKnownKind(kind)   // typed class exists?
EventFactory.probe(kind)         // a tagless instance, used by the stores' searchable checks
EventFactory.factories[kind] = EventBuilder { … }   // app-side runtime registrations
```

Kinds that unrelated apps share pick their class from tags: the kind's home module
registers a default class (often an `UnrecognizedKind…Event`), each app's class adds a
`registry.variant(kind, priority, matches = …)`, and `registry.probeAs(kind, …)` names the
class `probe` answers with (kinds 38000, 30301, 30302, 38384-38386, 31986, 39005, 20001).
Unknown kinds round-trip as plain `Event`.

## Adding a kind

Steps 1 and 2 happen in the [Quartz repository](https://github.com/vitorpamplona/quartz), and
Amethyst picks them up when it bumps `quartz` in `gradle/libs.versions.toml`.

1. **The class**, in its `nipXX…/` package (or `experimental/…`) in the right Quartz module:
   subclass `Event`, `BaseReplaceableEvent` or `BaseAddressableEvent` (see
   `event-hierarchy.md`), add `companion object { const val KIND = n; fun build(...) }`, tag
   codecs and builder extensions (see `tag-patterns.md`).
2. **The module's `EventKindProvider`**: `registry.register(MyEvent.KIND, ::MyEvent)`, or the
   kind parses as plain `Event`.
3. **`LocalCache` dispatch** (here, in Amethyst): `justConsumeInnerInner` in
   `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/cache/EventCache.kt`
   (`LocalCache` is `object LocalCache : EventCache()`). Without a branch the event hits the
   `else` and is dropped with only a `Log.w("Event Not Supported")` — including the
   account's own freshly signed copy. Add it next to a sibling of the same shape.

Then, if relevant: make it `SearchableEvent` and override `indexableFields()` (see the
`event-store-semantics` skill), add it to the subscription filter that should fetch it, and
add a test beside the class in Quartz. Regenerate `nip-catalog.md` with `scripts/nip-catalog.py`.
