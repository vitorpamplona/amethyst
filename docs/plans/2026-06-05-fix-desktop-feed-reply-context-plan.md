---
title: Desktop Feed — Reply Context Rendering
type: fix
status: active
date: 2026-06-05
origin: docs/brainstorms/2026-06-05-desktop-feed-reply-context-brainstorm.md
---

# Desktop Feed — Reply Context Rendering

## Enhancement Summary

**Deepened on:** 2026-06-05

Source-verified facts (carried into the final design):

- **NIP-10 / NIP-22 unified** — `BaseThreadedEvent.replyingToAddressOrEvent()` is overridden by `CommentEvent` (kind 1111). One polymorphic call covers both, no caller-side branching.
- **Return type** — `replyingToAddressOrEvent(): String?` is a flat string. Disambiguate event-id vs address by `raw.contains(":")`.
- **`QuotedNoteEmbed` is reusable as-is** — signature `(noteId: String, localCache, onMentionClick?, onNavigateToThread?)`. Already handles cache lookup, loading-state, and async parent arrival.
- **Addressable parents** — v1 renders label-only. `QuotedNoteEmbed` doesn't accept `Address`; embed is follow-up.
- **Existing styling** — `MaterialTheme.colorScheme.outlineVariant` (1.dp border, used in `ThreadScreen.kt:283`). No new theme keys.
- **FlowRow** — available in `commons/commonMain/` (live precedent: `commons/.../nip23LongContent/ui/editor/MetadataPanel.kt:26`).
- **Strings location** — `commons/src/commonMain/composeResources/values/strings.xml`. Package `com.vitorpamplona.amethyst.commons.resources`. Domain-grouped, NOT alphabetized — add new `<!-- Notes & Replies -->` section.
- **Reposted-reply** — FeedScreen.kt:131 extracts inner note via `note.replyTo?.lastOrNull()` then calls `NoteCard` on it. Phase 3 wiring passes a fresh `replyContext` for the inner note — reply context engages naturally on the reposted reply.
- **Cache parent observation pattern** — `note.flow().metadata.stateFlow.collectAsState()` drives recomposition on parent arrival.

## Review Findings Applied (2026-06-05)

After deepen-plan, three parallel reviewers (architecture-strategist,
code-simplicity-reviewer, pattern-recognition-specialist) flagged structural
issues. Applied revisions:

1. **`ReplyContext` moves to `commons/commonMain/`** (architect's blocker).
   Pure protocol→display data with no platform deps; if left in desktopApp,
   Android re-implements when it extracts.
2. **Drop the `withReplyContext: Boolean` flag** (simplicity). The flag
   existed only to break recursion. Instead: keep `NoteDisplayData` strictly
   display-only and pass `replyContext: ReplyContext?` as a *separate*
   parameter to `NoteCard`. `QuotedNoteEmbed` calls `NoteCard` without a
   `replyContext` (default null) — recursion impossible by construction. No
   `toNoteDisplayData` callsite audit, no behavior surprise for bookmarks /
   search / quote-mentions.
3. **Drop `ReplyRenderType` extraction** (simplicity). Desktop only uses
   FULL; Android's LINE/NONE branches stay in `amethyst/`. Enum stays where
   it is.
4. **Simplify `parentAuthorHint()`** — drop the brittle last-`p`-tag
   heuristic. Use only:
   - `CommentEvent.replyAuthor()` for NIP-22, OR
   - Read `parent.pubKey` from cache once parent loads.
   If neither resolves: omit the label (don't show "Replying to @unknown").
   Eventual consistency wins over speculative guesses.
5. **`strings.xml` section header** — add new `<!-- Notes & Replies -->`
   group; the file is domain-grouped, not alphabetized.
6. **Skip the commons `ReplyToLabelTest` smoke test** — testing a
   two-`Text` composable is theater. Real coverage is the desktop converter
   test.

**Gentle deviation from brainstorm:** the brainstorm option chosen was
phrased "add to NoteDisplayData". On sharper analysis, attaching the field
forces a depth-cap mechanism (the flag). Decoupling — pass `ReplyContext`
alongside `NoteDisplayData` rather than embedded inside it — preserves the
brainstorm's core intent ("FeedScreen pre-computes once per item, NoteCard
is a pure render fn") while eliminating the flag. Flagged for explicit
user awareness.

## Overview

Desktop home feed currently renders reply notes as standalone top-level posts —
no indication they're replies, no parent context. Users lose conversational
thread when scrolling the feed. Fix: detect NIP-10 / NIP-22 replies during
event→display-data conversion, then render an embedded parent card plus a
"Replying to @displayName" label above the reply body — matching Android's
`ReplyRenderType.FULL` mode (but capped at 1 quote level for the deck-column
layout).

Per brainstorm decisions (see brainstorm:
`docs/brainstorms/2026-06-05-desktop-feed-reply-context-brainstorm.md`):

- Render mode: **FULL** (label + embedded parent card)
- Extraction: move shared composables into `commons/commonMain/` now
- Scope: NIP-10 (kind 1) **and** NIP-22 (kind 1111)
- Quote depth: **1 level** (no recursive grandparent)
- Data flow: pre-compute `ReplyContext` in `Event.toNoteDisplayData()`

## Problem Statement

`desktopApp/.../FeedScreen.kt` currently special-cases reposts only (lines
128–197). Replies fall through and are rendered by `NoteCard` (NoteCard.kt:95+)
as if they were thread-root posts. Three concrete user impacts:

1. **Lost thread context.** Reply text often only makes sense relative to the
   parent ("yes!", "no this is wrong because…"). Without the parent the
   feed reads as decontextualized noise.
2. **Lost author cue.** No indication who is being replied to — feed user
   can't tell from a glance whether they care.
3. **Parity gap with Android.** Same account, same relays, same notes — the
   Android client shows the conversation structure; desktop doesn't.

## Proposed Solution

Three discrete layers of work:

1. **Detection** — reuse `BaseThreadedEvent.replyingToAddressOrEvent()` from
   `quartz/` (already KMP-common, already covers both NIP-10 and NIP-22).
   No new code needed in `quartz/`.
2. **Shared UI** — extract `ReplyRenderType` enum and `ReplyToLabel`
   composable from `amethyst/` to `commons/commonMain/`. Refactor away
   Android-only deps (`R.string` → `Res.string`, `nav.nav(routeFor(...))` →
   click callback).
3. **Desktop wiring** — extend `NoteDisplayData` with an optional
   `replyContext: ReplyContext?` field, populate it inside
   `Event.toNoteDisplayData(cache)`, and render it from `NoteCard` using
   the shared `ReplyToLabel` + the existing desktop `QuotedNoteEmbed()`
   (NoteCard.kt:488–535) for the parent card.

## Survey Matrix (per CLAUDE.md convention)

| Component | Status | Location | Action |
|---|---|---|---|
| `replyingToAddressOrEvent()` | ✅ Reuse | `quartz/.../nip10Notes/BaseThreadedEvent.kt` | Call as-is, no changes |
| `ReplyRenderType` enum | ⚠️ Avoid | `amethyst/.../ui/note/types/Text.kt:59-63` | Leave in Android. Desktop only uses FULL; extracting is YAGNI |
| `ReplyToLabel` composable | 📦 Extract | `amethyst/.../ui/note/ReplyInformation.kt:121-142` | Refactor + move to commons; Android switches to shared version |
| `ReplyInfoMention` helper | ⚠️ Avoid | `amethyst/.../ui/note/ReplyInformation.kt:145-163` | Leave in Android. v1 ReplyToLabel takes plain `String`; emoji-in-name regression flagged |
| `ReplyNoteComposition` | ⚠️ Avoid | `amethyst/.../ui/note/NoteCompose.kt:1491-1508` | Hard-coupled to `NoteCompose` (1100+ LOC). Desktop renders parent via its own `QuotedNoteEmbed`. |
| `ReplyContext` data class | 🆕 New | `commons/commonMain/.../ui/note/ReplyContext.kt` | Pure protocol→display struct. Lives in commons so Android can adopt later. |
| Desktop `QuotedNoteEmbed` | ✅ Reuse | `desktopApp/.../ui/note/NoteCard.kt:488-535` | Use unchanged. Recursion impossible because we pass `replyContext` as a separate param to `NoteCard`, not via `NoteDisplayData`. |
| Desktop `NoteCard` | 📦 Extend | `desktopApp/.../ui/note/NoteCard.kt:81-88, 95+` | Add optional `replyContext: ReplyContext?` param, branch render |
| Reply detection / `ReplyContext` build | 🆕 New | `commons/commonMain/.../ui/note/ReplyContext.kt` (companion `from(event, cache)`) | Pure function: `BaseThreadedEvent` + `ICacheProvider` → `ReplyContext?` |
| `Event.toNoteDisplayData()` | ✅ Reuse | `desktopApp/.../ui/EventExtensions.kt:32-53` | UNCHANGED. Reply detection happens separately in FeedScreen's item render block. |
| `DesktopLocalCache.getNoteIfExists` | ✅ Reuse | `desktopApp/.../cache/DesktopLocalCache.kt:564-571` | Call from converter for parent lookup |
| `User.toBestDisplayName()` | ✅ Reuse | `commons/.../model/User.kt:90` | Author label for "Replying to @X" |
| FeedScreen repost pattern | ✅ Mirror | `desktopApp/.../ui/FeedScreen.kt:128-197` | Apply same flow-observation pattern for parent recompose |
| `Res.string.replying_to` | 🆕 New | `commons/src/commonMain/composeResources/values/strings.xml` | Add string key |
| `Reply.kt` icon | ✅ Reuse | `commons/.../icons/Reply.kt` | Already extracted |
| Reply embed visual border | 🆕 New | `desktopApp/.../ui/note/NoteCard.kt` | Add small subtle-border style; Android has `replyModifier` but desktop has none |

**Legend:** ✅ Reuse · 📦 Extract / Extend · 🆕 New · ⚠️ Avoid (duplicate / blocked)

## Technical Approach

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  desktopApp/                                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ FeedScreen.kt — LazyColumn item block                 │  │
│  │   observe note + parent flowSet.metadata              │  │
│  │   call Event.toNoteDisplayData(cache) → NoteDisplayData│  │
│  │      └─ with replyContext: ReplyContext? populated    │  │
│  │   render NoteCard(noteDisplayData)                    │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ NoteCard.kt — render path branch                      │  │
│  │   if (replyContext != null):                          │  │
│  │     Column {                                          │  │
│  │       QuotedNoteEmbed(replyContext.parent)            │  │
│  │       ReplyToLabel(parentAuthor, onUserClick)         │  │
│  │       <existing body render>                          │  │
│  │     }                                                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ depends on (shared)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  commons/commonMain/                                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ ui/note/ReplyToLabel.kt (NEW)                         │  │
│  │   @Composable fun ReplyToLabel(                       │  │
│  │     parentAuthor: User,                               │  │
│  │     onUserClick: (User) -> Unit,                      │  │
│  │   )                                                   │  │
│  │ ui/note/ReplyRenderType.kt (NEW)                      │  │
│  │ resources/strings.xml — adds `replying_to`            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ used by
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  amethyst/ (Android)                                        │
│  ReplyInformation.kt::ReplyToLabel — DELETED               │
│  Callers updated: pass `onUserClick = { u → nav.nav(routeFor(u)) }`│
└─────────────────────────────────────────────────────────────┘
```

### Data Shape: `ReplyContext`

Lives in `commons/commonMain/.../ui/note/ReplyContext.kt`:

```kotlin
package com.vitorpamplona.amethyst.commons.ui.note

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

data class ReplyContext(
    /** Hex event id of the parent. Null when the reply targets only an addressable (`a` coord). */
    val parentNoteId: String?,
    /** Hex pubkey of the parent's author. Used by the click handler. */
    val parentAuthorPubKey: String,
    /** Resolved display name (or truncated hex fallback) for the "Replying to @X" label. */
    val parentAuthorDisplay: String,
) {
    companion object {
        /**
         * Pure detection. Returns null if [event] is not a reply, or if we
         * can't determine the parent author yet (caller should retry once
         * the parent event arrives in cache).
         */
        fun from(event: BaseThreadedEvent, cache: ICacheProvider?): ReplyContext? {
            val raw = event.replyingToAddressOrEvent() ?: return null
            val isAddressable = raw.contains(":")
            val parentNoteId = if (isAddressable) null else raw

            val parentAuthorPubKey = when (event) {
                is CommentEvent -> event.replyAuthor()
                else -> null
            } ?: parentNoteId?.let { cache?.getNoteIfExists(it)?.author?.pubkeyHex }
                ?: return null

            val parentAuthorDisplay = cache?.getUserIfExists(parentAuthorPubKey)
                ?.toBestDisplayName()
                ?: parentAuthorPubKey.take(8) + "…"

            return ReplyContext(parentNoteId, parentAuthorPubKey, parentAuthorDisplay)
        }
    }
}
```

**Three fields.** Dropped from earlier draft:
- `isAddressableParent: Boolean` — redundant; just check `parentNoteId == null`.
- Andriod's last-`p`-tag fallback path — fragile and only saved one render
  frame. Now: if we can't resolve the author (parent not cached, not a
  CommentEvent), `from()` returns null and the reply renders as a regular
  post. When the parent event arrives via subscription, recomposition
  re-evaluates `from()` and the label appears.

**ReplyContext is computed at the FeedScreen item-render level**, not at
event→display-data conversion time. It's passed as a *separate parameter*
to `NoteCard`, not embedded in `NoteDisplayData`. This is the structural
fix from the architecture/simplicity review: `NoteDisplayData` stays a
pure display struct; `NoteCard` doesn't need a flag to suppress embedded
recursion (the recursive `QuotedNoteEmbed → NoteCard` chain never receives
a `replyContext` parameter, so it can never render one). Recursion
impossible by construction.

### `NoteDisplayData` — UNCHANGED

After the review pass, `NoteDisplayData` is **not** modified. It stays a
strictly display-only snapshot. Reply context flows alongside it through
the `NoteCard` parameter list. This preserves single-responsibility and
removes the need to audit `toNoteDisplayData`'s 9 callsites.

### Reply Detection — `ReplyContext.from(event, cache)`

All detection logic lives in the `ReplyContext.from()` companion function
(spec'd above). `Event.toNoteDisplayData` is **not** modified. The
FeedScreen item-render block calls `ReplyContext.from()` separately when
preparing each feed item.

**NIP-10 / NIP-22 unification confirmed.**
`BaseThreadedEvent.replyingToAddressOrEvent()` at
`quartz/.../BaseThreadedEvent.kt:78-84` is **overridden** by
`CommentEvent.replyingToAddressOrEvent()` at
`quartz/.../nip22Comments/CommentEvent.kt:224` — polymorphic dispatch
makes our caller blind to which NIP it's handling. One code path.

**Return type:** `replyingToAddressOrEvent(): String?` returns a flat
string with no discriminant. Disambiguation: `raw.contains(":")` —
addresses use `kind:pubkey:d-tag` format; event IDs are pure hex.

### FeedScreen Integration

Mirror the repost pattern at FeedScreen.kt:128–152, which uses
`originalNote.flow()` → `NoteFlowSet` → `.metadata.stateFlow.collectAsState()`.
The `Note.flow()` accessor lives at `commons/.../model/Note.kt:913-916` and
returns a `NoteFlowSet` with `metadata`, `reactions`, `replies`, `zaps` —
all `StateFlow<T>`.

```kotlin
// For each LazyColumn item, before rendering NoteCard:
val displayData = remember(event, metadataState) {
    event.toNoteDisplayData(localCache)
}

// Reply-context computation — observe parent metadata so the label/embed
// pop in once the parent arrives.
val replyTargetEventId = remember(event) {
    (event as? BaseThreadedEvent)
        ?.replyingToAddressOrEvent()
        ?.takeUnless { it.contains(":") } // skip addressable; label-only path
}
val parentNote = replyTargetEventId?.let {
    remember(it) { localCache.getOrCreateNote(it) }
}
val parentMetaState = parentNote?.let {
    remember(it) { it.flow() }.metadata.stateFlow.collectAsState()
}
val replyContext = remember(event, parentMetaState?.value) {
    (event as? BaseThreadedEvent)?.let { ReplyContext.from(it, localCache) }
}

NoteCard(
    displayData = displayData,
    replyContext = replyContext,           // NEW: sibling param
    localCache = localCache,
    onNavigateToThread = onNavigateToThread,
    onNavigateToProfile = onNavigateToProfile,
)
```

**Why this works.**

- `getOrCreateNote` always returns a `Note` — placeholder with `event = null`
  if not yet fetched. The `NoteFlowSet.metadata` still emits when the event
  later arrives (verified — same pattern reposts use today).
- `remember(event, parentMetaState?.value)` invalidates the `replyContext`
  computation when the parent's metadata changes. Parent embed pops in via
  recomposition without scroll-position jump.
- The `?.takeUnless { it.contains(":") }` guards against trying to fetch an
  addressable note by event id; addressable parents render label-only.
- `ReplyContext.from()` returns null when author can't be resolved — reply
  renders as a regular post until parent arrives (eventual consistency).

**Subscription batching.** Existing feed subscription (FeedScreen.kt:419–429)
already batches missing event IDs. Extend the missing-set computation:

```kotlin
val missingParentIds = displayItems.mapNotNull { item ->
    val target = (item.event as? BaseThreadedEvent)
        ?.replyingToAddressOrEvent()
        ?.takeUnless { it.contains(":") }
    target?.takeIf { localCache.getNoteIfExists(it)?.event == null }
}
```

Pipe these into the existing batch — no new subscription path.

### NoteCard Render Path

`QuotedNoteEmbed` signature (NoteCard.kt:488-493) — **UNCHANGED**:

```kotlin
@Composable
fun QuotedNoteEmbed(
    noteId: String,
    localCache: DesktopLocalCache?,
    onMentionClick: ((String) -> Unit)? = null,
    onNavigateToThread: ((String) -> Unit)? = null,
)
```

Recursion is impossible because `replyContext` is a `NoteCard` parameter,
not a field of `NoteDisplayData`. When `QuotedNoteEmbed` internally calls
`NoteCard(...)` for the parent (line 512), it doesn't pass `replyContext`
— the default null applies and the parent renders without further embed.

NoteCard render path:

```kotlin
@Composable
fun NoteCard(
    displayData: NoteDisplayData,
    localCache: DesktopLocalCache?,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    replyContext: ReplyContext? = null,   // NEW — default null suppresses embed recursion
    ...
) {
    Column {
        replyContext?.let { ctx ->
            if (ctx.parentNoteId != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp),
                        ),
                ) {
                    QuotedNoteEmbed(
                        noteId = ctx.parentNoteId,
                        localCache = localCache,
                        onNavigateToThread = onNavigateToThread,
                    )
                }
            }
            ReplyToLabel(
                parentAuthorDisplay = ctx.parentAuthorDisplay,
                onClick = { onNavigateToProfile(ctx.parentAuthorPubKey) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        // existing body / actions render — unchanged
    }
}
```

**Visual styling.** `MaterialTheme.colorScheme.outlineVariant` (1.dp border)
is already used in `ThreadScreen.kt:283`. Matches Android's `replyModifier`
intent without inventing new theme keys. Rounded 8.dp corners match the
existing card chrome of the `Loading quoted note...` placeholder.

### Shared Composable: `ReplyToLabel` (in commons)

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/ReplyToLabel.kt
@Composable
fun ReplyToLabel(
    parentAuthorDisplay: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier) {
        Text(
            text = stringResource(Res.string.replying_to),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            text = "@$parentAuthorDisplay",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
```

Key extraction refactor decisions:

- **String:** `R.string.replying_to` → new `Res.string.replying_to` in
  `commons/.../composeResources/values/strings.xml`. Localized strings already
  in Android `res/values-*/strings.xml` will move to commons in a follow-up
  (out of scope for this fix — English-only commons string acceptable for v1
  per existing commons string practice).
- **Navigation:** drop the `INav` + `routeFor` coupling; expose `onClick: () -> Unit`
  callback. Android caller passes
  `{ nav.nav(routeFor(parentAuthorUser)) }`; desktop passes
  `{ onNavigateToProfile(pubKey) }`.
- **User lookup:** caller resolves the display name and passes it as
  `String` rather than passing a `User` and resolving inside. Keeps the
  composable pure of cache dependencies. This deliberately diverges from the
  original Android `ReplyToLabel` signature which took a `Note` — the
  refactored version is cleaner and equally functional.
- **`ReplyInfoMention` helper (lines 145–163):** the simpler "render
  display name + emoji" logic is inlined into the new `ReplyToLabel` for
  v1; if desktop later needs the emoji-aware variant we extract that too.
  Android currently uses `ReplyInfoMention` for emoji rendering inside the
  label — for v1, the simple `@displayName` text is acceptable; Android may
  regress on inline emoji in display names until follow-up.

**Trade-off flagged:** the Android user briefly loses inline custom-emoji
rendering in the "Replying to @X" line. Acceptable for this fix; raise as
follow-up.

### Shared: `ReplyRenderType` enum

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/ReplyRenderType.kt
enum class ReplyRenderType { FULL, LINE, NONE }
```

Trivial extraction. Android `Text.kt` imports from commons instead of
declaring locally.

## Implementation Phases

Each phase is independently buildable and testable. Don't move on until the
previous phase compiles + (where applicable) `./gradlew test` passes.

### Phase 1 — Commons Additions

**Goal:** add shared `ReplyContext` data class and `ReplyToLabel` composable
to commons + add the `replying_to` string. No behavior change yet.

Files (exact paths confirmed against repo):
- `commons/src/commonMain/composeResources/values/strings.xml` — add new
  section header and key. `strings.xml` is **domain-grouped** (see existing
  `<!-- Login & Auth -->`, `<!-- Common Actions -->`, `<!-- Errors -->`,
  `<!-- Loading & Empty States -->`, `<!-- Accessibility -->` sections).
  Append:
  ```xml
  <!-- Notes & Replies -->
  <string name="replying_to">replying to </string>
  ```
  Trailing space matches Android original. Package for generated `Res` class
  is `com.vitorpamplona.amethyst.commons.resources` (confirmed at
  `commons/build.gradle.kts:194`).
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/ReplyContext.kt` — new file with the data class + companion `from()`.
- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/note/ReplyToLabel.kt` — new composable. Uses `androidx.compose.foundation.layout.FlowRow` (live precedent at `commons/.../nip23LongContent/ui/editor/MetadataPanel.kt:26`) and `org.jetbrains.compose.resources.stringResource`.

**Not extracted:** `ReplyRenderType` enum stays in Android. Desktop only
uses one mode (FULL); extracting just to keep Android importing from
commons is YAGNI.

Verify:
- `./gradlew :commons:compileKotlinJvm` succeeds.
- `./gradlew :commons:compileKotlinAndroid` succeeds.

### Phase 2 — Desktop NoteCard Parameter

**Goal:** add a `replyContext: ReplyContext? = null` parameter to
`NoteCard`. Branch render in the body. `NoteDisplayData` and
`toNoteDisplayData` are NOT touched.

Files:
- `desktopApp/.../ui/note/NoteCard.kt` —
  - Import `ReplyContext` and `ReplyToLabel` from commons.
  - Add `replyContext: ReplyContext? = null` and (if not already present)
    `localCache: DesktopLocalCache?` parameters to `NoteCard`.
  - Insert the render branch above the body (see "NoteCard Render Path"
    section above).

**Recursion impossibility (by construction).** `QuotedNoteEmbed` internally
calls `NoteCard(...)` without passing `replyContext` — the default `null`
applies, no embed. No flag, no callsite audit, no behavior change to any
non-feed surface.

**Existing call sites — do they need updates?** No. They all pass the
default `null` for `replyContext`. Bookmarks / search / quote-mentions
continue rendering exactly as today. Reply context engages only in feed
contexts where Phase 3 explicitly passes a non-null value.

Verify:
- `./gradlew :desktopApp:compileKotlin` succeeds.
- Existing app still renders feed identically (Phase 3 wires the actual
  detection + observation).

### Phase 3 — Desktop FeedScreen Wiring

**Goal:** FeedScreen computes `ReplyContext` per item, observes parent
metadata for async arrival, passes context to `NoteCard`. Replies render
with embedded parent + label.

Files:
- `desktopApp/.../ui/FeedScreen.kt` —
  - In each LazyColumn item (around lines 230-260, the normal note render
    path; mirror the repost pattern at lines 128-152 for the flow
    observation):
    - Compute `replyTargetEventId = event.replyingToAddressOrEvent()?.takeUnless { it.contains(":") }`
    - If non-null, `getOrCreateNote(it)` and observe `note.flow().metadata.stateFlow.collectAsState()`
    - Compute `ReplyContext.from(event as BaseThreadedEvent, localCache)` in a `remember(event, parentMetaState?.value)` block
    - Pass the resulting `replyContext` to `NoteCard`
  - Extend the missing-event-IDs batch (lines 419–429) with reply parent IDs
    so relay subscriptions fetch them along with everything else.

Manual check after this phase: see Testing section.

### Phase 4 — Android Switchover

**Goal:** Android's `ReplyToLabel` definition deletes; callers use the
shared commons version.

Files:
- `amethyst/.../ui/note/ReplyInformation.kt` — delete `ReplyToLabel`
  function (lines 121–142). Keep `ReplyInfoMention` (lines 145–163) — it's
  still used elsewhere if any caller remains (grep first).
- `amethyst/.../ui/note/types/Text.kt` —
  - In `RenderTextEvent`'s LINE branch (lines 117-121), replace the local
    `ReplyToLabel` call with the imported commons version. Resolve the
    parent author's display name at the call site (currently the Android
    version did it internally):
    ```kotlin
    val parentAuthor = remember(replyingDirectlyTo) {
        replyingDirectlyTo.author?.toBestDisplayName().orEmpty()
    }
    ReplyToLabel(
        parentAuthorDisplay = parentAuthor,
        onClick = { replyingDirectlyTo.author?.let { nav.nav(routeFor(it)) } },
    )
    ```
- `amethyst/src/main/res/values/strings.xml` — remove `replying_to`
  (now lives in commons).
- `amethyst/src/main/res/values-*/strings.xml` — leave translations until
  follow-up that migrates to commons multi-locale resources.

**Not changed in Phase 4:**
- `ReplyRenderType` enum stays in `Text.kt` (no extraction; Android keeps
  using its three modes internally).
- `ReplyNoteComposition` unchanged (still uses Android's own `NoteCompose`).
- Custom-emoji rendering in the "Replying to @X" line via `ReplyInfoMention`:
  Android loses this momentarily. Flagged in PR body as known regression
  with follow-up.

Verify:
- `./gradlew :amethyst:compileDebugKotlin` succeeds.
- Run Android app, confirm reply rows in home feed still show "replying
  to @X".

### Phase 5 — Tests, Polish, Format

**Goal:** unit-test the new detection logic, run formatter, ready for review.

Files:
- `commons/src/jvmTest/.../ReplyContextTest.kt` — new test file with cases
  exercising `ReplyContext.from(event, cache)`:
  - kind 1 with reply marker → `parentNoteId` matches the marked tag.
  - kind 1 with root + reply markers → `parentNoteId` matches reply, not
    root.
  - kind 1 single unmarked e tag → `parentNoteId` matches (positional).
  - kind 1111 with `e` tag → `parentNoteId` matches.
  - kind 1111 with `a` tag → `parentNoteId == null` (addressable).
  - Non-reply kind 1 → returns null.
  - Reply with parent NOT in cache and no NIP-22 `replyAuthor()` → returns
    null (eventual consistency — pops in on parent arrival).
  - Reply with parent in cache → `parentAuthorPubKey` matches parent's
    `pubKey`, `parentAuthorDisplay` from User metadata if loaded.

Test placement rationale: lives in `commons/` because that's where
`ReplyContext` and its `from()` function live. Pattern follows existing
commons test conventions.

**Skipped:** commons `ReplyToLabelTest` smoke test — testing a 2-`Text`
composable provides no real coverage; logic lives in `ReplyContext.from()`
which has its own test.

Run:
- `./gradlew :commons:jvmTest --tests "*ReplyContext*"`
- `./gradlew spotlessApply`

## System-Wide Impact

### Interaction Graph

- `FeedScreen` LazyColumn item → reads note + parent flowSet metadata →
  recomposes on either change → calls `toNoteDisplayData(cache)` → resolves
  parent via `getNoteIfExists` → constructs `NoteDisplayData` → `NoteCard`
  branches on `replyContext` → renders `QuotedNoteEmbed` + `ReplyToLabel`.
- Click on `ReplyToLabel` → invokes `onNavigateToProfile(parentPubKey)` →
  `DeckLayout` pushes profile column.
- Click on `QuotedNoteEmbed` → invokes `onNavigateToThread(parentNoteId)` →
  `DeckLayout` pushes thread column (existing behavior).

### State Lifecycle Risks

- **Parent fetched-then-evicted from cache.** `LargeSoftCache` is a soft
  reference cache; under memory pressure the parent `Note` could be evicted
  between `getNoteIfExists` calls. Impact: `QuotedNoteEmbed` falls back to its
  "Loading quoted note..." card briefly until re-fetched. Mitigation: the
  parent flow observation re-triggers fetch on next recomposition.
  Acceptable.
- **Reply detection wrong for thread root.** A note with only a `root`
  marker (no `reply` marker) is a reply to the root. `replyingToAddressOrEvent`
  already returns the root in that case — covered.

### Error & Failure Propagation

- `replyingToAddressOrEvent()` returns null for non-replies → `resolveReplyContext`
  returns null → no UI change. Safe.
- Malformed `e` tag (non-hex) → `toEventIdOrNull()` returns null →
  `resolveReplyContext` returns null. Silent skip is correct.
- Missing parent + missing tag author pubkey → `resolveReplyContext` returns
  null. Reply renders as regular post (current behavior, no regression).

### API Surface Parity

| Surface | Reply rendering today | After this change |
|---|---|---|
| Desktop home feed | None | Full (this PR) |
| Desktop hashtag / profile feeds | None | **Inherits**, since all desktop feeds funnel through `FeedScreen` + `NoteCard`. Verify in QA. |
| Desktop thread view | Already structural | Unchanged |
| Android home feed | Full | Unchanged (still works via re-pointed `ReplyToLabel`) |

### Integration Test Scenarios

(Manual; documented under Testing.)

1. Post a fresh reply on Android → confirm appears with embed + label on
   desktop home feed.
2. Reply via desktop to a note from another account → confirm embed appears
   in own feed after relay round-trip.
3. NIP-22 kind 1111 reply on an article (kind 30023) → confirm
   addressable-parent path renders label only (no embed in v1).
4. Scroll feed fast, parent loads after reply visible → confirm embed
   "pops in" via recomposition without scroll-position jump.
5. Reposted reply (someone reposts another's reply) → confirm we don't
   recursively double-wrap (only the outermost repost wrapper renders; inner
   reply context not re-embedded inside the repost). v1 acceptable behavior.

## Acceptance Criteria

### Functional

- [ ] A reply note in the desktop home feed renders the embedded parent
      card directly above the reply body.
- [ ] A reply note shows "replying to @displayName" below the parent embed
      and above the reply body.
- [ ] Clicking the "@displayName" opens the parent author's profile.
- [ ] Clicking the embedded parent opens the parent's thread.
- [ ] If the parent event is not yet in cache, the label is rendered with
      the parent author's display name (or truncated hex if metadata also
      missing). No empty quoted card is rendered.
- [ ] When the parent event arrives via relay subscription, the embed
      appears via recomposition without manual refresh.
- [ ] NIP-22 generic comments (kind 1111) get the same treatment as
      NIP-10 kind-1 replies.
- [ ] Non-reply notes render identically to today (no regression).
- [ ] Reposts continue to render identically to today.
- [ ] Android home feed reply rendering still works (no regression after
      switchover to shared `ReplyToLabel`).

### Non-Functional

- [ ] No measurable feed scroll-frame regression (visual sniff test on a
      ~500-item feed).
- [ ] No new unbounded subscriptions — parent fetches use the existing
      batched missing-event-ID mechanism.
- [ ] Quote depth strictly = 1 (parent embed does not itself embed a
      grandparent). Verified by unit test.

### Quality

- [ ] `./gradlew :commons:build` passes.
- [ ] `./gradlew :desktopApp:test` passes (including new tests).
- [ ] `./gradlew :amethyst:compileDebugKotlin` passes.
- [ ] `./gradlew spotlessApply` applied; no diff after.
- [ ] Manual UI checklist (above) passes on a real account.

## Testing

### Unit

```bash
./gradlew :desktopApp:test --tests "*EventExtensionsReplyContext*"
./gradlew :commons:jvmTest --tests "*ReplyToLabel*"
```

### Manual UI Checklist

1. Launch desktop app: `./gradlew :desktopApp:run`
2. Open home feed, scroll to a thread with replies.
3. Verify reply rows show parent embed + "replying to @X".
4. Click "@X" → profile opens in new deck column.
5. Click parent embed → thread opens in new deck column.
6. Force-fetch a reply whose parent is NOT yet cached (e.g. follow
   someone whose reply targets an old root):
   - confirm label shows immediately.
   - confirm embed appears within a few seconds (after relay returns).
7. Open a feed of an account that uses kind 1111 (NIP-22) replies →
   confirm same treatment.
8. Compare against Android side-by-side for the same account/relays.
9. Confirm a long thread doesn't recursively show grandparent (1-level
   cap).
10. Confirm reposts don't visually regress.

## Dependencies & Risks

- **No new third-party deps.**
- **Depends on quartz's `replyingToAddressOrEvent()` being correct** — this
  is already battle-tested on Android. Low risk.
- **Risk: Android emoji-in-reply-label regression** for users with custom
  emoji in display names. Documented trade-off; follow-up to extract
  `ReplyInfoMention` if regression confirmed.
- **Risk: `QuotedNoteEmbed` signature mismatch** — implementation phase
  may need to refactor it to accept `NoteDisplayData`. Contained.
- **Risk: Address-based parents (`a` tag) for kind 30023 articles** — the
  embed render path may not handle non-kind-1 parents gracefully. Mitigation:
  when `ReplyContext.parentNoteId == null` (addressable), render label only
  (skip the `QuotedNoteEmbed` branch). Spec'd in NoteCard render path.

## Sources & References

### Origin

- **Brainstorm:**
  [docs/brainstorms/2026-06-05-desktop-feed-reply-context-brainstorm.md](../brainstorms/2026-06-05-desktop-feed-reply-context-brainstorm.md)
  — key decisions carried forward: FULL render mode, extract to commons now,
  NIP-22 same treatment, 1-level depth, pre-compute in
  `Event.toNoteDisplayData`.

### Internal

- Detection: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip10Notes/BaseThreadedEvent.kt:73-84`
- Android render: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/types/Text.kt:82-124`
- Android `ReplyToLabel`: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/ReplyInformation.kt:121-142`
- Android `ReplyNoteComposition` (NOT extracted — for reference only):
  `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/note/NoteCompose.kt:1491-1508`
- Desktop `NoteCard` + `NoteDisplayData`:
  `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/note/NoteCard.kt:81-88, 488-535`
- Desktop converter: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/EventExtensions.kt:32-53`
- Desktop cache: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:564-571`
- Repost pattern (blueprint): `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/ui/FeedScreen.kt:128-197`
- Best-name resolution: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/User.kt:90`
- Compose Multiplatform string pattern: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/screens/PlaceholderScreens.kt:31-38`

### Worktree

- Path: `../AmethystMultiplatform-feed-reply-context`
- Branch: `fix/desktop-feed-reply-context`
- Base: `origin/main` @ `0874706b3`

## Unanswered Questions

### Resolved by deepen-plan research

- ✅ **`QuotedNoteEmbed` signature** — takes `(noteId: String, localCache, onMentionClick?, onNavigateToThread?)`. Handles loading state internally. We just pass `noteId`.
- ✅ **`replyingToAddressOrEvent()` return** — flat `String?`. Disambiguate event-id vs address by `":" in raw`.
- ✅ **NIP-22 addressable parent** — v1 = **label-only** (no embed). `QuotedNoteEmbed` doesn't accept `Address`. Follow-up.
- ✅ **Visual border** — `MaterialTheme.colorScheme.outlineVariant` 1.dp + `RoundedCornerShape(8.dp)`. Already used in `ThreadScreen.kt:283`.
- ✅ **Translations** — leave Android translations until follow-up commons multi-locale consolidation. English-only key in commons `strings.xml`.
- ✅ **Reposted-reply behavior** — FeedScreen extracts inner note then renders full `NoteCard` on it. Our new reply-context auto-engages on the inner card. Acceptable; no extra work.
- ✅ **Hashtag / profile / bookmarks / search feeds** — all use `Event.toNoteDisplayData(localCache)` (9 verified call sites). Reply context auto-engages everywhere with no plumbing.

### Still open — flagged for implementation phase

- ⚠️ **Inline custom-emoji in "Replying to @X"** — Android currently uses `ReplyInfoMention` (lines 145-163) which renders emoji-aware display names via `CreateTextWithEmoji`. Our extracted `ReplyToLabel` takes a plain `String` for v1, losing emoji on Android. Trade-off: simpler API, faster ship. If regression complaints arrive, follow-up adds an emoji-aware overload.
- ⚠️ **`Note.flow()` performance with many feed items** — each LazyColumn item now creates and `collectAsState`s an extra StateFlow when it's a reply. For a 500-item feed where 50% are replies, that's ~250 extra collectors. Repost code already does this so it's the established pattern, but profile if scroll regresses.
- ⚠️ **Parent author availability when not in cache and event is not NIP-22** — `ReplyContext.from()` returns null. Reply renders as a regular post until the parent event arrives via subscription, then recomposition picks it up. UX impact: brief moment where a reply looks like a regular post. Acceptable (better than guessing the wrong author from a `p` tag).
