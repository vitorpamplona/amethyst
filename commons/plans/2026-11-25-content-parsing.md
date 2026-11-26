---
title: "Content parsing: one document model, a parser per format, chosen by kind"
type: refactor
status: in progress
date: 2026-11-25
owner: commons
consumers: quartz, commons, commonsUI, cli, new djot-kmp, the compose-richtext fork
---

# Content parsing: one document model, a parser per format, chosen by kind

## Goal

Parse every event's content once, off the UI thread, with the parser its NIP names, into one
document model that every format can extend. Render that model with one renderer registry.

This also takes the last JVM-only parser out of the shared UI, so markdown, wiki pages, Git
screens and cordn chats work on iOS ([2026-10-09-ios-app.md](2026-10-09-ios-app.md), A4) and on
the web.

## Where we are (measured 2026-11-25 on `main` @ `a9e6a9d8`)

**What works:**
- `RichTextParser` (`commons/richtext`, 18 files, 3.5k lines) parses Nostr text: mentions,
  NIP-27 references, hashtags, custom emoji, media, invoices, Cashu tokens. It is shared Kotlin
  with no Compose and no JVM-only code. It is well tested and benchmarked
  (`RichTextParserBenchmark`).
- `CachedRichTextParser` keeps 500 parses in one global LRU, and the feed prefetcher
  (`PrefetchFeedMedia`) parses ahead off the UI thread, so the screen usually gets a cache hit.
- `amy` reuses the same parse through `commons/rendering/BodySpans`.

**What does not:**
1. **The format is guessed from the text.**
   - `RichTextViewer` runs `CachedRichTextParser.isMarkdown()` on every piece of content it is
     given, whatever the kind. About 80 call sites in `commonsUI` go through it, through
     `TranslatableRichTextViewer` and `ExpandableRichTextViewer`.
   - NIP-23 articles are no exception: their body reaches the screen through `RenderTextEvent`
     like a note's.
   - The heuristic still misfires: `cat foo | grep bar` is markdown to it.
2. **Two document models, two renderers.**
   - Nostr text parses into `RichTextViewerState`; markdown into the compose-richtext fork's
     `AstNode`.
   - Hashtags and `nostr:` links are implemented twice: in `RichTextParser`, and in the fork's
     commonmark extensions (`HashtagExtension`, `NostrUriExtension`). Bare URLs are detected in
     `RichTextParser` and again in `BareUrlExtension`.
3. **Markdown is parsed in composition with no shared cache.** `RenderContentAsMarkdown` calls
   `CommonmarkAstNodeParser(...).parse(content)` inside `remember(content)`. A long article
   re-parses whenever it leaves and re-enters composition.
4. **The cache is keyed by content and UI context, not by event.** The key includes
   `callbackUri`, which depends on where the note is shown. One 500-entry cache serves every
   feed, and markdown is not in it.
5. **The markdown parser is JVM-only.** It is the fork's `richtext-commonmark` (commonmark-java)
   plus our `BareUrlExtension` (commonmark-java and the JVM autolink library). This is the iOS
   blocker.

**Formats the NIPs name** (checked in `nostr-protocol/nips` on 2026-11-25):

| Format | Kinds | Spec says | Amethyst today |
|---|---|---|---|
| Plain Nostr text | 1 (NIP-10), 1111 (NIP-22) | NIP-10: "Markup languages such as markdown and HTML SHOULD NOT be used." NIP-22: plain text, "no HTML, Markdown, or other formatting" | Heuristic: markdown when it looks like markdown |
| Markdown | 30023 (NIP-23), 1618 and 1621 (NIP-34 pull requests and issues), 30402/30403 (NIP-99), 30063 release notes (NIP-51), 54 (NIP-F4) | Markdown; NIP-23: "MUST NOT support adding HTML" | Heuristic |
| Djot | 30818 (NIP-54) | Djot, plus `nostr:` link targets, plus wikilinks: an undefined reference link points to the article with that normalized `d` tag | **Not supported.** No Djot code anywhere; bodies go through the heuristic |
| AsciiDoc | 30040/30041 (NKBIP-01 publications) | AsciiDoc | `CachedAsciiDocToMarkdown` converts it to markdown, then the markdown path |

**The compose-richtext fork** (`vitorpamplona/compose-richtext` @ `4d3b3dd`, from JitPack) is
upstream `halilozercan/compose-richtext` `main` (2026-06-07) plus 45 of our commits (about 700
lines in 23 files). Upstream: 8 commits in the last 12 months, at `1.0.0-alpha05`, Android and
JVM targets only, even for its pure-Compose modules.

| Fork module | Lines | Role | In this plan |
|---|---|---|---|
| `richtext-ui` | 2,320 | Layout primitives: `Heading`, `FormattedList`, `Table`, `BlockQuote`, `CodeBlock`, `HorizontalRule`, `InfoPanel`, `RichTextString` with inline content, `RichTextStyle` | **Kept** as the renderer's building blocks |
| `richtext-ui-material3` | 80 | Material 3 theming of the above | Kept |
| `richtext-markdown` | 1,118 | Its markdown AST and composer (`AstNode`, `BasicMarkdown`) | Dropped: our model replaces it |
| `richtext-commonmark` | 658 | commonmark-java parser, hashtag and `nostr:` extensions | Dropped: our parsers replace it |

## Decisions

1. **The NIP names the format, not a guess.** Each kind maps to a content format. The mapping
   lives in quartz, next to the event classes, because every client needs it.
2. **One document model, open to extension.**
   - `Block` and `Inline` are open interfaces with a shared core: paragraph, heading, list,
     quote, code block, table, thematic break, media; text, emphasis, strong, strikethrough, code,
     link, line break, image.
   - The Nostr inlines are core too, shared by every format: mention, event and address
     references, hashtag, custom emoji, invoice, Cashu token, URL, math.
   - Each format adds nodes for what only it has: Djot's definition lists, footnotes,
     super/subscript and `Div`/`Span` with attributes; AsciiDoc's admonitions and callouts;
     GitHub markdown's task items.
   - A rule of one NIP is a parser option or post-pass, not a new output type: NIP-54
     wikilinks, NIP-23's "no HTML".
   - Content that is not prose (a spreadsheet NIP, chess games, polls, calendars) is not part of
     this model. Each stays its own kind, with its own parser and its own composable, as today.
3. **Parse once, off the UI thread, cached by event.**
   - Parse on first request in the background; the prefetcher keeps doing it ahead of the feed.
   - Cache by event id (and the edit's id for an edited note), in a bounded LRU.
   - Parse only from the event's content and tags.
   - Do not parse at ingest: most cached notes are never shown.
   - Translations stay keyed by text, since a translated body is not an event.
4. **One renderer registry in `commonsUI`.**
   - A renderer per node type, keyed by class. Core renderers draw on the fork's `richtext-ui`
     primitives for headings, lists, tables, quotes and code blocks.
   - Each format registers renderers for its own nodes.
   - An unregistered node renders its children, or its source text. A parser can add a node
     before any UI draws it.
   - Djot attributes are the in-document extension point: a `{.spreadsheet}` block arrives as a
     `Div` with that class, and a renderer registered for the class can draw it.
5. **Where each piece lives:**

   | Piece | Home | Why |
   |---|---|---|
   | Format per kind; NIP-54 wikilink target normalization | quartz | NIP interpretation every client needs; no new dependency |
   | General Djot parser, no Nostr | `vitorpamplona/djot-kmp`, via Magpie | No Kotlin or JVM Djot parser exists (the Djot README lists JavaScript, Lua, Prolog, PHP, Rust, Go and Haskell; Maven Central has none). Spec-driven, with its own conformance suite, like crypto-kmp and kiko-kmp |
   | Markdown parser | JetBrains `org.jetbrains:markdown` 0.7.18, used directly | Apache-2.0 (checked in its POM); publishes iOS, JVM, wasm, JS and native targets |
   | Document model, format mappings, Nostr inline pass, parser registry, event-keyed cache | `commons/content/` | Amethyst's own model; `amy` already depends on `commons` |
   | Renderer registry and renderers | `commonsUI` | UI |

   `commons/content/` depends on no `Account`, `LocalCache` or UI, so it can move to its own
   repo by moving files if another Kotlin client ever wants it.
6. **The fork shrinks to `richtext-ui` and heads upstream.** Once `richtext-markdown` and
   `richtext-commonmark` are unused, the fork's diff is the media and link hooks, the
   `RichTextString` additions and the iOS targets: about 100 lines. Offer those upstream. If
   they land, depend on `com.halilibo.compose-richtext` from Maven Central and drop the fork and
   JitPack.
7. **Kind 1 keeps rendering markdown (maintainer, 2026-11-25).** Notes already contain
   markdown, and Amethyst renders it, even though NIP-10 says markup SHOULD NOT be used. So kind
   1's format is "Nostr text, or markdown when it looks like markdown": today's `isMarkdown()`
   heuristic becomes a documented rule of kind 1's parser, and stops running for every other
   kind. Comments (kind 1111, NIP-22) and the kinds whose NIP names a format use that format
   only.
8. **Kind-1 text keeps its own fast layout for now.** Plain Nostr text renders through
   `RichTextViewer`'s word layout, which is tuned for feed scrolling. In the model it is a
   paragraph-only document. Whether it moves onto `richtext-ui`'s `RichTextString` is decided by
   the scroll benchmarks, not up front.
9. **`vitorpamplona/djot-kmp`** holds the Djot parser, MIT-licensed, published through Magpie
   like the other libraries (repo created 2026-11-25, empty).

## Steps

| # | Step | Where | Weeks |
|---|---|---|---|
| 1 | `ContentFormat` per kind for the kinds in the table above; NIP-54 wikilink target normalization. Release. | quartz | 0.5 |
| 2 | Registry and event-keyed, off-thread cache, still with today's two renderers. `RichTextViewer` stops guessing for kinds with a declared format; kind 1 keeps the heuristic as its own rule (decision 7). Check what `RichTextParser` uses `callbackUri` for and move it to render time if it can. | commons, commonsUI | 1–1.5 |
| 3 | Document model; markdown into it through JetBrains markdown; the Nostr inline pass. Parity harness (below). | commons | 2 |
| 4 | Renderer registry on `richtext-ui`. Delete `RenderContentAsMarkdown`'s commonmark path, `BareUrlExtension`, and the `richtext-markdown`, `richtext-commonmark`, commonmark and autolink dependencies. Move the markdown files from `commonsUI/jvmAndroid` to `commonMain`. Markdown works on iOS from here. | commonsUI | 2 |
| 5 | Fork: drop the two modules, add `iosArm64`/`iosSimulatorArm64` to `richtext-ui` and `-material3` with an iOS `CodeBlock`, publish through Magpie instead of JitPack. Open the upstream PR. | fork | 0.5 |
| 6 | `djot-kmp`: port the reference parser (djot.js) with the Djot conformance tests. Then the Djot mapping and NIP-54's `nostr:` links and wikilinks in `commons/content/`. Wiki pages render to spec on every platform. | new repo, commons | 2–3 |
| 7 | AsciiDoc into the model, replacing the markdown round trip. | commons | 1 |

Steps 1–5 (about 6–7 weeks) are what iOS needs. They replace the markdown part of the iOS plan's
A4. The iOS slice (B3) does not wait for them: ordinary notes already use the shared parser.
Steps 6 and 7 can follow in either order.

## Status (2026-11-26)

All seven steps are in, on branches stacked in this order: `claude/djot-wiki` (Djot for wiki pages,
PR vitorpamplona/amethyst#4906), `claude/content-format-rendering` (step 2), `claude/rich-text-model`
(steps 3, 4, 6 and 7); quartz `claude/content-format` (step 1); compose-richtext `claude/ios-targets`
(step 5).

| # | What landed | Where it departs from the plan |
|---|---|---|
| 1 | `ContentFormat`, `FormattedContentEvent` on the 16 classes whose NIP names a format, `ContentFormats.KINDS` checked against every kind by `ContentFormatsTest`. | Needs a quartz release; Amethyst builds with `-PquartzDir` until then. |
| 2 | `TextRendering`: a kind whose NIP says Markdown always renders as markdown; everything else keeps the guess. Passed where those kinds' bodies render. | Comments (1111), DMs (14) and public messages (24) keep the guess too (maintainer, 2026-11-25), not only kind 1. `callbackUri` stays in the parse: it is the note's own URI, carried by media items. |
| 3 | `commons/content`: `Document`, core and format nodes, `NostrInlinePass` (RichTextParser over the text a format leaves plain), `MarkdownToDocument` on JetBrains markdown 0.7.18. | Parity, see Verification. Scheme-less file names (`photo.jpg`) are not media in documents. |
| 4 | `commonsUI/content`: `RenderDocument` on `richtext-ui` in `commonMain` (blocks, code chips, Nostr segments through the platform's `RichTextSegmentRenderer`, section anchors), `DocumentRenderers` for formats' own blocks, `RenderPlainMarkdown` for text that is not Nostr's (the coding-agent chat, a string resource). `CachedDocuments` parses once per text and the feed prefetcher warms it. The commonmark path, `BareUrlExtension`, `InlineCodeChips`, `MarkdownMediaRenderer`, `richtext-markdown`, `richtext-commonmark`, commonmark and autolink are gone. | The cache is keyed by text (and tags, author, callback URI), not by event id: a translated or cropped body is a different document. |
| 5 | The fork adds `iosArm64`/`iosSimulatorArm64` to `richtext-ui` and `-material3` and moves to Kotlin 2.4.21, CMP 1.12.2, AGP 9.5.0. | Stays on JitPack (maintainer, 2026-11-26). JitPack drops the classifier from the iOS metadata variant's file name in the Gradle module it serves; a component metadata rule in `settings.gradle.kts` points it at the real `-metadata.jar`. The two parser modules stay in the fork, unused by Amethyst. |
| 6 | `DjotToDocument`: everything Djot has (highlight, insert, super/subscript, spans and divs with classes, definition lists, footnotes), NIP-54 wikilinks and `nostr:` targets. `DjotToMarkdown` is gone. | |
| 7 | `AsciiDocToDocument`: sections, lists, checklists, description lists, admonitions as callouts, listing/literal/source, quote/sidebar/example/open blocks, tables, images, footnotes, xrefs, wikilinks, link macros without their attributes (`[Rupelian,role=orange]` showed the role). Publications and legacy wiki articles use it; `CachedAsciiDocToMarkdown` is gone. | quartz's `AsciiDocToMarkdown` is still there for other clients. |

A translated or copied article is its own source now (Djot, AsciiDoc), not the markdown it used to
convert to.

Finishing touches found by running the app (2026-11-26):

- **Callouts** (GitHub `> [!TIP]`, AsciiDoc `NOTE:` and `[WARNING]` blocks, Djot `::: warning`
  divs) draw as a tinted panel with an accent bar and the kind's name, in the theme's colors.
  richtext-ui's `InfoPanel` was a light pastel box that glared in dark mode.
- **An article's first heading is dropped when it repeats the title tag**, which the header
  already shows: wiki articles through `Document.withoutLeadingTitle` (a legacy AsciiDoc article's
  `= Title` line is the usual case), NIP-23 articles through `MarkdownTitle.withoutLeadingTitle` on
  a first `#`/`##` line.
- **A note's tab title reads its words**, not its markup (`**bold**` reads "bold").
- **Code blocks highlight 20-odd programming languages** (`ProgramHighlighter`: Kotlin, Java,
  Swift, Rust, Go, C/C++, C#, JS/TS, Python, Ruby, shells, SQL, Lua, TOML, PHP, Dart, Scala):
  keywords, types, calls, strings, numbers and comments. JSON and YAML keep `CodeHighlighter`.
  `MarkdownChunks.split`, which existed only to pull code out for the old renderer, is gone.
- **A custom emoji whose image fails** leaves a faint tile instead of a gap.
- **Audit, 2026-11-26** (code review of the branch), fixed:
  - Code blocks in quotes and list items kept the container's `> ` and indentation.
  - Deep nesting overflowed the stack: each mapper now caps blocks and inlines together at
    `Document.MAX_DEPTH` (32) and keeps only the words past it. Markdown's input is also capped at
    32 quote markers per line, since JetBrains' parser ran out of memory on 20,000.
  - AsciiDoc was quadratic on blank runs in lists, on unclosed marks and per character (a
    substring per macro check). Closing marks and brackets are now found once each.
  - `NostrInlinePass` rebuilt the `imeta` and emoji maps for every run of text: `TagContext`
    builds them once per event.
  - The prefetcher, copy and translation now see the same text the card draws (`displayedNoteText`).
  - Ordered lists that start at 0, a cache hit that only compared the tags' hash, and chip spacing
    in the chat with chips off.
  `HostileInputTest` runs every format against deep quotes, lists and emphasis, unclosed marks
  and long blank runs.
- **Table columns keep their alignment** (`:-:` centered, `--:` right), which the old renderer
  dropped.
- **Markdown footnotes** (GitHub's `[^label]` and `[^label]: text`), which both the old renderer
  and JetBrains' parser left as literal text, found by the before/after captures.
  `MarkdownFootnotes` takes the definitions out of the source before it is parsed (not inside
  code fences), with their indented and lazy continuation lines. Each note is then parsed once,
  numbered in the order it is first called, and drawn in the same `Footnotes` block as Djot's
  and AsciiDoc's. A call with no note stays text, and a note nobody calls is not drawn.

## Verification

- **Parity harness (step 3):** fetch a few hundred real kind-30023 articles with `amy`, render
  each through the old and the new path, and report structural differences. Each difference is
  either a bug or a documented behavior change.
  **Run 2026-11-26** on 400 recent articles: 395 with the same structure (headings, paragraphs,
  lists and items, quotes, code, tables, rules, images, links, emphasis). The five left: three
  plain emails (a mail link before, an email segment that renders as one now), a single-tilde
  strikethrough (`~x~`, which JetBrains' parser does not read; it is subscript in Djot anyway) and
  an escaped star before a closing `**`. The run found and fixed Windows line endings,
  angle-bracketed link destinations (linked images were dropped), `<email>` autolinks, URLs and
  hashtags detected again inside a link's own text, and file names read as images. The harness
  (`MarkdownParityTest`) left with the old parser.
- **Model tests** in `commonTest`, so CI runs them on the JVM and on the iOS simulator.
- **Djot:** the official conformance suite in `djot-kmp`.
- **Parse time, 2026-11-26** (JVM, warm, `commons:jvmTest`): a 28 KB markdown article 9.6 ms, a
  15 KB Djot article 2.6 ms, 15 KB of AsciiDoc 2.2 ms, a 70-character note 24 µs. The feed
  prefetcher parses on a background thread, so a card's first frame finds the document cached; a
  long article opened from a link parses once in composition.
- **Cache memory, 2026-11-26:** 60 real kind-30023 articles parsed five times over and kept: 15 KB
  per document, about 3 bytes per character of source. `CachedDocuments` keeps 100, so about 1.5 MB
  for typical articles and 4 to 5 MB if every one is long. It stays at 100; `trimToSize(4)` still
  runs on memory pressure.
- **Run 2026-11-26** in the headless desktop app against a local `amy serve` relay: a markdown
  article (emphasis, code chips, hashtag, task list, ordered list, callout, quote, aligned table,
  highlighted code, a broken image's error card), a Djot wiki (highlight, insert, sub/superscript,
  wikilink, definition list, callout div, captioned table, footnotes), a legacy AsciiDoc wiki
  (admonitions, checklist, titled table, footnote, `kbd:`), a NIP-23 article that opens with its
  title, and a kind-1 note. No exceptions in the log.
- **Benchmarks, before and after:** `RichTextParserBenchmark`; the desktop scroll benchmark
  (`-Damethyst.scrollBench`); parse time of long articles on an Android device; memory of the
  event-keyed cache at its chosen size.
- **Run it:** articles, wiki pages, Git issues and a kind-1 feed in the headless desktop app at
  each step that changes what users see (`tools/desktop-run`).

## Risks

- **JetBrains markdown parses some edge cases differently** from commonmark-java (HTML blocks,
  nested emphasis). The parity harness measures this before step 4 ships.
- **Parse speed and memory.** Measured in the benchmarks above; the cache size is a number, not a
  guess.
- **The Djot port** is a new parser. The conformance suite bounds it.
- **Upstream may not take the iOS targets.** Then the fork stays, at about 100 lines with nothing
  Nostr-specific in it.
- **Users may see articles change** where the old path rendered differently. The parity report
  lists every such case for review before the switch.

## Open

1. **Kind-1 layout:** whether plain Nostr text moves onto `RichTextString` (decision 8).
