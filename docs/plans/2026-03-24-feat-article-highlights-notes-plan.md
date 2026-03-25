---
title: "feat: Article Highlights & Note-Taking"
type: feat
status: active
date: 2026-03-24
deepened: 2026-03-24
origin: docs/brainstorms/2026-03-24-article-highlights-notes-brainstorm.md
---

# feat: Article Highlights & Note-Taking

## Enhancement Summary

**Deepened on:** 2026-03-24
**Research agents used:** text-selection, richtext-rendering, floating-popup, nip84-highlights

### Key Improvements
1. **Text selection strategy resolved** — Use `LocalTextContextMenu` override (only official API exposing selected text), not clipboard polling
2. **Inline rendering strategy resolved** — Pre-process markdown with special link URI (`highlight://`) as v1; fork-level `==highlight==` syntax as v2
3. **Floating toolbar pattern confirmed** — `Popup` + custom `PopupPositionProvider`, matches existing `ChatPane.kt` pattern
4. **NIP-84 gap found** — `HighlightEvent.create()` only takes `msg`+`signer`, needs tag assembly wrapper for full highlight creation

### Resolved Questions
- **`<mark>` support?** No — richtext library ignores `HtmlInline` nodes. Use pre-processing or fork changes.
- **Clipboard polling reliability?** Moot — use `LocalTextContextMenu` instead (right-click UX, official API)
- **NIP-09 deletion?** Yes — send kind 5 event with `["e", highlightEventId]` + `["k", "9802"]`

## Overview

Text selection-based highlight and annotation system for the Desktop article reader. Users select text in NIP-23 articles, a context menu option or floating toolbar appears, and they create highlights (with optional notes). Highlights render inline as colored markers. Supports private (local) and public (NIP-84) modes. Includes a "My Highlights" aggregation screen.

## Problem Statement

Desktop article reader has no way to mark up, annotate, or take notes on long-form content. Users reading NIP-23 articles can't highlight passages, add personal notes, or publish highlights to their Nostr social graph. This limits the reading experience compared to tools like Kindle, Medium, or Hypothesis.

## Proposed Solution

Three-phase implementation:
1. **Storage + data model** — DesktopHighlightStore (Preferences-based) + highlight data classes
2. **Selection UX + inline rendering** — Text selection interception via context menu, floating toolbar, yellow highlight markers in markdown
3. **My Highlights screen + NIP-84 publishing** — Aggregation view, public/private toggle, relay broadcast

## Technical Approach

### Architecture

```
desktopApp/
├── service/highlights/
│   └── DesktopHighlightStore.kt    # Preferences-based storage (like DraftStore)
├── ui/
│   ├── ArticleReaderScreen.kt      # Modified: selection + inline highlights
│   ├── highlights/
│   │   ├── FloatingHighlightToolbar.kt  # Popup on text selection
│   │   ├── HighlightAnnotationDialog.kt # Note entry dialog
│   │   ├── HighlightPublishAction.kt    # NIP-84 tag assembly + publish
│   │   └── MyHighlightsScreen.kt        # Aggregation screen
│   └── deck/
│       ├── DeckColumnType.kt       # Add MyHighlights
│       ├── DeckColumnContainer.kt  # Route MyHighlights
│       └── SinglePaneLayout.kt     # Add nav item

commons/
├── compose/markdown/
│   └── RenderMarkdown.kt          # Modified: accept highlight ranges, render yellow bg
├── model/highlights/
│   └── HighlightData.kt           # Shared data class
```

### Implementation Phases

#### Phase 1: Storage & Data Model

**Goal:** DesktopHighlightStore + highlight data classes, no UI yet.

**Files:**
- `desktopApp/service/highlights/DesktopHighlightStore.kt` — follows DesktopDraftStore pattern (Jackson + Preferences)
- `commons/model/highlights/HighlightData.kt` — shared data class

**Data model:**
```kotlin
data class HighlightData(
    val id: String,           // UUID
    val text: String,         // selected/highlighted text
    val note: String?,        // optional annotation
    val articleAddressTag: String, // "30023:pubkey:d-tag"
    val articleTitle: String?, // cached for My Highlights display
    val createdAt: Long,      // epoch seconds
    val published: Boolean,   // false = private, true = NIP-84 published
    val eventId: String?,     // NIP-84 event ID if published
)
```

**DesktopHighlightStore API:**
```kotlin
class DesktopHighlightStore(scope: CoroutineScope) {
    private val mapper = jacksonObjectMapper()
    val highlights: StateFlow<Map<String, List<HighlightData>>>  // keyed by articleAddressTag

    suspend fun addHighlight(articleAddressTag: String, text: String, note: String?, articleTitle: String?)
    suspend fun updateNote(highlightId: String, note: String)
    suspend fun removeHighlight(highlightId: String)
    suspend fun markPublished(highlightId: String, eventId: String)
    fun getHighlightsForArticle(addressTag: String): List<HighlightData>
    fun getAllHighlights(): Map<String, List<HighlightData>>
}
```

**Tests:** Unit tests for store CRUD, serialization round-trip.

**Success criteria:**
- [ ] HighlightData serializes/deserializes via Jackson
- [ ] Store persists across app restarts via Preferences
- [ ] CRUD operations work correctly
- [ ] StateFlow emits on changes

### Research Insights — Phase 1

**Storage pattern:** Follow `DesktopDraftStore.kt` exactly:
- `jacksonObjectMapper()` for serialization (line 62)
- Atomic writes with temp files + `Files.move()` (lines 237-254)
- POSIX file permissions for security (lines 261-288)
- Preferences key: `"highlights:${articleAddressTag}"` with JSON array value

**Edge case — Preferences size limit:** `java.util.prefs.Preferences` has a per-value limit of 8192 bytes on some platforms. For articles with many highlights, the JSON array could exceed this. Mitigation: if value exceeds 6KB, spill to file-based storage (same pattern as DraftStore's file storage).

---

#### Phase 2: Selection UX + Inline Rendering

**Goal:** Select text in article → create highlight → see yellow marker.

##### Text Selection Strategy (REVISED)

**Primary: `LocalTextContextMenu` override** — the only official Compose Desktop API that exposes `selectedText`:

```kotlin
@Composable
fun HighlightableContent(
    onHighlight: (String) -> Unit,
    onAnnotate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val defaultMenu = LocalTextContextMenu.current

    CompositionLocalProvider(
        LocalTextContextMenu provides object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit,
            ) {
                ContextMenuDataProvider({
                    val selected = textManager.selectedText
                    if (selected.text.isNotEmpty()) {
                        listOf(
                            ContextMenuItem("Highlight") { onHighlight(selected.text) },
                            ContextMenuItem("Highlight with Note") { onAnnotate(selected.text) },
                        )
                    } else {
                        emptyList()
                    }
                }) {
                    defaultMenu.Area(textManager, state, content = content)
                }
            }
        },
        content = content,
    )
}
```

**UX:** Select text → right-click → "Highlight" / "Highlight with Note" in context menu. Natural desktop UX. No clipboard polling needed.

**Secondary: Keyboard shortcut (Cmd+H)** — reads clipboard after user copies:

```kotlin
Modifier.onPreviewKeyEvent { event ->
    if (event.isMetaPressed && event.key == Key.H && event.type == KeyEventType.KeyDown) {
        val clipText = clipboard.getText()?.text
        if (!clipText.isNullOrBlank()) onHighlight(clipText)
        true
    } else false
}
```

##### Inline Rendering Strategy (REVISED)

**Research finding:** richtext library ignores `HtmlInline` (`<mark>`) and has no `Highlight` format. Three options ranked:

| Approach | Effort | Quality | Recommended |
|----------|--------|---------|-------------|
| Pre-process: wrap in special link `[text](highlight://)` | Low | Hacky but works | v1 |
| Fork: add `==text==` DelimiterProcessor + `Format.Highlight` | Medium | Clean, semantic | v2 |
| Overlay: position colored Box composables | High | Fragile | No |

**v1 approach (ship fast):** Pre-process markdown before parsing:
```kotlin
fun applyHighlights(content: String, highlights: List<HighlightData>): String {
    var result = content
    // Sort by length descending to avoid nested replacement issues
    highlights.sortedByDescending { it.text.length }.forEach { h ->
        val idx = result.indexOf(h.text)
        if (idx >= 0) {
            // Wrap in bold + italic to visually distinguish
            result = result.replaceFirst(h.text, "***${h.text}***")
        }
    }
    return result
}
```

**v2 approach (proper):** Add highlight support to Vitor's richtext fork:
1. Add `AstHighlight` inline node type
2. Add `HighlightDelimiterProcessor` for `==text==` syntax
3. Add `Format.Highlight` with `SpanStyle(background = Color(0xFFFFEB3B))`
4. Pre-process: wrap highlights with `==text==` before parsing

##### Floating Toolbar (for future enhancement beyond context menu)

**Pattern:** `Popup` + custom `PopupPositionProvider` (matches existing `ChatPane.kt:584`):

```kotlin
class MousePositionProvider(private val offset: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset {
        val x = (offset.x - popupContentSize.width / 2)
            .coerceIn(0, windowSize.width - popupContentSize.width)
        val y = (offset.y - popupContentSize.height - 8)
            .coerceIn(0, windowSize.height - popupContentSize.height)
        return IntOffset(x, y)
    }
}
```

Track mouse via `Modifier.pointerInput` + `awaitPointerEventScope` (pattern from `VideoControls.kt:89`). Dismiss on scroll via `LaunchedEffect(scrollState.isScrollInProgress)`.

**Files:**
- `desktopApp/ui/highlights/FloatingHighlightToolbar.kt` — Popup with Highlight/Annotate buttons
- `desktopApp/ui/highlights/HighlightAnnotationDialog.kt` — AlertDialog for note text
- `desktopApp/ui/ArticleReaderScreen.kt` — Add context menu override, highlight state, inline rendering
- `commons/compose/markdown/RenderMarkdown.kt` — Add `highlights: List<HighlightData>` param

**UX flow (revised):**
1. User reads article, selects text by click-dragging
2. Right-click → context menu shows "Highlight" / "Highlight with Note" (via `LocalTextContextMenu`)
3. "Highlight" → saves immediately via DesktopHighlightStore, re-renders with bold/italic marker (v1) or yellow bg (v2)
4. "Highlight with Note" → opens HighlightAnnotationDialog → saves with note
5. Click existing highlight → popup with note / delete / publish toggle

**Success criteria:**
- [ ] Right-click context menu shows "Highlight" option when text is selected
- [ ] Highlight saves and renders as visual marker in article
- [ ] Annotation dialog captures and saves notes
- [ ] Highlights persist across navigation (leave article and return)
- [ ] Best-effort match: highlights survive minor article edits (see brainstorm)
- [ ] Cmd+H keyboard shortcut works as alternative (select, copy, Cmd+H)

---

#### Phase 3: My Highlights Screen + NIP-84 Publishing

**Goal:** Aggregation screen showing all highlights grouped by article. Public/private toggle per highlight.

**Files:**
- `desktopApp/ui/highlights/MyHighlightsScreen.kt`
- `desktopApp/ui/highlights/HighlightPublishAction.kt` — tag assembly + publish
- `desktopApp/ui/deck/DeckColumnType.kt` — Add `object MyHighlights`
- `desktopApp/ui/deck/DeckColumnContainer.kt` — Route MyHighlights
- `desktopApp/ui/deck/SinglePaneLayout.kt` — Add nav item
- `desktopApp/ui/deck/AddColumnDialog.kt` — Add to column options
- `desktopApp/ui/deck/ColumnHeader.kt` — Add icon

##### NIP-84 Publishing (REVISED)

**Gap found:** `HighlightEvent.create()` only takes `msg` + `signer` — doesn't accept source/context/comment tags. Need a wrapper:

```kotlin
object HighlightPublishAction {
    suspend fun publish(
        highlightText: String,
        articleEvent: LongTextNoteEvent,
        note: String?,
        signer: NostrSigner,
    ): HighlightEvent {
        val tags = TagArrayBuilder().apply {
            // Article reference (addressable event)
            add(ATag.assemble(30023, articleEvent.pubKey, articleEvent.dTag()))
            // Article author attribution
            add(PTag.assemble(articleEvent.pubKey, role = "author"))
            // Optional annotation
            note?.let { add(CommentTag.assemble(it)) }
            // Surrounding paragraph as context
            extractContext(articleEvent.content, highlightText)?.let {
                add(ContextTag.assemble(it))
            }
            // Alt text for non-NIP-84 clients
            add(AltTag.assemble("Highlight: $highlightText"))
        }.build()

        return HighlightEvent.create(
            msg = highlightText,
            tags = tags,
            signer = signer,
        )
    }

    /** Extract the paragraph containing the highlighted text */
    fun extractContext(content: String, highlightText: String): String? {
        val paragraphs = content.split("\n\n")
        return paragraphs.find { it.contains(highlightText) }
    }
}
```

##### NIP-09 Deletion for Published Highlights

```kotlin
suspend fun deleteHighlight(eventId: String, signer: NostrSigner): DeletionEvent {
    return DeletionEvent.create(
        deleteEvents = listOf(eventId),
        deleteKinds = listOf(9802),
        reason = "User deleted highlight",
        signer = signer,
    )
}
```

**My Highlights screen layout:**
```
┌─────────────────────────────────┐
│ My Highlights                   │
├─────────────────────────────────┤
│ ▼ "Article Title One"           │
│   "highlighted text..."    🔒   │
│   Note: my annotation      ╳   │
│   Mar 24, 2026                  │
│                                 │
│   "another highlight..."   🌐   │
│   Mar 24, 2026                  │
│                                 │
│ ▼ "Article Title Two"           │
│   "highlighted passage..." 🔒   │
│   Note: thoughts here      ╳   │
└─────────────────────────────────┘
🔒 = private  🌐 = published
Click article title → navigates to article
Click 🔒 → publish to Nostr (NIP-84)
Click ╳ → delete (+ NIP-09 if published)
```

**Success criteria:**
- [ ] My Highlights accessible from sidebar nav
- [ ] Highlights grouped by article with collapsible sections
- [ ] Click article title navigates to article (onNavigateToArticle)
- [ ] Public/private toggle publishes NIP-84 event to relays
- [ ] Delete removes from local store + sends NIP-09 deletion for published
- [ ] Empty state when no highlights exist

## Acceptance Criteria

- [ ] Right-click selected text in article → "Highlight" in context menu
- [ ] Click "Highlight" → text marked visually, saved locally
- [ ] Click "Highlight with Note" → note dialog, then saved with annotation
- [ ] Cmd+H keyboard shortcut creates highlight from clipboard
- [ ] Highlights persist across app restarts
- [ ] Highlights survive article content updates (best-effort string match)
- [ ] "My Highlights" screen shows all highlights grouped by article
- [ ] Can toggle highlight public/private (publishes NIP-84 event)
- [ ] Can delete highlights (+ NIP-09 for published)
- [ ] Zoom (Cmd+/Cmd-) doesn't break highlight rendering
- [ ] Works in both single-pane and deck layout modes

## Dependencies & Risks

| Risk | Impact | Mitigation | Status |
|------|--------|------------|--------|
| `SelectionContainer` doesn't expose selection state | High | **Resolved:** Use `LocalTextContextMenu` override — official API, accesses `selectedText` directly | Mitigated |
| richtext library doesn't support `<mark>` or highlight formatting | Medium | **Resolved:** v1 uses bold/italic pre-processing; v2 adds `Format.Highlight` to fork | Mitigated |
| `LocalTextContextMenu.TextManager.selectedText` doesn't work across multiple `Text()` children | Medium | Test during Phase 2; fallback to clipboard-based Cmd+H shortcut | Open |
| `HighlightEvent.create()` doesn't accept custom tags | Low | **Resolved:** Create `HighlightPublishAction` wrapper with `TagArrayBuilder` | Mitigated |
| Preferences 8KB per-value limit | Low | Monitor; spill to file storage if needed | Open |

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-03-24-article-highlights-notes-brainstorm.md](docs/brainstorms/2026-03-24-article-highlights-notes-brainstorm.md)
  - Key decisions: private+public scope, select+popup UX, Preferences storage, own highlights only, best-effort persistence, My Highlights screen

### Internal References

- HighlightEvent protocol: `quartz/nip84Highlights/HighlightEvent.kt:137-141`
- DraftStore pattern: `desktopApp/service/drafts/DesktopDraftStore.kt`
- Event publishing: `desktopApp/ui/ArticleEditorScreen.kt:161-187`
- SelectionContainer: `desktopApp/ui/ArticleEditorScreen.kt:304`
- Context menu override: `LocalTextContextMenu` (Compose Desktop API)
- Popup pattern: `desktopApp/ui/chats/ChatPane.kt:584`
- Mouse tracking: `desktopApp/ui/media/VideoControls.kt:89`
- Android highlight rendering: `amethyst/ui/note/types/Highlight.kt:179-198`

### External References

- [Compose Desktop context menus](https://kotlinlang.org/docs/multiplatform/compose-desktop-context-menus.html)
- [NIP-84 spec (Highlights)](https://github.com/nostr-protocol/nips/blob/master/84.md)
- [NIP-09 spec (Event Deletion)](https://github.com/nostr-protocol/nips/blob/master/09.md)
- [commonmark-java DelimiterProcessor](https://github.com/commonmark/commonmark-java)
