---
name: relay-client
description: Subscription and filter-assembly patterns for the Amethyst relay client layer in `commons/.../relayClient/`. Use when working with compose-scoped subscriptions (`ComposeSubscriptionManager`, `Subscribable`), filter assemblers (`MetadataFilterAssembler`, `ReactionsFilterAssembler`, `FeedMetadataCoordinator`), preloaders (`MetadataPreloader`, `MetadataRateLimiter`), EOSE managers, or any feature that needs to talk to relays lifecycle-aware from a composable. Complements `nostr-expert` (protocol filter syntax) and `kotlin-coroutines` (callbackFlow patterns).
---

# Relay Client & Subscriptions

The layer between `LocalCache`/`Account` and the raw relay connection. Ensures composables only subscribe to what is visible, deduplicates filters across screens, and rate-limits bulk queries like "fetch metadata for these 200 pubkeys".

## When to Use This Skill

- Adding a new screen that needs events it doesn't already have (write a `FilterAssembler`).
- Wiring a composable to subscribe on enter / unsubscribe on leave (`ComposeSubscriptionManager`).
- Preloading metadata / profile pictures for a set of pubkeys (`MetadataPreloader`).
- Deduplicating identical filters across concurrent screens.
- Handling EOSE → "we have historical data, stop showing loading" transitions.

## Layout

All under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/`:

```
relayClient/
├── assemblers/              # "Given these inputs, build this relay Filter"
│   ├── MetadataFilterAssembler.kt      # kind 0 for N pubkeys
│   ├── ReactionsFilterAssembler.kt     # kind 7 for N note ids
│   └── FeedMetadataCoordinator.kt      # coordinates metadata loads for a feed
├── composeSubscriptionManagers/
│   ├── ComposeSubscriptionManager.kt              # interface Subscribable<T>
│   ├── MutableComposeSubscriptionManager.kt       # reference impl
│   └── ComposeSubscriptionManagerControls.kt      # DisposableEffect-style controls
├── eoseManagers/            # EOSE tracking per subscription
├── preload/
│   ├── MetadataPreloader.kt            # bulk-fetch metadata with rate limiting
│   └── MetadataRateLimiter.kt          # token-bucket-ish limiter
└── subscriptions/
    └── KeyDataSourceSubscription.kt    # "this set of keys drives this filter"
```

## Core Concept: `Subscribable<T>`

```kotlin
// composeSubscriptionManagers/ComposeSubscriptionManager.kt
interface Subscribable<T> {
    val state: StateFlow<T>
    fun subscribe()
    fun unsubscribe()
}
```

Every feature-level manager implements or embeds a `Subscribable`. The `MutableComposeSubscriptionManager` reference implementation uses reference-counting so that two screens asking for the same feed share one subscription, and only the last leaver actually closes it.

`ComposeSubscriptionManagerControls.kt` provides `DisposableEffect`-style helpers so composables don't leak subscriptions when the user navigates away or the process backgrounds.

## Typical Flow

```kotlin
@Composable
fun ProfileHeader(pubKey: HexKey) {
    val subscription = rememberSubscribable(pubKey) {
        MetadataFilterAssembler(setOf(pubKey)).toSubscribable()
    }
    LaunchedEffect(pubKey) { subscription.subscribe() }
    DisposableEffect(pubKey) { onDispose { subscription.unsubscribe() } }

    val metadata by subscription.state.collectAsStateWithLifecycle()
    // render metadata…
}
```

The assembler produces a `Filter` (see `quartz/.../nip01Core/relay/RelayFilters.kt` in the quartz module). The `RelayPool` below dedups, opens subs, emits events to `LocalCache.consume`, and emits EOSE through the eose manager.

## Assemblers

An assembler is a plain class:

```kotlin
class MetadataFilterAssembler(
    private val pubKeys: Set<HexKey>,
) {
    fun toFilter(): Filter = filter {
        kinds(MetadataEvent.KIND)
        authors(pubKeys)
        limit(pubKeys.size)
    }
}
```

Assemblers stay pure — no state, no I/O. They're the composition seam: `FeedMetadataCoordinator` takes a list of visible notes and assembles a single metadata filter covering every referenced pubkey.

## Preloaders

`MetadataPreloader` is the "I need metadata for 200 pubkeys, but don't melt my CPU or the relay" path. It uses `MetadataRateLimiter` (token bucket) to throttle bulk fetches and group them into relay-friendly chunks.

Related: `amethyst/.../service/images/ImageLoaderSetup.kt` also uses preloaders for blurhash hydration — they're a general pattern, not metadata-specific.

## EOSE Handling

Each subscription tracks "End of Stored Events" per relay. The eose manager in `eoseManagers/` aggregates per-relay EOSE into a single "loading done" boolean that the UI uses to hide spinners. Without aggregation, composables would flicker as individual relays ack.

## Patterns

### DO

- Build one `Subscribable` per feature scope (screen / dialog / card).
- Dedupe via reference counting — multiple identical subscriptions should share.
- Use `DisposableEffect` / `LaunchedEffect` to tie sub/unsub to lifecycle.
- Put the relay `Filter` building in an assembler so the test is trivial.
- Route bulk metadata through `MetadataPreloader`; don't fire N subscriptions.

### DON'T

- Don't call `RelayPool` / `NostrClient` directly from composables — always through a `Subscribable`.
- Don't hold a subscription past the composable's lifetime — memory & socket leaks.
- Don't build ad-hoc filters inline in composables — assemblers only.
- Don't preload metadata for everything — it's a rate-limited resource and competes with user-visible loads.

## Related

- `nostr-expert/references/tag-patterns.md` — how tags inform what a filter needs to look for.
- `kotlin-coroutines/references/relay-patterns.md` — relay pool internals (sibling layer beneath assemblers).
- `feed-patterns` skill — feeds compose several Subscribables (content + metadata + reactions).
- `account-state` skill — `Account`'s per-kind flows are themselves consumers of the relay-client layer.
