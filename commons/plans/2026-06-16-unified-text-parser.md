# Unified Text Parser + Note-Cached, Receipt-Time Content Parsing

Status: **proposed** · Owner: Vitor · Drafted 2026-06-16

## Goal

Rewrite Amethyst's content parsers so that:

1. **Parsed content lives on the `Note`** as a cache, so the lifecycle of the
   parsed info matches the lifecycle of the note (born with `loadEvent`, freed
   when the note is GC'd from `LargeSoftCache`).
2. We can **pre-cache media** (videos, images, OpenGraph, PDFs) *before* the
   note hits the screen — but driven by the feed window, not by raw receipt.
3. The **Markdown and regular parsers merge into one** unified pipeline that
   emits a single block + inline model.
4. Parsing runs **from receipt on `LocalCache`** (throttled, content-kinds
   only) so the parsed state is ready by the time the feed binds.
5. The number of objects retained in memory is **minimized** — this is the hard
   constraint that shapes the whole model design.
6. The **Markdown parser itself moves in-repo** — our own CommonMark-derived
   block parser, replacing Halilibo `richtext-commonmark`, which is heavy on
   allocations.

### Decisions locked (2026-06-16)

- **Parse timing**: throttled-eager, content kinds only. A background
  work-queue modeled on `MetadataRateLimiter` parses on receipt, but only for
  kinds that render long content in feeds (kind 1, long-form 30023, picture,
  comments, etc.). Avoids CPU spikes on relay backfills and skips notes never
  rendered.
- **Markdown**: build our **own** block parser modeled on the CommonMark AST
  but memory-lean, emitting the unified segment model. Inline content (URLs,
  `nostr:` mentions, emoji, invoices, …) flows through **one** word parser
  shared with the regular path.
- **Media prefetch**: feed-driven byte prefetch. Cheap metadata (URLs,
  dimensions, blurhash, mime) is extracted at parse time; byte downloads (video
  ranges, images, OG HTML, PDFs) fire only as a note approaches the feed
  window.

## Current state (grounded)

| Concern | Where it is today |
|---|---|
| Regular parser | `commons/.../richtext/RichTextParser.kt` → `parseText()` returns `RichTextViewerState` |
| Segment model | `commons/.../richtext/RichTextParserSegments.kt` (`RichTextViewerState` → `ParagraphState` → `Segment` subclasses) |
| Media model | `commons/.../richtext/MediaContentModels.kt` (`MediaUrlImage/Video/Pdf`) |
| Markdown detect | `amethyst/.../service/CachedRichTextParser.isMarkdown()` (allocation-free scan) |
| Markdown render | `amethyst/.../ui/components/markdown/RenderContentAsMarkdown.kt` via Halilibo `CommonmarkAstNodeParser` + `BasicMarkdown` + `MarkdownMediaRenderer` |
| Parse cache | `amethyst/.../service/CachedRichTextParser` — global `LruCache<Int, RichTextViewerState>(50)`, keyed by content hash, **not** note-scoped; computed lazily during composition |
| Invocation | `RichTextViewer` composable: `remember(content){isMarkdown}` then `remember(content,tags){parseText}` — parsing happens **in composition, on render** |
| Note model | `commons/.../model/Note.kt` — `event/author/replyTo` set once by `loadEvent`; derived collections (replies, reactions, zaps…) updated as related events arrive, each guarded by `syncLock` and notified via `flowSet?.X?.invalidateData()` |
| Note cache | `LargeSoftCache<HexKey, Note>` (`WeakReference`), so anything stored on a Note dies with it — no extra eviction needed |
| Consume | `LocalCache.consumeRegularEvent()` / `consume(...)` runs off the main thread (`checkNotInMainThread()`); heavy parsing is **not** done here today |
| Media caches | Coil (images, PDFs via `PdfFetcher`), Media3 `SimpleCache` (video). OG previews via `UrlPreview`/`OpenGraphParser` have **no** prefetch. `MetadataPreloader`/`MetadataRateLimiter` is the model to copy for warm-caching |
| Desktop | `desktopApp/.../service/DesktopCachedRichTextParser.kt` — separate LRU + simplified markdown detect |

Key tension: #4 (parse on receipt for all) vs #5 (minimum memory). Resolved by
(a) parsing only content kinds, (b) throttling, (c) a compact model, and (d)
soft-referenced Notes so retention is GC-bounded.

## Target architecture

### 1. One parsed model, living on the Note

Introduce `ParsedContent` in `commons/.../richtext/` and attach it to `Note`:

```kotlin
// Note.kt
@Volatile var parsedContent: ParsedContent? = null      // computed off-thread
```

`ParsedContent` replaces `RichTextViewerState` as the rendered model and unifies
markdown + regular:

```
ParsedContent
 ├─ blocks: List<Block>             // markdown gives >1; plain text gives paragraphs
 │   Block = Paragraph | Heading(level) | BlockQuote | ListBlock | CodeBlock
 │           | Table | ThematicBreak | ImageGallery
 │   each leaf block holds inline: List<Inline>   // the shared word model
 ├─ media:  Map<String, MediaRef>   // url -> lightweight ref (no bytes)
 └─ emoji:  Map<String, String>     // custom emoji shortcode -> url
```

The regular path emits a single implicit document of `Paragraph` blocks; the
markdown path emits real headings/lists/quotes/code/tables. **Both** funnel
their text through the *same* inline tokenizer (today's `wordIdentifier` logic),
so `nostr:` mentions, hashtags, emoji, invoices, cashu, links, etc. render
identically inside or outside markdown — the core of merge goal #3.

### 2. Memory model (constraint #5)

This is the part that justifies writing our own markdown parser instead of
keeping Halilibo's AST resident.

- **No substring copies.** Inline tokens reference `(start, end)` offsets into
  the original `event.content` string (already retained on the Event) instead
  of allocating a new `String` per word. A token is a `@JvmInline value class`
  packing `start`/`end`/`type` where possible, or a small class with int
  offsets. The renderer slices on demand.
- **Drop the per-paragraph `ImmutableList` wrappers** (`persistentListOf`) in
  favor of plain arrays sized once; Compose stability comes from `@Immutable`
  on the container, not from kotlinx persistent collections.
- **`MediaRef` is metadata only** — url + dim + blurhash + thumbhash + mime +
  contentWarning. No bytes, no Coil/ExoPlayer objects. ~6 fields.
- **Parse lazily-but-once per content**, deduped: identical content quoted in N
  notes shares one `ParsedContent` via a content-hash intern map *with weak
  values*, so retention still tracks live notes.
- Target: a typical kind-1 note's `ParsedContent` should be a handful of small
  objects + one int-array, materially below today's
  `RichTextViewerState`/persistent-list footprint.

> Measure before/after with the existing benchmark module and a heap dump on a
> loaded home feed. Memory reduction is the acceptance gate for the model.

### 3. Our own Markdown block parser (#6)

A single-pass, allocation-lean block scanner derived from the CommonMark spec
(not a fork of commonmark-java's object-heavy AST):

- Block starts: ATX/Setext headings, block quotes, bullet/ordered lists,
  fenced/indented code, tables (GFM), thematic breaks. Reuse the proven trigger
  logic already in `CachedRichTextParser.isMarkdown()` — that scanner becomes
  the front half of the real parser, so detection and parsing share code.
- Inline phase delegates to the **shared word tokenizer** (§1).
- Emits `List<Block>` directly into `ParsedContent` — no intermediate AST tree
  retained.
- New Compose renderer: block-level composables (`Heading`, `BlockQuote`,
  `BulletList`, `CodeBlock`, `MarkdownTable`) in `commons` replacing
  `BasicMarkdown`/`RichText`. This **removes the Halilibo `richtext-*`
  dependencies** — confirm in `gradle/libs.versions.toml` and delete once the
  renderer is at parity.

Markdown vs plain is decided once at parse time (the `isMarkdown` result is
stored on `ParsedContent`), not re-detected on every recomposition.

### 4. Receipt-time parse pipeline (#4)

A `ContentParseQueue` in `commons` (sibling of `MetadataRateLimiter`):

- `LocalCache.consume*` for content kinds calls `queue.enqueue(note)` after
  `loadEvent`, instead of parsing inline (keep `consume` fast).
- Queue drains on `Dispatchers.Default`, rate-limited / batched, computes
  `ParsedContent`, writes `note.parsedContent`, then
  `flowSet?.metadata?.invalidateData()` so any bound UI refreshes.
- Idempotent + content-keyed; re-enqueue on event replacement (addressables).
- `clearChildLinks()` / event replacement sets `parsedContent = null`.
- Renderers read `note.parsedContent` first; on miss (not yet parsed, or a kind
  we don't pre-parse) they parse synchronously and back-fill the field — so the
  UI never blocks on the queue and off-screen notes stay correct.

### 5. Feed-driven media prefetch (#2)

- At parse time we already have `MediaRef`s (urls + dims). Cheap, no I/O.
- A feed-window prefetcher (extending the `BottomBarFeedPreloaders` /
  `ImagePrefetcher` pattern) walks notes entering/approaching the viewport and:
  - warms Coil for image + OG-preview images,
  - warms Media3 `SimpleCache` with the opening byte range of videos,
  - fetches + caches OG HTML via a new `OpenGraphCache` (today there is none),
  - pre-fetches PDF first page via `PdfFetcher`.
- Rate-limited and Tor-aware (reuse the per-URL OkHttp client selection).
- Strictly feed-driven: notes that are merely received but never approach the
  window never download bytes.

## Phasing (each phase ships independently, green build + tests)

**P0 — Foundation (no behavior change).** Add `ParsedContent` model + shared
inline tokenizer extracted from `RichTextParser.wordIdentifier`, with the
regular path producing `ParsedContent` and an adapter back to the existing
renderer. Unit tests porting current `RichTextParser` test cases. *No markdown,
no Note field yet.*

**P1 — Note cache + receipt pipeline.** Add `Note.parsedContent` + `ContentParseQueue`;
wire content-kind `consume` paths to enqueue; renderers read-through to the
Note. Achieves #1 + #4 for the regular path. Benchmark memory.

**P2 — Memory model.** Switch inline tokens to offset-based value classes, drop
persistent-list wrappers, add weak intern map. Acceptance gate: measured heap
reduction vs P1. Achieves #5.

**P3 — Markdown merge.** In-repo block parser + block renderer in `commons`;
route markdown text nodes through the shared tokenizer; delete Halilibo
`richtext-*`. Achieves #3 + #6. Parity tests against the existing markdown
preview corpus in `RenderContentAsMarkdown.kt`.

**P4 — Feed media prefetch.** `OpenGraphCache` + feed-window prefetcher for
images/video/OG/PDF. Achieves #2.

**P5 — Cleanup.** Remove `CachedRichTextParser` global LRU and
`DesktopCachedRichTextParser`; both platforms read from the Note. Update CLI
(`amy`) if it renders content.

## Risks / open questions

- **Hot path correctness.** `RichTextParser` has many edge cases (HLS mime,
  schemeless URLs, nowhere links, math spans, galleries, secret emoji). Every
  phase must keep the existing test corpus green; port tests before refactor.
- **iOS / KMP.** Model + parser live in `commonMain`; no Android-only APIs
  (`android.util.LruCache` must not leak into commons — use a KMP cache).
- **Markdown parity.** Our parser must match the current corpus
  (tables, nested quotes, footnotes, fenced code) before Halilibo is removed;
  footnotes may be descoped — confirm.
- **Throttling vs freshness.** A note rendered before the queue reaches it
  parses synchronously on the UI thread (same cost as today) — acceptable, but
  verify the queue keeps up under fast scroll.
- **Desktop renderer** must move to the shared block composables too, or keep a
  thin adapter.

## Test strategy

- Port `RichTextParserTest` / URL / math / gallery suites onto `ParsedContent`.
- New `MarkdownBlockParserTest` against the preview corpus + CommonMark spec
  subset we support.
- Memory: macrobenchmark + heap dump on a seeded home feed, P1 vs P2.
- `amy` interop: a `parse` verb dumping `ParsedContent` as JSON for golden
  tests (optional, aids regression).
