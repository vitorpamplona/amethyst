# Preloaders & Rate Limiting

Bulk-fetch patterns for data that needs to come in over relays but isn't directly user-requested (e.g. "hydrate metadata for 200 pubkeys the user might scroll past", "prefetch images before a gallery opens").

## Files

Under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/preload/`:

- **`MetadataPreloader.kt`** — `class MetadataPreloader(rateLimiter: MetadataRateLimiter, ...)`. Accepts "I need metadata for pubkeys X, Y, Z" requests and batches them into relay-friendly subscriptions.
- **`MetadataRateLimiter.kt`** — token-bucket-style limiter. Caps how many metadata fetches are in-flight simultaneously so relays don't rate-limit the client.

Additional preloader seams worth knowing:
- `MetadataPreloader` is itself called by `FeedMetadataCoordinator` (`assemblers/FeedMetadataCoordinator.kt`) — feeds generate the bulk request set.
- Image prefetching uses an `ImagePrefetcher` interface (see `preload/MetadataPreloader.kt` for the contract). Implementations live in platform code: Android uses Coil's prefetch API, Desktop uses the Skia loader.

## When to Use a Preloader

- You have a **set** of pubkeys/note ids and you want them in `LocalCache` "soon" but the user isn't waiting on any single one.
- You need to **coalesce** many small requests from different composables into one relay subscription.
- You care about **backpressure** — a normal `Subscribable` fires immediately; a preloader defers and batches.

If the user is looking at something right now, use a `Subscribable` instead (`MetadataFilterAssembler`). Preloaders are for "might need this".

## Typical Flow

```kotlin
// Inside a coordinator, e.g. when a feed list emits the set of visible pubkeys
metadataPreloader.request(pubKeys)
// MetadataPreloader deduplicates against what it has already scheduled, clips
// with the rate limiter, and eventually opens a relay sub through the normal
// ComposeSubscriptionManager plumbing.
```

The preloader is process-wide (one instance, injected where needed). Don't instantiate per-screen — that defeats the deduplication.

## Gotchas

- **Rate limits are cooperative.** The limiter throttles the client; relays enforce their own limits. If you see `NOTICE: rate-limited` frames, the preloader's budget is too generous — lower the bucket size.
- **Don't preload sensitive kinds** (encrypted DMs, gift-wrapped events) — they aren't speculatively useful and waste bandwidth.
- **Preload scope should match visibility scope.** When a screen unmounts, cancel its preload requests, otherwise you keep fetching for off-screen data.
- **`MetadataRateLimiter` is not a general rate limiter.** Reuse it only for metadata-like patterns. For publishing rate limits use relay-specific logic in the signer path.

## Related

- `filter-assemblers.md` — the atomic unit the preloader assembles.
- `kotlin-coroutines/references/relay-patterns.md` — how the underlying relay pool handles concurrent subscriptions.
