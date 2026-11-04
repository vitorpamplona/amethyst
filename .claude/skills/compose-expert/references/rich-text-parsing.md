# Rich Text Parsing

Event content (text with URLs, mentions, hashtags, media links, nostr references, invoices,
markdown) is parsed headlessly into segments, then rendered by shared composables.

## Parser (headless, `commons`)

All under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/richtext/`:

- **`RichTextParser.kt`**: `class RichTextParser`, `parseText(content, tags, callbackUri, authorPubKey)`
  returns a `RichTextViewerState`.
- **`CachedRichTextParser.kt`**: `object CachedRichTextParser`, an LRU in front of the parser:
  `parseText(...)` (parse or hit), `cachedText(...)` (hit only, may return null),
  `isMarkdown(content)`, `trimToSize(maxItems)`. **Always parse through this.**
- **`RichTextParserSegments.kt`**: `RichTextViewerState` (`urlSet`, `mediaForPager`, `mediaList`,
  `customEmoji`, `paragraphs`, `tags`, all `Immutable*`), `ParagraphState` /
  `ImageGalleryParagraph`, and the `Segment` subclasses (`ImageSegment`, `VideoSegment`,
  `LinkSegment`, `HashTagSegment`, `BechSegment`, `HashIndexUserSegment`, `InvoiceSegment`,
  `CashuSegment`, `RelayUrlSegment`, `MathSegment`, `RegularTextSegment`, ...).
- **`Patterns.kt`**: the regex bank (URL, hashtag, mention, email, invoice, cashu, nostr URI).
  Add a case here instead of writing a regex at a call site.
- **`UrlParser.kt`**: `UrlParser` / `Urls`, URL extraction before classification.
- **`GalleryParser.kt`**: groups consecutive media into an `ImageGalleryParagraph`.
- **`MediaContentModels.kt`**: `MediaUrlImage`, `MediaUrlVideo`, `MediaUrlPdf` and their
  encrypted siblings `EncryptedMediaUrlImage` / `EncryptedMediaUrlVideo`.
  **`MediaLocalContent.kt`**: `MediaLocalImage` / `MediaLocalVideo` for not-yet-uploaded drafts.
- **`ExpandableTextCutOffCalculator.kt`**: where to fold long content for "Show more".

## Renderer (`commonsUI`)

- `commonsUI/.../commons/ui/components/RichTextViewer.kt`: `RichTextViewer(content, ...)`, the
  entry point screens call. It checks `CachedRichTextParser.isMarkdown`, parses through
  `CachedRichTextParser.parseText` inside `remember(content, tags)`, and hands the state to the
  core below.
- `commonsUI/.../commons/ui/richtext/RichTextViewer.kt`: the shared core that takes a parsed
  `RichTextViewerState`: paragraphs, RTL, word flow, plain text, custom emoji, hashtags.
- `commonsUI/.../commons/ui/richtext/RichTextSegmentRenderer.kt`: the seam for segments whose
  look and action differ per platform (media, gallery, equation, nostr entity, quoted event,
  user mention, payment, ...), installed through `LocalRichTextSegmentRenderer`;
  `DefaultRichTextSegmentRenderer.kt` is the default. See the `README.md` in that folder.

To render a new segment type: add the `Segment` subclass and its detection in the parser, then
either draw it in the shared core (if it looks the same everywhere) or add a method to
`RichTextSegmentRenderer`.

## NIP-92 imeta enrichment

`imeta` tags carry per-URL metadata (`url`, `m`, `dim`, `blurhash`, `x`, `size`). The parser
maps them onto `MediaUrlImage` / `MediaUrlVideo` so the renderer can reserve the aspect ratio
and show a blurhash before the image loads. The data comes from the event's tags, not the URL.

## Gotchas

- **Never parse per recomposition.** Go through `CachedRichTextParser`; it is keyed by content,
  tags, callback URI and author.
- **Encrypted media is a subclass.** `EncryptedMediaUrlImage` extends `MediaUrlImage` (and the
  video likewise), so an `is MediaUrlImage` branch also catches encrypted NIP-17 / Blossom media.
  Test for the encrypted class first wherever it needs decryption.
- **Changing `GalleryParser` grouping** changes the layout of every note with several images.
