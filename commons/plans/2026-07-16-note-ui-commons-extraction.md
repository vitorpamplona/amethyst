---
title: "refactor(commons): extract note-type rendering to commons"
type: refactor
status: proposed
date: 2026-07-16
owner: commons
consumers: amethyst, desktopApp, cli
---

# refactor(commons): extract `ui.note` rendering into commons

> **Status:** review / proposal. No code moved yet. This is the study
> requested on branch `claude/amethyst-note-ui-refactor` — a per-event
> generalization plan for pulling the *rendering* half of
> `com.vitorpamplona.amethyst.ui.note` down into `commons`, leaving only the
> nav/AccountViewModel-bound *entry* composables in the Android app.
> _Authored 2026-07-16._

## 0. Goal (as requested)

Leave the **entry composable** — the one that takes `AccountViewModel` + `INav`
and decodes the `Note`/`Event` — in the mobile app. Move the **actual
rendering** (the Compose layout, specific functions, and their local UI state)
into `commons`, where it takes plain values, immutable state objects, and
**lambda callbacks** instead of `AccountViewModel`/`INav`. The Android package
then shrinks to a thin adapter that wires the lambdas.

This is deliberately narrower than the two standing plans and complementary to
both:

- `commons/plans/2026-04-21-event-renderer.md` — a UI-agnostic
  `RenderedEvent` data model. Ambitious, still "proposed", nothing built.
- `commons/plans/2026-05-30-amethyst-to-commons-migration.md` — the full app
  migration; its §4 puts `ui/note` (184 files, ~50–70% shareable) behind the
  **Account keystone (Phase A)**, which is untouched.

The key insight of *this* plan: **we do not need the Account keystone to start.**
The `Render → Display` split lets each event's layout move now, because the
`Display` half never touches `AccountViewModel` — the `Render` half (staying in
`amethyst`) does the account/relay work and hands down primitives + callbacks.

## 1. What the package looks like today

`amethyst/ui/note`: **214 files, ~48.7k LOC**.
- `types/` — **89 files, ~21.9k LOC**: one `RenderXxx` composable per Nostr
  event kind (the dispatch targets).
- top level — 39 files, ~14.7k LOC: `NoteCompose.kt` (2180 LOC, the central
  dispatcher), avatars, reaction/zap rows, dialogs, formatters.
- `creators/` (post editor), `elements/`, `buttons/`, `nip22Comments/`,
  `share/` — mostly interactive, largely stays native (see §7).

### The dispatcher stays native

`NoteCompose.kt` → `RenderNoteRow()` is a giant `when (baseNote.event)` that
routes each kind to its `RenderXxx(...)`. This is the natural home of the
**entry** layer and should *not* move — it is inherently coupled to
`AccountViewModel`, `INav`, feed state, the quick-action menu, drag/swipe, and
report/block gating.

### A two-tier pattern is already emerging

Several types already split `RenderXxx(note, …)` (decodes the event) from a
`DisplayXxx(…decomposed params…)` (renders). `Highlight.kt` is the clearest:
`RenderHighlight(note, …)` → `DisplayHighlight(comment, highlight, context,
authorHex, url, postAddress, …)`. **But `DisplayHighlight` still takes
`accountViewModel` + `nav`.** The work of this plan is to push that boundary the
last mile so the `Display*` layer takes callbacks/state instead — then relocate
it to `commons`.

### The target already has a reference implementation

`commons/ui/note/StaticWebsiteCard.kt` is exactly the end-state shape: pure
value params, lambda slots (`onOpen: (() -> Unit)?`, `headerActions:
@Composable (() -> Unit)?`), commons `Res.string`, commons icons, Coil
`AsyncImage` — **zero `AccountViewModel`/`INav`**. Also already extracted into
`commons/ui/note/`: `CollapsedSpamNote`, `HeaderPill`, `QuietMark`,
`ReplyContext`, `ReplyToLabel`. Do not re-propose these — copy their convention.

## 2. The canonical signature (the generalization anchor)

Across `types/`, the entry composable is remarkably uniform. Parameter frequency
(public `@Composable` entry fns):

```
accountViewModel 198   note/baseNote 152   backgroundColor 71
nav 157             quotesLeft 41       makeItShort 38   canPreview 35
```

So the near-universal entry shape is:

```kotlin
@Composable
fun RenderXxx(
    note: Note,
    quotesLeft: Int,               // recursion budget for embedded quotes
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,          // compact feed vs full thread view
    canPreview: Boolean,           // expand link/media previews inline
    accountViewModel: AccountViewModel,
    nav: INav,
)
```

`backgroundColor`, `quotesLeft`, `makeItShort`, `canPreview` are **pure display
inputs** and travel straight into `commons` unchanged. Only `accountViewModel`
and `nav` need a seam.

## 3. The dependency surface to cross

### 3a. `AccountViewModel` — smaller behavioral surface than it looks

`accountViewModel` appears 198× in `types/`, but most are **pass-through** to
leaf composables, not direct calls. The actual method surface across the whole
`note` package, grouped:

- **Lookups** (resolve a hex/address to a `Note`/`User`): `getNoteIfExists`,
  `getOrCreateAddressableNote`, `getUserIfExists`, `checkGetOrCreateUser`,
  `loadParticipants`, `loadUsers`, `userProfile`. → become **loader lambdas**
  or pre-resolved state on the entry side.
- **Config/settings**: `settings`, `zapAmountChoices`, `showSensitiveContent`,
  `httpClientBuilder`, `nip`. → passed as plain values / a small settings holder.
- **Actions (write + signer)**: `zap`, `follow`/`unfollow`, `delete`, `hide`,
  `reactToOrDelete`, `decrypt`, `launchSigner`, `addPublicBookmark`,
  `muteThread`. → become **callback lambdas** (`onZap: () -> Unit`, …).
- **Predicates**: `isLoggedUser`, `isWriteable`, `isThreadMutedFor`. → plain
  `Boolean` params.
- **Infra**: `toastManager`, `runOnIO`, `tempManualPaymentCache`. → stay native;
  surfaced as callbacks (`onError: (msg) -> Unit`) where they cross the seam.

### 3b. `INav` — trivial

Already an interface; the migration plan §1 prescribes the answer: shared
composables take `onClick*` / `onNavigate` lambdas, not `INav`. Each nav target
in a `Display*` body becomes one lambda (`onClickUser: (User) -> Unit`,
`onClickNote`, `onClickHashtag`, …).

### 3c. `R.string` (52/89 type files) — solved pattern

Migrate the needed strings to `commons/.../composeResources/values/strings.xml`
and swap `stringResource(R.string.x)` → `stringResource(Res.string.x)`. No new
abstraction (see `StaticWebsiteCard`, which already does this). This is the
single biggest *mechanical* cost, but it is rote.

### 3d. The **leaf-composable toolkit** — the real blocker

The render bodies don't just lay out boxes; they recursively call a handful of
heavy shared composables that *themselves* take `accountViewModel`/`nav`. Top
leaf deps in `types/`:

```
TranslatableRichTextViewer 55   observeNoteEvent 23   LoadNote 18
UserPicture 16                  observeNote 15         LoadUser 13
NoteCompose 12                  LoadDecryptedContent 6 ClickableUrl 6
```

Two sub-problems:

1. **`TranslatableRichTextViewer` is flavor-specific.** It has *separate*
   `src/play/` (ML-Kit translation) and `src/fdroid/` (no translation)
   implementations. It is the #1 dependency (55 uses) and cannot be naively
   moved. **Resolution:** the `Display*` layer takes rich text as a
   **`@Composable` slot** (`richText: @Composable (content: String) -> Unit`)
   that the entry supplies. The shared body decides *where* text goes; the app
   decides *how* it renders (translation or not). This keeps the flavor split in
   `amethyst` untouched.
2. **`LoadNote`/`LoadUser`/`observe*` are relay-subscription + LocalCache
   bound.** These live in `amethyst/service/relayClient/reqCommand/`. Per the
   migration plan they stay native orchestration. **Resolution:** resolve on the
   entry side and pass the loaded `Note`/`User` (or a small immutable snapshot)
   down; or pass embedded-content as a slot the same way as rich text.

**This is the crux of the generalization:** the seam is not "primitives only"
(too limiting for content that embeds other notes/users), it is
**primitives + immutable state objects + `@Composable` slots for recursive
content + lambda callbacks for actions.** `StaticWebsiteCard`'s `headerActions`
slot is the pattern in miniature.

## 4. The proposed split contract

For each event kind, produce two composables:

**Entry (stays in `amethyst/ui/note/types/`), thin adapter:**
```kotlin
@Composable
fun RenderXxx(note, quotesLeft, backgroundColor, makeItShort, canPreview,
              accountViewModel, nav) {
    val event = note.event as? XxxEvent ?: return
    // decode event → values; resolve users/notes via accountViewModel;
    // build callbacks that close over accountViewModel + nav
    XxxCard(                                   // commons
        title = event.title(), …,
        makeItShort = makeItShort,
        richText = { txt -> TranslatableRichTextViewer(txt, …, accountViewModel, nav) },
        onClickAuthor = { nav.nav(routeFor(author)) },
        onZap = { accountViewModel.zap(note, …) },
    )
}
```

**Display (moves to `commons`), pure:**
```kotlin
@Composable
fun XxxCard(
    title: String?, …,                         // decoded values
    makeItShort: Boolean,
    richText: @Composable (String) -> Unit,    // slot for flavor-coupled leaf
    onClickAuthor: () -> Unit,                  // nav callback
    onZap: (() -> Unit)? = null,                // action callback (null = hidden)
) { /* Compose layout only */ }
```

**Seam types to introduce in `commons/ui/note/`:**
- A small `@Immutable` snapshot per family where many primitives travel together
  (e.g. `AuthorLine(name, pubkeyHex, avatarUrl, nip05)`), so signatures stay
  sane. Prefer these over 15-arg functions.
- Reuse existing commons atoms: `UserAvatar`, `Nip05OrPubkeyLine`,
  `ClickableTexts`, `RobohashImage`, `GenericLoadable`, the icon set, `Res`
  strings. Several avatar/user-line needs are **already** in
  `commons/ui/components`.

**Where files land** (per `commons/ARCHITECTURE.md` + migration §4): a
cross-cutting card → `commons/ui/note/`; a NIP-specific renderer → the owning
`commons/.../<nipNN>/ui/` package. Follow whatever the existing
`commons/ui/note/*` files already do.

## 5. Tiered categorization of the 89 `types/`

Classified by the seam each needs (method: `accountViewModel` refs, leaf-dep
grep, `launchSigner`/dialog grep).

### Tier 0 — no `AccountViewModel` at all (13 files, ~2.2k LOC) — **do first**
`ActivityCard, Birdex, CodeSnippet, EcashMint, GitDiffView, GitStatusPill,
ImetaContent, MusicFormatting, PodcastChips, PodcastSoundbites,
PodcastValueSplits, Ps1Save, RoadEvent`.
These are already value-in/Compose-out. Mostly just an `R.string` sweep + a
package move. **Ideal pilots** — several are also self-contained cards (no rich
text, no loaders).

### Tier 1 — decode-only, no loaders, no rich text
Read-only renderers that only need `nav` callbacks + `R.string` (e.g. relay/list
cards, badge display, goal/fundraiser headers, calendar/road cards). Split is
mechanical: decode in entry, pass primitives + `onClick*` down. Bulk of the
value.

### Tier 2 — need recursive content slots (rich text and/or embedded note/user)
23 use `TranslatableRichTextViewer`; 23 use `LoadNote/LoadUser/observe*`:
`Text, TextModification, Highlight, PublicMessage, PrivateMessage, Report,
Chat, CommunityHeader, FollowList, Git, InteractiveStory, MusicTrack,
MusicPlaylist, PodcastEpisode, PodcastMetadata, Video, PictureDisplay,
Attestation, NIP90ContentDiscoveryResponse, ZapPoll, AudioTrack, …`.
Movable, but **only via the `@Composable` slot pattern** (§3d/§4). The layout
moves to commons; rich-text/embeds stay app-supplied. `Highlight` is the
recommended Tier-2 pilot (already half-split).

### Tier 3 — interactive: signer / dialogs / write actions — **stay native or last**
5 use `launchSigner`: `AppDefinition, Badge, CalendarRsvpRow, GitStatusActions,
Poll`. Plus zap/reaction dialogs, `Poll`/`ZapPoll` voting, `Chess`. The
**visual card** can still move (as a Tier-2 body with `onVote`/`onSign`
callbacks), but the interaction/signer flow stays in `amethyst`. Don't force
these early.

## 6. Recommended sequencing

1. **Pilot (1 PR, Tier 0):** move 2–3 pure cards (e.g. `RoadEvent`, `EcashMint`,
   `CodeSnippet`) to `commons/ui/note/`, migrate their strings, leave a thin
   `RenderXxx` entry. Proves the string + package + preview-test loop end to end
   and sets the reviewable template. Verify with `./gradlew :commons:build` and
   a Compose `@Preview`.
2. **Establish the slot contract (1 PR, Tier 2 pilot):** `Highlight` → move
   `DisplayHighlight` to `commons` taking a `richText` slot + `onClickAuthor`/
   `onClickNote` callbacks; entry supplies `TranslatableRichTextViewer` and
   `LoadNote`. This is the load-bearing pattern — get it reviewed before scaling.
3. **Bulk Tier 0/1** — mechanical, parallelizable, one small PR per event family.
4. **Tier 2 family by family** (podcast ×13, git ×5, calendar ×4, music ×3,
   chat ×3) once the slot contract is settled.
5. **Tier 3** last, and only the visual shell; leave signer flows native.

Each step is small, compile-verifiable, and deletes an app-side render body —
exactly the "manually, event by event" cadence you called for.

## 7. Out of scope (stays native)

- `NoteCompose.kt` dispatcher, feed/thread scaffolding, drag/swipe,
  quick-action menu, report/block gating.
- `creators/` (post editor), `buttons/`, most of `share/` — interactive,
  signer- and Context-bound.
- The flavor-specific `TranslatableRichTextViewer` implementations and the
  `service/relayClient/reqCommand` observers/loaders — consumed via slots, not
  moved.

## 8. Risks

- **Slot proliferation.** If every embed becomes a slot, entries get noisy.
  Mitigate with per-family `@Immutable` snapshot types and by reusing existing
  commons atoms rather than re-passing avatars/user-lines as slots.
- **String drift.** Moving strings piecemeal risks duplicate keys between
  `amethyst` `R.string` and commons `Res.string`. Migrate + delete the app copy
  in the same PR; don't leave both.
- **Preview coverage.** The `types/` files carry `@Preview`s (see
  `Highlight.kt`). Move the previews with the body so commons keeps visual
  regression cover; they also become desktop/cli-testable fixtures.
- **Desktop double-benefit vs. drift.** Desktop currently re-parses some of
  this; extracting to commons is the chance to converge — but only if Desktop is
  pointed at the new card in the same or an immediately following PR.
