# Event hierarchy

All in [core/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/core/](https://github.com/vitorpamplona/quartz/tree/main/core/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/core) unless noted.

```
IEvent (marker interface)
└── Event(id, pubKey, createdAt, kind, tags, content, sig)      Event.kt, @Immutable, open
    ├── BaseReplaceableEvent   kinds 0, 3, 10000–19999; dTag() = FIXED_D_TAG ("")
    ├── BaseAddressableEvent   kinds 30000–39999; dTag() from tags, address() = Address(kind, pubKey, dTag)
    └── every other typed event class (subclasses Event directly)
```

Both bases implement `AddressableEvent` (`dTag()`, `address()`), so a replaceable event
also has an `Address` — with an empty d-tag. `BaseReplaceableEvent` does **not** extend
`BaseAddressableEvent`.

## Kind ranges (`Kind.kt`, verbatim)

```kotlin
fun Kind.isRegular() = this > 0 && this < 10_000 && this != ContactListEvent.KIND
fun Kind.isEphemeral() = this >= 20_000 && this < 30_000
fun Kind.isReplaceable() = this == MetadataEvent.KIND || this == ContactListEvent.KIND || (this >= 10_000 && this < 20_000)
fun Kind.isAddressable() = this >= 30_000 && this < 40_000
```

Note `isRegular()` excludes kind 0 and kind 3 and starts at 1, not 1000.

## Shape of a typed event

Copy an existing class in the same NIP. `TextNoteEvent` (`nip10Notes/TextNoteEvent.kt`):

```kotlin
class TextNoteEvent(id: HexKey, pubKey: HexKey, createdAt: Long, tags: Array<Array<String>>, content: String, sig: HexKey) :
    BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider, AddressHintProvider, PubKeyHintProvider, IForkableEvent, SearchableEvent {
    companion object {
        const val KIND = 1
        fun build(note: String, replyingTo: EventHintBundle<TextNoteEvent>? = null, …,
                  initializer: TagArrayBuilder<TextNoteEvent>.() -> Unit = {}) =
            eventTemplate(KIND, note, createdAt) { … initializer() }
    }
}
```

House rules visible there:

- The constructor takes no `kind`; it passes its `KIND` constant up.
- Construction goes through a `companion` `build(...)` that returns an `EventTemplate<T>`
  (unsigned); a `NostrSigner` signs it (`signer.sign(template)`, see `auth-signers`).
- References to other events come in as `EventHintBundle<T>` (`nip01Core/hints/`), which
  carries the relay hint along with the event.

## Optional interfaces

| Interface | File | Purpose |
|---|---|---|
| `EventHintProvider` / `AddressHintProvider` / `PubKeyHintProvider` | `nip01Core/hints/HintProviders.kt` | `eventHints()`/`linkedEventIds()` etc. — feed the relay-hint indexes and `LocalCache` gatherers |
| `SearchableEvent` | `nip50Search/SearchableEvent.kt` | NIP-50 full-text indexing — see the `event-store-semantics` skill |
| `PrivateTagArrayEvent` / `PrivateReplaceableTagArrayEvent` | `nip51Lists/` | lists with NIP-44-encrypted private tags in `content` |

## Validation (`nip01Core/crypto/EventExt.kt`)

`generateId()`, `verifyId()`, `verifySignature()`, `verify(): Boolean`, and
`checkSignature()` (throws). JSON: `Event.fromJson(json)` / `Event.fromJsonOrNull(json)` /
`event.toJson()` via `OptimizedJsonMapper`.
