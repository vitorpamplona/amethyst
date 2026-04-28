# Common Utility Functions

Canonical helpers that repeatedly come up when working in Amethyst. Prefer these to hand-rolling equivalents.

## Formatting (commons)

All under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/util/`:

- **`NumberFormatters.kt`**
  - `countToHumanReadable(counter: Int, noun: String): String` — `1500 → "1K items"`, `2_500_000 → "2M items"`. Suffixes `K`, `M`, `G`.
  - `countToHumanReadableBytes(bytes: Int): String` — `1024 → "1 KB"`, scales through KB/MB/GB/TB.
- **`PubKeyFormatter.kt`** — condense an npub to `npub1abc…xyz` with a symmetric prefix/suffix truncation. Use in chips and small UI that show an author.
- **`EmojiUtils.kt`** — parse custom emoji (`:name:`), render bridging to NIP-30 `emoji` tags.
- **`IterableUtils.kt`** — small shortcuts like `firstNotNullOf` variants, chunking helpers.
- **`PlatformNumberFormatter.kt`** — expect/actual locale-aware number formatting (delegates to `NumberFormat` on JVM/Android).

## Time (quartz)

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/TimeUtils.kt` — the source of truth for "now in Nostr seconds" and common offsets:

```kotlin
TimeUtils.now()               // Long seconds since epoch (Nostr `created_at`)
TimeUtils.oneMinuteAgo()
TimeUtils.fiveMinutesAgo()
TimeUtils.fifteenMinutesAgo()
TimeUtils.oneHourAgo()
TimeUtils.oneDayAgo()
TimeUtils.oneWeekAgo()

TimeUtils.withinTenMinutes(other)   // |now - other| < 10m
```

Constants (`TEN_SECONDS`, `ONE_MINUTE`, `FIVE_MINUTES`, `TEN_MINUTES`, `FIFTEEN_MINUTES`, `ONE_HOUR`, `EIGHT_HOURS`, `ONE_DAY`, `ONE_WEEK`) are in seconds and are what every subscription filter and staleness check uses — keep using them instead of magic numbers.

## Hex / bytes / strings (quartz)

Under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/`:

- **`Hex.kt`** — `ByteArray.toHex()`, `String.hexToByteArray()`. MPP-friendly, no java.util.
- **`StringUtils.kt`** — generic helpers (normalization, truncation).
- **`StringExt.kt`** — small extensions (e.g. safe substring).
- **`UriParser.kt`** — NIP-19 / nostr URI friendly URL parsing without java.net.
- **`UrlEncoder.kt`** / **`Rfc3986.kt`** — percent-encoding / decoding for URL-safe content.
- **`UnicodeNormalizer.kt`** — NFC normalization for search/matching.

## Threading & coroutines

- **`commons/src/commonMain/.../threading/Threading.kt`** — shared dispatchers and `CoroutineScope` helpers for commonMain code.
- **`amethyst/src/main/java/.../service/CoroutinesExt.kt`** — Android-only helpers: `launchIO(block)`, `launchMain(block)` built on top of `Dispatchers.IO` / `Dispatchers.Main`. Use these in ViewModels and services to stop re-spelling the dispatcher every time.
- **`amethyst/src/main/java/.../service/MainThreadChecker.kt`** — debug assertion helper for catching main-thread misuse during dev.

## Quartz iterables & JSON

- **`quartz/.../utils/IterableExt.kt`** — mutation-free filter/map/group helpers used by the cache layer.
- **`quartz/.../utils/JsonElementExt.kt`** — safe navigation for Jackson nodes when parsing unknown-shape JSON.
- **`quartz/.../kotlinSerialization/OptimizedJsonMapper.kt`** — shared Jackson mapper with reified `fromJson<T>(...)` / `toJson(...)`; prefer this to spinning up a local `ObjectMapper`.

## Number formatting for Android display

`amethyst/src/main/java/.../service/CountFormatter.kt` and `ByteFormatter.kt` wrap the commons formatters with Android-specific pluralization / locale. Use them from Android UI; use the commons functions directly from commonMain.

## When to Add vs Reuse

Before introducing a new helper:

1. `grep -r "fun count\|fun format\|fun toHuman" commons/ quartz/` — there is almost certainly something already.
2. If you're about to write `System.currentTimeMillis() / 1000` — use `TimeUtils.now()`.
3. If you're about to write `String.format("%.1f K", n / 1000.0)` — use `countToHumanReadable`.
4. If you need locale-specific rendering on both Android and Desktop, reach for `PlatformNumberFormatter` (expect/actual) rather than hard-coding.
