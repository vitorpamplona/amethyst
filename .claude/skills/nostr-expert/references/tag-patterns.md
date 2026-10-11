# Tag patterns in Quartz

`TagArray = Array<Array<String>>` (raw JVM arrays on purpose — see AGENTS.md › Kotlin
Style on the `fast*` operators). Quartz never passes tags around as `List`s of objects;
each tag type is a small codec class plus extension files.

## The per-tag file layout

Every tag lives in its NIP package as a quartet. `nip14Subject/` is the smallest
complete example:

| File | Holds | Example (`nip14Subject/`) |
|---|---|---|
| `XxxTag.kt` | `TAG_NAME`, `parse(tag)`, `assemble(...)`, `isTagged`/`hasTagWithContent` | `SubjectTag.parse`, `SubjectTag.assemble(subject)` |
| `TagArrayBuilderExt.kt` | writer: `fun TagArrayBuilder<T>.xxx(...)` | `fun TagArrayBuilder<TextNoteEvent>.subject(s) = addUnique(SubjectTag.assemble(s))` |
| `TagArrayExt.kt` | readers on `TagArray` | `fun TagArray.subject() = firstNotNullOfOrNull(SubjectTag::parse)` |
| `EventExt.kt` | the same readers on `Event` | `fun Event.subject() = tags.subject()` |

Follow this shape for a new tag. Typing the builder receiver
(`TagArrayBuilder<TextNoteEvent>` rather than `<T : Event>`) is how Quartz restricts a
tag to the kinds that may carry it; generic tags (`pTag`, `eTag`, `aTag`, `hashtag`,
`alt`) use `fun <T : Event> TagArrayBuilder<T>.…`.

There are ~230 `TagArrayBuilderExt.kt` and ~160 `TagArrayExt.kt` files — grep for an
existing builder before writing `add(arrayOf(...))` by hand.

## Core tags (`nip01Core/tags/`)

| Dir | Codec | Builder / reader examples |
|---|---|---|
| `people/` | `PTag` (`parse`, `parseKey`, `assemble(pubkey, relayHint)`) | `pTag(pubkey, relayHint)`, `pTags(list)`, `TagArray.taggedUserIds()`, `isTaggedUser(hex)` |
| `events/` | `ETag` (`parse`, `parseId`, `assemble(id, relay, author)`) | `eTag(ETag)` |
| `aTag/` | `ATag` (`parse`, `parseAddress`, `parseAddressId`) | `aTag(ATag)` |
| `hashtags/` | `HashtagTag` | `hashtag(tag)` writes **two** tags (`assembleDualCase`: original + lowercase) |
| `dTag/`, `kinds/`, `geohash/`, `publishedAt/`, `references/` | one codec each | |

NIP-10 reply markers are `MarkedETag` in `nip10Notes/tags/`, not `ETag`. Relay hints are
`NormalizedRelayUrl`, never raw strings.

## TagArrayBuilder (`nip01Core/core/TagArrayBuilder.kt`)

Builder methods: `add`, `addFirst`, `addUnique` (replaces every tag with that name),
`addUniqueValueIfNew`, `addAll`, `addAllUnique`, `remove(name)`, `remove(name, value)`,
`removeIf`. Entry points:

```kotlin
// nip01Core/core/TagArrayBuilder.kt
inline fun <T : Event> tagArray(initializer: TagArrayBuilder<T>.() -> Unit = {}): TagArray

// nip01Core/core/TagArray.kt — copy existing tags into a builder and edit them
fun <T : Event> TagArray.builder(initializer: TagArrayBuilder<T>.() -> Unit = {})
```

To edit a signed event's tags, use `eventUpdate(base) { … }` / `event.update { … }` from
`nip01Core/signers/EventTemplate.kt`, not a hand-copied array.

## Generic readers (`nip01Core/core/TagArray.kt`)

`firstTagValue(name)`, `firstTagValueAsLong`, `firstTagValueFor(vararg names)`,
`hasTagWithContent(name)`, `hasTagName`, `isTagged(name, value)`, `isAnyTagged`,
`anyTagged`, `forEachTagged`, `mapValueTagged`, `tagValueContains`. Prefer the typed
reader from the tag's `TagArrayExt.kt` when one exists. There is no `allTags`/`tagValue`.
