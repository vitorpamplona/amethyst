# Rich Text Parsing

Amethyst converts raw event content (plain text with URLs, mentions, hashtags, media links, nostr references, markdown) into structured segments that Compose can render. Everything lives under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/richtext/`.

## Files

- **`RichTextParser.kt`** — the main entry point. A `class RichTextParser` that takes a note's content, the URL preview cache, and NIP-92 `imeta` tags and returns a `RichTextViewState`.
- **`RichTextParserSegments.kt`** — segment data classes (hashtag, url, mention, invoice, etc.) that the parser emits.
- **`Patterns.kt`** — the regex bank. Single source of truth for URL, hashtag, mention, email, invoice, cashu, nostr-URI patterns. Prefer adding a case here to writing a one-off regex at a call site.
- **`UrlParser.kt`** — URL extraction + validation; used to pull URLs out of free-form text before the parser classifies them.
- **`GalleryParser.kt`** — builds `MediaGallery` groupings from consecutive media URLs in a note.
- **`MediaContentModels.kt`** — the rendering contracts:
  - `MediaUrlImage`, `MediaUrlVideo` — plain HTTP(S) media with optional NIP-92 metadata.
  - `EncryptedMediaUrlImage`, `EncryptedMediaUrlVideo` — for encrypted/blossom-gated media.
  - `MediaLocalImage`, `MediaLocalVideo` — for drafts / not-yet-uploaded media.
- **`Base64Image.kt`** — inline base64 data URI support.
- **`ExpandableTextCutOffCalculator.kt`** — decides where to truncate long content for "Show more" fold points.

## How a Note Becomes Rendered UI

1. Raw `content: String` arrives (from an `Event`).
2. `RichTextParser` scans with patterns, extracts URLs, nostr IDs, hashtags, mentions, invoices, cashu tokens.
3. URLs are classified against `imeta` tags (NIP-92) so media gets correct dimensions, mime type, blurhash.
4. `GalleryParser` groups adjacent media into a single `MediaGallery` segment.
5. The composable layer (elsewhere in `commons/compose/` and amethyst/desktop UI) walks the segment list and renders each with the appropriate composable (`RenderMarkdown`, `NoteQuoteBody`, `ClickableUrl`, etc.).

## Typical Reuse

```kotlin
// inside a composable
val state = remember(note, imetaTags) {
    CachedRichTextParser.parseReturningNullable(content, imetaTags, callbackUri)
}
state?.paragraphs?.forEach { paragraph ->
    paragraph.words.forEach { segment ->
        when (segment) {
            is UrlSegment       -> ClickableUrl(segment)
            is HashtagSegment   -> HashtagChip(segment)
            is NostrRefSegment  -> NoteCompose(segment.entity)
            is ImageSegment     -> ZoomableMedia(segment.media)
            // ...
        }
    }
}
```

On Android there's `amethyst/.../service/CachedRichTextParser.kt` which caches parser output per content — re-parsing the same note on every recomposition is expensive, so **always parse behind a cache**.

## NIP-92 imeta Enrichment

`imeta` tags attached to an event carry structured metadata for each media URL: `url`, `m` (mime), `dim`, `blurhash`, `x` (sha256), `size`. `RichTextParser` maps these into `MediaUrlImage` / `MediaUrlVideo` so the renderer can reserve correct aspect ratio and show a blurhash placeholder before the image loads. Reference: `nip-catalog.md`.

## Gotchas

- **Don't parse on every recomposition.** Use `CachedRichTextParser` (Android) or `remember(content, imeta) { … }` for commonMain.
- **Regexes live in `Patterns.kt`.** If you're writing a new regex for URLs/mentions/hashtags in a UI file, move it to `Patterns.kt` instead.
- **Segments are `@Immutable`** data classes — safe to pass to Compose without triggering recomposition spam.
- **Encrypted media is a separate class** (`EncryptedMediaUrl*`). If you handle `MediaUrlImage` but not its encrypted sibling, blossom/NIP-17 gated media silently falls through.
- **`GalleryParser` groups** across whitespace-only-lines between URLs. Changing its grouping rules breaks layout in many note screens.

## Related

- `nostr-expert/references/nip-catalog.md` — NIP-92 (imeta) spec location
- `compose-expert/references/shared-composables-catalog.md` — which composables consume which segment types
