# Filter Assemblers

An assembler takes a plain input (a set of pubkeys, a set of note ids, a hashtag, a time range) and produces a relay `Filter`. Assemblers are pure, test-friendly, and composable.

## Concrete Assemblers

Under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/assemblers/`:

- **`MetadataFilterAssembler.kt`** — `class MetadataFilterAssembler(pubKeys: Set<HexKey>)`. Emits a `Filter` for kind 0 over those authors, respecting relay `limit` conventions.
- **`ReactionsFilterAssembler.kt`** — builds a kind 7 filter with `#e` tag set to the note ids you want reactions for.
- **`FeedMetadataCoordinator.kt`** — higher-order coordinator: given a list of currently-visible notes, figure out which pubkeys and note ids still need metadata and reactions, and produce one (or two) consolidated filters.

## Subscription Helper

`commons/.../relayClient/subscriptions/KeyDataSourceSubscription.kt` — wraps an assembler plus a "data source" that keeps the assembler's input set up to date. E.g. "the set of pubkeys visible in the current feed" is a data source; when the feed scrolls, the set changes, and the subscription re-emits a new filter.

## RelayFilter DSL (quartz)

The `Filter` type the assemblers produce is defined in `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/RelayFilters.kt`. The DSL roughly looks like:

```kotlin
filter {
    kinds(MetadataEvent.KIND)
    authors(pubKeys)
    since(TimeUtils.oneHourAgo())
    until(TimeUtils.now())
    limit(200)
    // tag filters
    tag("e", noteIds)
    tag("p", pubKeys)
    tag("t", hashtags)
}
```

Builders for individual tag types match the `TagArrayBuilder` conventions (see `nostr-expert/references/tag-patterns.md`).

## Writing a New Assembler

Recipe for `FooFilterAssembler`:

1. Create the file under `assemblers/`.
2. Define a small immutable `data class FooQuery(...)` holding the inputs — or take constructor parameters directly if simple.
3. Expose one method: `fun toFilter(): Filter` (or `toFilters()` if you need multiple).
4. Keep the class deterministic and side-effect-free. No cache reads, no coroutines.
5. Add a unit test that asserts the `Filter`'s JSON serialization — `quartz`'s filter serialization is stable, so golden tests work.

## Coordinator Pattern

When a feature needs several related filters (content + reactions + zaps + metadata), write a **coordinator** (see `FeedMetadataCoordinator.kt`) that takes higher-level inputs and fans out to several single-purpose assemblers. Coordinators compose; they don't talk to relays themselves.

## Gotchas

- **`limit` matters** on large author sets. Without it, relays may rate-limit or truncate.
- **Use `since` liberally** to avoid pulling years of history; a feed usually only needs `TimeUtils.oneHourAgo()` or similar.
- **Don't put filter logic inline in composables** — it ends up duplicated and desynced across screens.
- **`toFilter()` should be cheap** — assemblers may be called on every recomposition until you wrap them in a subscription.
