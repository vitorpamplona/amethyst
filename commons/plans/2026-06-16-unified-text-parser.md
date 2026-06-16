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
- **Rendering unification (visual drift accepted)**: markdown now renders
  through Amethyst's **own** components on the **same** rendering stack as
  regular notes — shared typography (text size), color, and link/mention
  styling. Block constructs that only existed in the markdown path (code
  blocks, tables, block quotes, headings) get **brought into the regular
  rendering stack** and may look slightly different from the old Halilibo
  output. That is expected and acceptable: we hold **structural/content**
  parity (the right blocks and inline tokens), **not pixel parity**.

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

## Module placement (two-layer split)

The parser is split so that **external Quartz consumers get an integrated,
UI-free parser**, while Amethyst's screens get an enriched layer on top.

**Quartz — the integrated content parser + a plain data model. No UI elements
at all** (no Compose, not even the `@Immutable`/`@Stable` stability
annotations; plain data classes / `value class`es). This is what ships to
external Quartz users. It contains:

- the **inline tokenizer** (today's `wordIdentifier` logic): URLs, `nostr:` /
  NIP-19 references (decoded to pointers), hashtags, NIP-30 custom-emoji refs,
  lightning invoices, LNURL, cashu, clink offers, relay/blossom URIs, emails,
  phones, math spans, plain text — as an allocation-lean, **offset-based** token
  stream (#5 benefits external users too);
- the **markdown block-structure parser** (headings, lists, quotes, fenced/
  indented code, GFM tables, thematic breaks, footnote definitions +
  references) — pure text→structure, no rendering;
- a **`MediaRef`** carrying only protocol metadata from NIP-92/94 (url, mime,
  dim, blurhash, thumbhash, hash, alt) — no bytes, no Coil/Exo, no UI.

Everything it needs already lives in quartz (`nip19Bech32`, `nip21UriScheme`,
`nip30CustomEmoji`, `nip92IMeta`, `nip94FileMetadata`). The three commons
helpers it currently borrows move down: `EmojiCoder` → quartz, `isValidUrl` →
reuse quartz's `Rfc3986`/`HttpUrlFormatter`, and `ImmutableListOfLists` →
raw quartz tag arrays. Proposed home: a new cross-cutting
`quartz/.../content/` package (content parsing spans many NIPs, so it isn't one
NIP folder). External integration notes go in the `quartz-integration` skill.

**Commons — the screen layer (all UI lives here).** Builds on quartz's parse
and adds only what the screen needs:

- the Compose **renderer** (block + inline composables) on the regular note
  rendering stack;
- presentation-only decisions: **gallery grouping** of image paragraphs,
  `@Immutable` render wrappers, `MediaUrlContent` with UI/account fields
  (`callbackUri`, `authorPubKey` for click routing);
- the **`Note.parsedContent` cache**, the **receipt-time parse queue**, and
  the **feed-driven media prefetch**.

The cache on `Note` (commons) holds the screen model, which embeds the quartz
core parse — so our UI gets everything and external users get the quartz core
directly. quartz stays a pure function (`ContentParser.parse(content, tags)`);
caching/lifecycle is a commons concern, since `Note` and `LocalCache` live in
commons.

## Target architecture

### 1. One parsed model, split across the two layers

Quartz exposes the **core** parse (UI-free); commons wraps it as the cached,
renderable model attached to `Note`:

```kotlin
// quartz/.../content/ — UI-free, shipped to external users
class ContentParser { fun parse(content: String, tags: Array<Array<String>>): ParsedNote }

// commons Note.kt — screen model embedding the quartz core
@Volatile var parsedContent: ParsedContent? = null      // computed off-thread
```

`ParsedNote` (quartz) is the UI-free structure; `ParsedContent` (commons) is the
render model that embeds it and adds galleries + media wrappers. Together they
replace `RichTextViewerState`, unifying markdown + regular:

```
ParsedNote (quartz, no UI)
 ├─ blocks: List<Block>             // markdown gives >1; plain text gives paragraphs
 │   Block = Paragraph | Heading(level) | BlockQuote | ListBlock | CodeBlock
 │           | Table | ThematicBreak | FootnoteDefs
 │   each leaf block holds inline: List<Inline>   // the shared offset-based tokens
 ├─ media:  Map<String, MediaRef>   // url -> protocol metadata (no bytes, no UI)
 └─ emoji:  Map<String, String>     // custom emoji shortcode -> url

ParsedContent (commons, UI) = ParsedNote + ImageGallery grouping + MediaUrlContent
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
  offsets. The renderer slices on demand. (This lives in the quartz core, so
  external users get the lean representation too.)
- **Drop the per-paragraph `ImmutableList` wrappers** (`persistentListOf`) in
  favor of plain arrays sized once. The quartz model carries no Compose
  annotations; the commons render wrapper supplies `@Immutable` stability where
  Compose needs it.
- **`MediaRef` is metadata only** — url + dim + blurhash + thumbhash + mime +
  contentWarning. No bytes, no Coil/ExoPlayer objects. ~6 fields.
- **Parse lazily-but-once per content**, deduped: identical content quoted in N
  notes shares one `ParsedContent` via a content-hash intern map *with weak
  values*, so retention still tracks live notes.
- Target: a typical kind-1 note's parse should be a handful of small
  objects + one int-array, materially below today's
  `RichTextViewerState`/persistent-list footprint.

> Measure before/after with the existing benchmark module and a heap dump on a
> loaded home feed. There is **no fixed heap target** — we track the footprint
> across phases and keep it as small as possible; each phase should not regress
> the previous one's measured footprint.

### 3. Our own Markdown block parser (#6)

A single-pass, allocation-lean block scanner derived from the CommonMark spec
(not a fork of commonmark-java's object-heavy AST). The **parser** lives in the
quartz core (UI-free, so external longform/NIP-23 clients get it); only the
**renderer** is in commons:

- Block starts: ATX/Setext headings, block quotes, bullet/ordered lists,
  fenced/indented code, tables (GFM), thematic breaks. Reuse the proven trigger
  logic already in `CachedRichTextParser.isMarkdown()` — that scanner becomes
  the front half of the real parser, so detection and parsing share code.
- **Footnotes are supported** (GFM-style): the block parser collects footnote
  *definitions* (`[^id]: ...`, including their continuation/indented
  paragraphs) into `ParsedNote`, and the inline tokenizer emits footnote
  *reference* tokens (`[^id]`). The renderer shows references as superscript
  links and renders the collected definitions as a footnotes block at the end
  of the document, on the regular note stack. Definition bodies run through the
  shared inline tokenizer like any other text.
- Inline phase delegates to the **shared word tokenizer** (§1).
- Emits `List<Block>` directly into `ParsedNote` — no intermediate AST tree
  retained.
- New Compose renderer: block-level composables (`Heading`, `BlockQuote`,
  `BulletList`, `CodeBlock`, `MarkdownTable`) in `commons` replacing
  `BasicMarkdown`/`RichText`. These render on the **regular note rendering
  stack** — same typography/color/link styling as a plain note, *not* a
  separate `MarkdownTextStyle`/`markdownStyle`. This **removes the Halilibo
  `richtext-*` dependencies** — confirm in `gradle/libs.versions.toml` and
  delete once the renderer covers the supported block set (parity is
  structural, not visual; see below).

Because the two paths now share one rendering stack, block constructs that were
markdown-only (code blocks, tables, quotes, headings) become first-class in the
regular renderer. Expect minor visual differences from the old Halilibo output;
this is an accepted goal, not a regression.

Markdown vs plain is decided once at parse time in the quartz core (the
`isMarkdown` result is stored on `ParsedNote`), not re-detected on every
recomposition.

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

**P0 — Foundation (no behavior change).** In **quartz**, add the UI-free
`ParsedNote` model + shared inline tokenizer (ported from
`RichTextParser.wordIdentifier`), moving `EmojiCoder`/`isValidUrl` down and
dropping `ImmutableListOfLists` for raw tag arrays. In **commons**, wrap it as
`ParsedContent` with an adapter back to the existing renderer. Unit tests
porting current `RichTextParser` test cases into quartz. *No markdown, no Note
field yet.*

**P1 — Note cache + receipt pipeline.** Add `Note.parsedContent` (commons) +
`ContentParseQueue` (commons); wire content-kind `consume` paths to enqueue;
renderers read-through to the Note. Achieves #1 + #4 for the regular path.
Benchmark memory.

**P2 — Memory model.** Switch the quartz tokens to offset-based value classes,
drop persistent-list wrappers, add the weak intern map. Track the measured
footprint (no fixed target) and keep it as small as possible without regressing
P1. Achieves #5.

**P3 — Markdown merge.** In-repo block parser in **quartz** + block renderer in
**commons**, rendering on the **regular note stack**; route markdown text nodes
through the shared tokenizer; delete Halilibo `richtext-*`. Achieves #3 + #6.
**Structural** parity tests (correct blocks + inline tokens) against the
existing markdown preview corpus in `RenderContentAsMarkdown.kt`; **visual**
drift from the old Halilibo styling is accepted, not gated.

**P4 — Feed media prefetch.** `OpenGraphCache` + feed-window prefetcher for
images/video/OG/PDF. Achieves #2.

**P5 — Cleanup.** Remove `CachedRichTextParser` global LRU and
`DesktopCachedRichTextParser`; both platforms read from the Note. Update CLI
(`amy`) if it renders content.

## Risks / open questions

- **Hot path correctness.** `RichTextParser` has many edge cases (HLS mime,
  schemeless URLs, nowhere links, math spans, galleries, secret emoji). Every
  phase must keep the existing test corpus green; port tests before refactor.
- **iOS / KMP.** The quartz core parser + model live in `quartz/commonMain`
  with **no UI / no Compose** (not even stability annotations); commons render
  layer + cache live in `commons/commonMain`. No Android-only APIs in either
  (`android.util.LruCache` must not leak down — use a KMP cache).
- **External API surface.** quartz's `ContentParser`/`ParsedNote` becomes a
  public, semver-relevant API for outside consumers — keep it stable, minimal,
  and documented (update the `quartz-integration` skill). Don't expose
  Amethyst-internal naming.
- **Markdown parity is structural, not visual.** Our parser must produce the
  right blocks + inline tokens for the current corpus (tables, nested quotes,
  fenced code, **footnotes**) before Halilibo is removed. The *rendering* moves
  to the regular note stack and may look different — accepted.
- **Throttling vs freshness.** A note rendered before the queue reaches it
  parses synchronously on the UI thread (same cost as today) — acceptable, but
  verify the queue keeps up under fast scroll.
- **Desktop renderer** must move to the shared block composables too, or keep a
  thin adapter.

## Test strategy

- Port `RichTextParserTest` / URL / math / gallery suites onto the quartz
  `ParsedNote` (parser tests move to quartz; gallery/render tests stay commons).
- New `MarkdownBlockParserTest` (quartz) against the preview corpus + CommonMark
  spec subset we support — asserts **block structure + inline tokens**, not
  rendered pixels (rendering moves to the shared note stack and is allowed to
  drift).
- Memory: macrobenchmark + heap dump on a seeded home feed, P1 vs P2.
- `amy` interop: a `parse` verb dumping the parse as JSON for golden tests
  (optional, aids regression) — drives the quartz parser directly.
