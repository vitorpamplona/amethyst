---
title: Desktop Note Scheduling & "Drafts & Scheduled" Screen
type: feat
status: completed
date: 2026-07-10
origin: docs/brainstorms/2026-07-09-feat-desktop-note-scheduling-brainstorm.md
---

# ✨ Desktop Note Scheduling & "Drafts & Scheduled" Screen

## Overview

Bring **note scheduling** and a unified **"Drafts & Scheduled"** screen to Amethyst
**Desktop**. A clock icon in the composer opens a date/time picker; scheduled notes
are **pre-signed** at schedule time and stored locally, then published at their
scheduled moment by an **OS-level job** so they fire even when the desktop app is
fully closed. A single sidebar destination lists Drafts and Scheduled notes with
manage actions.

Amethyst **Android already ships full scheduling + NIP-37 drafts** — this is a
Desktop port that **extracts** Android's pure logic into `commons/`, replaces
Android's WorkManager background layer with an OS-scheduler + `amy` publisher, and
adds Desktop-native UI. (see brainstorm: `docs/brainstorms/2026-07-09-feat-desktop-note-scheduling-brainstorm.md`)

## Problem Statement / Motivation

Desktop users cannot schedule notes or manage drafts from a composer. Android has
had this since the `scheduledposts/` service landed. The gap is Desktop-only UI +
a Desktop background-publish mechanism (Desktop has no WorkManager equivalent).

The **crux insight**: because the scheduled event is pre-signed and its JSON is
stored, the process that fires at time T is a *dumb pipe* — it opens a websocket
and pushes already-signed bytes. It **never touches the signing key**. This makes
an OS-level "publish even when app is closed" mechanism safe and small.

## Proposed Solution

1. **Extract** Android's pure scheduling code (`ScheduledPost`, `ScheduledPostStore`,
   the drain/publish loop, time-preset utils, `ScheduleAtButton/Picker` logic) into
   `commons/` so Desktop, Android, and `amy` share one implementation.
2. **Compose integration** (Desktop): add a clock icon + `ScheduleAtPicker` to
   `ComposeNoteDialog`. On "schedule", pre-sign the event (block if a bunker signer
   is offline), store it locally.
3. **Publish mechanism** (hybrid, lifecycle-managed):
   - **In-app timer** fires due posts instantly while the app is open (re-resolves
     write relays via NIP-65 outbox).
   - **OS-level recurring job** (every ~5 min → `amy publish-scheduled`) is
     **registered when the queue goes 0→1 and cancelled when it drains to 0**.
     Fires when the app is closed. Both paths dedup via the store's atomic
     `claimDuePosts()`.
4. **`amy publish-scheduled`** — new headless, key-free `cli` subcommand that drains
   due pre-signed posts and publishes them.
5. **"Drafts & Scheduled" screen** — one Desktop sidebar destination, two tabs
   (Drafts | Scheduled), rows with preview/time/status and actions.
6. **Overdue posts auto-publish** on next app launch / next OS-job tick.

### Key decisions carried from brainstorm

| Decision | Choice | Source |
|----------|--------|--------|
| Target | Desktop port (Android = reference) | brainstorm §Key Decisions |
| Publish model | OS-level, lifecycle-managed single recurring job + in-app timer | brainstorm §1 |
| Publisher | `amy publish-scheduled` (key-free, pre-signed) | brainstorm §2 |
| Signing | Pre-sign at schedule time | brainstorm §3 |
| Bunker offline at schedule | **Block with a message** | brainstorm §Resolved |
| Overdue posts | **Auto-publish** on next launch/tick | brainstorm §4 |
| Drafts storage | Local + **opt-in NIP-37 sync** | brainstorm §5 |
| Relay drift | **Re-resolve at publish** (best-effort; see wrinkle below) | brainstorm §Resolved |
| OS coverage v1 | **All three** (macOS launchd / Windows schtasks / Linux systemd) | brainstorm §6 |
| Screen shape | **One screen, two tabs** | brainstorm §7 |
| Editing | **Cancel + recompose** | brainstorm §8 |
| Draft sync UX | One list, **small "synced" badge** | brainstorm §Resolved |

---

## Locked Decisions (post-deepen, user-confirmed 2026-07-10)

- **Single all-in PR** — implement all phases together (user chose against staging).
  The 3-PR table below is retained only as a risk map / suggested commit sequence
  within the one PR.
- **App-closed mechanism = thin OS trigger → headless mode of the desktop binary.**
  The OS job (launchd/schtasks/systemd) launches the desktop app in a
  `--publish-scheduled` headless mode that reuses the commons publisher against the
  same `~/.amethyst/` store. **Not** the separate `amy` binary (no data-dir mismatch,
  no second bundle, no keychain-on-tick). `amy publish-scheduled` is out of scope for
  this PR (may be added later as CLI parity).
- **Single-writer discipline** guards the store: the GUI in-app timer and the
  OS-triggered headless process must never drain concurrently (lockfile/heartbeat so
  one yields). Keeps the JSON store; SQLite migration not required. Still add the
  `CLAIM_TTL` stuck-`PUBLISHING` recovery + account-scoped claim in Phase 0.

## Enhancement Summary (deepened 2026-07-10)

Deepened with 8 parallel research/review agents (architecture, security, simplicity,
KMP-placement, amy-CLI, cross-process-store, Compose-picker, OS-scheduler). **Three
independent reviewers (architecture, security, simplicity) converged on the same
verdict: the extraction is sound, but the two-process OS-publisher design is the
risky, over-engineered pillar.** Key changes below; details in "Research Insights".

### Key improvements
1. **Staged into 3 PRs.** PR1 (Phases 0–2 + Scheduled tab) delivers the core value
   with ~zero HIGH risks. PR2 adds app-closed OS firing. PR3 adds NIP-37 draft sync.
2. **Publish mechanism reconsidered.** The recommended app-closed mechanism is now a
   **thin OS trigger that launches a headless publish mode of the desktop binary**
   (single-writer, same `~/.amethyst/` dir, real relay re-resolution, no second
   bundled binary, no keychain reads on tick) — with `amy publish-scheduled` kept as
   *optional* CLI/interop parity, not the production path. This collapses four HIGH
   risks at once (cross-process race, data-dir mismatch, amy bundling, keychain
   exposure).
3. **Two latent correctness bugs found in the existing store** (inherited by Android
   too): (a) no cross-process safety in `claimDuePosts` (in-process `Mutex` only), (b)
   **no timeout recovery for stuck `PUBLISHING` rows** → silent permanent drop. Both
   must be fixed in Phase 0.
4. **KMP placement corrected** — Jackson + `java.io.File` are **gate-forbidden in
   `commonMain`** (`verifyKmpPurity`). Store/publisher go in `jvmAndroid`, not
   commonMain. `OsScheduler` `expect` in `jvmAndroid`, not commons/jvmMain.
5. **Security hardening added** to acceptance criteria (0600 perms, absolute/canonical
   trigger path, argv-arrays not `sh -c`, strict plist/unit escaping, key-free path).

### New considerations discovered
- Material3 `DatePicker`/`TimePicker` **do work on Compose Desktop** (CMP 1.11.0) —
  adapt Android's picker inside a `Dialog` (not `AlertDialog`); no custom picker needed.
- `claimDuePosts` is **not account-scoped** — with a single multi-account file, the
  amy/headless path could publish account B's event under account A's relays. Add an
  account-scoped claim.
- Relays commonly **reject `created_at` too far in the future** — pre-signing next
  week's post may be refused at publish; handle as FAILED, and bound stale-overdue
  auto-publish (don't blast weeks-old content silently).

> **The phase details below are superseded by the "Research Insights & Revised Plan"
> section wherever they conflict.** The original phases are kept for provenance.

## Reuse / Extract / New Matrix

| File/Component | Status | Location | Action |
|----------------|--------|----------|--------|
| `ScheduledPost` model + `ScheduledPostStatus` | 📦 Extract (PURE) | `amethyst/.../service/scheduledposts/` | Move to `commons/commonMain` |
| `ScheduledPostStore` (Jackson+Mutex+atomic, StateFlow) | 📦 Extract | same | Move to **`commons/jvmAndroid`** (Jackson+File gate-forbidden in commonMain); inject `File`, NO expect/actual. Add `CLAIM_TTL` recovery + account-scoped claim. |
| Drain/publish loop (from `ScheduledPostWorker`) | 📦 Extract logic | same | New `ScheduledPostPublisher` in **`commons/jvmAndroid`**; call quartz `publishAndConfirmDetailed` (drop `waitForOk`) |
| `INostrClient.publishAndConfirmDetailed()` | ✅ Reuse | `quartz/.../accessories/NostrClientPublishExt.kt` | Key-free publish+confirm (commonMain) |
| `ScheduleAtButton` / `ScheduleAtPicker` | 📦 Extract UI + presets | `amethyst/.../creators/scheduling/` | Presets/rounding → commons; picker to commons Compose |
| `roundUpToNextQuarterHour`, preset fns | 📦 Extract (PURE) | `ScheduleAtPicker.kt` | Move to `commons` util |
| `DraftWrapEvent` (NIP-37, kind 31234) | ✅ Reuse | `quartz/.../nip37Drafts/` | commonMain; usable from Desktop as-is |
| `ScheduledPostWorker` (WorkManager) | ⚠️ Android-only | `amethyst/...` | Keep Android; Desktop uses OS scheduler |
| `ScheduledPostNotifier` | ⚠️ Android-only | `amethyst/...` | `expect/actual` notifier; Desktop = tray/log |
| `ComposeNoteDialog` (composer) | 🆕 Extend | `desktopApp/.../ui/ComposeNoteDialog.kt` | Add clock icon + picker + schedule path |
| `DeckColumnType` (nav) | 🆕 Extend | `desktopApp/.../ui/deck/DeckColumnType.kt` | Add/rename destination for Drafts & Scheduled |
| `DesktopDraftStore` (local, article-oriented) | ⚠️ Reconcile | `desktopApp/.../service/drafts/` | Reuse pattern; add short-note drafts + opt-in NIP-37 |
| `DesktopHighlightStore` (JSON+Mutex+atomic) | ✅ Reuse pattern | `desktopApp/.../service/highlights/` | Template for `DesktopScheduledPostStore` wiring |
| `iAccount.nip65RelayList.outboxFlow` | ✅ Reuse | `desktopApp/.../model/DesktopIAccount.kt` | Write-relay re-resolution (in-app path) |
| OS scheduler (launchd/schtasks/systemd) | 🆕 New (PR2) | `expect` in `commons/jvmAndroid`, actual `jvmMain` (or `desktopApp`) | NOT commonMain (no iOS stub); Desktop-only registration; macOS-first |
| `amy publish-scheduled` subcommand | 🆕 New (PR2, **optional**) | `cli/.../commands/` + `Main.kt` dispatch | Verb group `scheduled run\|list\|publish-now\|cancel` + `--scheduled-file`; optional CLI parity, not the production path |

## Research Insights & Revised Plan (deepen-plan)

### Revised staging (supersedes the single-PR phase list)

| PR | Scope | Risk | Delivers |
|----|-------|------|----------|
| **PR1** | Phase 0 (extract + fix store bugs) + Phase 1 (composer schedule) + Phase 2 (in-app timer + launch catch-up) + Scheduled tab | ~zero HIGH | "Schedule a note; it publishes at its time" — including the laptop-closed-overnight-reopened-next-morning case, which covers the large majority of real usage. |
| **PR2** | App-closed OS firing (thin trigger → headless publish mode), macOS-first behind the abstraction | isolates all HIGH risks | Fires even if the app is never reopened around the scheduled time. |
| **PR3** (optional) | NIP-37 opt-in draft sync (synced badge, dTag dedup) | MED | Cross-device drafts. Orthogonal to scheduling. |

Rationale (simplicity review): every HIGH risk in this plan lives in the OS/amy
layer. Shipping PR1 first gets ~90% of the value at ~10% of reviewer/maintainer cost —
which matters doubly for a first upstream FOSS PR.

### CRITICAL / HIGH findings to fix before/within Phase 0

- **Cross-process store race (arch C1 / sec H3).** `ScheduledPostStore.claimDuePosts()`
  guards with an **in-process `Mutex` only** — zero cross-process mutual exclusion.
  Two JVMs (app timer + OS-fired publisher) can both claim the same PENDING rows →
  **duplicate publish** or lost status write. Resolution options, best-first:
  1. **Single-writer discipline (recommended for PR2):** OS trigger launches the
     desktop binary's headless publish mode; a lockfile/heartbeat ensures the GUI
     timer and the headless drain never run concurrently. Removes the race by
     construction (only one drainer alive at a time), same `~/.amethyst/` dir.
  2. **Switch the store to SQLite-WAL (cross-process-store research):** the repo
     already runs SQLite cross-process-correctly (`SQLiteEventStore`, WAL +
     `busy_timeout`, `BundledSQLiteDriver`, no JNI). `claimDuePosts` becomes one
     `BEGIN IMMEDIATE; UPDATE … WHERE status='PENDING' AND publish_at<=?` — true ACID,
     lease recovery is a one-line `WHERE`. **Use `PRAGMA synchronous=NORMAL`, NOT the
     `OFF` that `SQLiteEventStore` uses** (a lost SENT row = double-publish). This is
     the robust option if concurrent drain must be allowed.
  3. File lock (`FileChannel.lock()` on a sibling `.lock`, held across claim+persist,
     both paths) — correct but fragile (per-JVM semantics, NFS, stale locks). Least
     preferred.
- **Stuck `PUBLISHING` → silent permanent drop (arch C2 / sec H4).** There is **no
  timeout auto-recovery** today; `releaseClaim` only fires when the account isn't
  loaded. A crash/sleep between claim and ack strands the row in `PUBLISHING` forever
  (never re-claimed, never purged, never surfaced) — the worst outcome for a
  scheduling feature. **Fix in Phase 0 (benefits Android too):** on load/claim, revert
  any `PUBLISHING` row with `now - lastAttemptAtSec > CLAIM_TTL` (e.g. 10 min) to
  `PENDING`. Add a test. This makes Open Question #3's assumed recovery real.
- **Account-scoped claim (amy-expert).** Add `claimDuePosts(nowSec, accountPubkey)` (or
  filter in the publisher). Otherwise the single multi-account file lets the
  headless/amy path publish account B's pre-signed event using account A's outbox.

### KMP placement — corrected (kotlin-multiplatform skill, verified against `verifyKmpPurity`)

Jackson and `java.io.File` are **forbidden in `commonMain`** by the live purity gate
(`commons/build.gradle.kts:223-232`). Jackson reaches `commons` only transitively via
quartz's `jvmAndroid` `api`. Corrected layout:

```
commons/src/commonMain/.../scheduledposts/
    ScheduledPost.kt          (model + enum + file DTO)          — PURE, commonMain OK
    ScheduleTimePresets.kt    (roundUpToNextQuarterHour, presets) — PURE (use TimeUtils.now, NOT System.currentTimeMillis)
commons/src/jvmAndroid/.../scheduledposts/
    ScheduledPostStore.kt     (Jackson + File + Mutex + StateFlow) — inject the File, NO expect/actual
    ScheduledPostPublisher.kt (drain → INostrClient.publishAndConfirmDetailed)
    ScheduledPostNotifier.kt  (expect class — declared in jvmAndroid so iOS needs no stub)
    OsScheduler.kt            (expect class — declared in jvmAndroid)
commons/src/androidMain/.../scheduledposts/  ScheduledPostNotifier.kt (WorkManager), OsScheduler.kt
commons/src/jvmMain/.../scheduledposts/      ScheduledPostNotifier.kt (tray/log), OsScheduler.kt (launchd/schtasks/systemd)
```

- Store + publisher live in **`jvmAndroid`** (both JVM Desktop & Android share it
  verbatim). Do **not** abstract the file path behind expect/actual — inject the
  resolved `File` (over-abstraction). No `iosMain` files needed for any scheduling
  artifact.
- **Sequence the Android refactor (arch M4):** 0a pure move + re-point Android (tests
  green) → 0b swap `waitForOk`→`publishAndConfirmDetailed` (re-verify "any relay
  acked = SENT") → 0c add claim-staleness sweep + test. Keep commits bisectable.
- **Kill the `waitForOk` extraction (arch H3):** both platforms call the quartz
  `publishAndConfirmDetailed` primitive; don't port Android's `pendingPublishRelaysFor`
  polling into commons.

### OsScheduler placement (arch H2)

`OsScheduler` writes plist/unit/task files and shells out — it must NOT sit in
`commons/jvmMain` if that would put OS-orchestration on amy's CLI-safe classpath. Per
the KMP finding, declare the `expect` in **`jvmAndroid`** (actual in `jvmMain` for
Desktop OS-switch, `androidMain` = WorkManager). The Desktop-only registration logic
itself may equally live in `desktopApp/jvmMain` if it consumes only Desktop types —
decide by whether Android reuses the abstraction (it has WorkManager, so a `desktopApp`
home is also defensible). Either way: **not commonMain, no iOS stub.**

### Relay re-resolution — fix the story (arch H1)

A fresh `~/.amy/` has no kind:10002 for the account, so the amy path's
`ctx.outboxRelays()` re-resolution **never fires** for the app-closed case — it always
falls back to the stored snapshot. Better: **the app refreshes the stored `relayUrls`
snapshot whenever its outbox changes** (cheap, app-side, always current), so whatever
the headless/amy path reads is fresh. With the thin-trigger design (headless mode of
the desktop binary) this is moot — it reads the app's own live NIP-65 state.

### Compose picker (research) — no custom picker needed

Material3 `DatePicker`/`TimePicker` work on Compose Desktop (CMP 1.11.0, material3
1.9.0). Adapt Android's `ScheduleAtPicker` inside a `Dialog` (or `DialogWindow` for a
roomier modal) — **not `AlertDialog`** (project memory: subscriptions/state issues in
AlertDialog on Desktop). Reuse `rememberDatePickerState`/`rememberTimePickerState`,
keep the timezone + rounding logic verbatim. Split the matrix row: pure time utils =
clean 📦 extract; picker composable = adapt-to-Desktop (verify parity), closer to 🆕.

### Security hardening (security review) — fold into acceptance criteria

- Store file + any scheduler files: **`0600`**; refuse to write if the parent dir is
  group/world-writable. (`DesktopDraftStore` sets 0600 — copy it; `DesktopHighlightStore`
  sets none — do NOT copy that.)
- OS trigger command: **absolute, canonicalized path inside the app bundle**, never a
  bare name / `PATH` lookup (else a poisoned `~/.local/bin/amy` = durable user-level
  RCE on a 5-min timer). Verify the path is non-writable by others before emitting it.
- Generate plist/unit/task via **strict escaping / argv arrays, never `sh -c` string
  concat**; reject control chars / newlines in any interpolated path (injection).
  Reuse the repo's existing argv-array subprocess pattern (`security`/`secret-tool`/
  `gsettings`).
- **Key-free path must read zero secrets:** amy's `Context` eagerly builds a signer
  from the keychain today — the publish path must use a key-free `Context`/`Identity`
  variant that never calls `keyPair().privKey`/`secrets.resolve`. Add a criterion:
  "publish path performs zero keychain reads."
- Both drain paths **re-verify the signature** of the deserialized `signedEventJson`
  and assert `event.pubKey == accountPubkey` before broadcast (tamper defense).
- NIP-37 sync: keep **default OFF**; document that kind 31234 leaks *that a draft of
  kind N exists at time T for pubkey P* (content stays NIP-44 encrypted to self); 90-day
  NIP-40 expiry is advisory only.

## Technical Approach

### Architecture

```
Compose (Desktop, ComposeNoteDialog)        commons (shared)                     OS
  ClockButton → ScheduleAtPicker  ─┐
  pre-sign via account.signer      ├─► ScheduledPostStore ───┐  register/cancel
  (block if bunker offline)        │     (commonMain: JSON,   ├─► OsScheduler (expect/actual)
  store.add(signedEventJson)      ─┘     Mutex, claimDuePosts)│     launchd / schtasks / systemd
                                              ▲   │            │
  In-app timer (app open) ────────────────────┘   │            ▼ every ~5 min WHEN queue>0
    ScheduledPostPublisher.drain()                │      amy publish-scheduled (cli, key-free)
    re-resolve outbox relays                      │        drain due → publishAndConfirmDetailed
  "Drafts & Scheduled" screen ────────────────────┘        → markSent / markFailed
    Drafts tab (local + NIP-37, synced badge)
    Scheduled tab (status + edit/cancel/publish-now)
```

Store path (shared by Desktop app AND `amy`):
`~/.amethyst/scheduled/scheduled.json` (per-account keyed by `accountPubkey` field;
one file, filtered by account — mirrors Android's single-file store).

> **Data-dir mismatch (must reconcile):** `amy` resolves its own data dir as
> `~/.amy/<account>/…` with its own `SecretStore`, independent of the desktop
> app's `~/.amethyst/`. So the OS job must point `amy` at the shared scheduled
> file explicitly — either a new `--scheduled-file PATH` flag on
> `publish-scheduled`, or a convention both agree on. `amy` does **not** need the
> desktop's keys (posts are pre-signed), but it does need read/write access to the
> shared scheduled store. Decide the mechanism in Phase 3.

### Implementation Phases

#### Phase 0 — Extract shared scheduling core to `commons/`
- Move `ScheduledPost.kt` (model + enum + `ScheduledPostFile`) to
  `commons/commonMain/.../scheduledposts/`. PURE — no changes.
- Move `ScheduledPostStore.kt` to commons. Replace `java.io.File` constructor arg
  with a KMP-friendly path/IO abstraction:
  - `expect` a storage-path/file-writer, `actual` for `jvmAndroid` (both JVM &
    Android are JVM → likely one `jvmAndroid` actual using `java.io.File`, plus
    iOS stub if needed). Keep injected `nowSec: () -> Long`.
- Extract the drain/publish/`waitForOk` loop from `ScheduledPostWorker` into a pure
  `ScheduledPostPublisher` (commons):
  ```kotlin
  // commons/commonMain/.../scheduledposts/ScheduledPostPublisher.kt
  class ScheduledPostPublisher(
      private val store: ScheduledPostStore,
      private val client: INostrClient,
      private val resolveRelays: (post: ScheduledPost) -> Set<NormalizedRelayUrl>,
  ) {
      suspend fun drainDue(nowSec: Long): DrainReport { /* claim → publishAndConfirmDetailed → markSent/markFailed */ }
  }
  ```
  Use quartz `INostrClient.publishAndConfirmDetailed()` instead of Android's
  hand-rolled `pendingPublishRelaysFor` poll.
- Extract `roundUpToNextQuarterHour` + preset generators into a commons util.
- **Refactor Android** `ScheduledPostWorker`/ViewModel to call the commons store +
  publisher (no behavior change; Android tests stay green).
- `expect/actual` notifier interface: `ScheduledPostNotifier` (Android = current
  impl; Desktop = tray notification or log).

**Success:** Android build + existing Android scheduling tests green against the
extracted commons code. `:commons:jvmTest` + `:commons:compileKotlinJvm` pass.

#### Phase 1 — Desktop store + composer scheduling
- `DesktopScheduledPostStore` wiring: instantiate the commons `ScheduledPostStore`
  with the Desktop path (`~/.amethyst/scheduled/scheduled.json`), provide as a
  CompositionLocal / app-level singleton in `Main.kt` (mirror `DesktopDraftStore`).
- Add clock icon to `ComposeNoteDialog` toolbar row (near `MediaAttachmentRow`);
  `MaterialSymbols.Schedule` (already referenced? verify; regenerate subset font if
  it's a new codepoint — see CLAUDE.md Icons rule).
- Add `scheduledForSec: Long?` state + `ScheduleAtPicker` (extracted) in the dialog.
- On Publish when `scheduledForSec != null`:
  - Build the template, **re-stamp `createdAt = scheduledForSec`** (feed ordering),
    `account.signer.sign(template)`.
  - **Bunker offline → block**: if signer is remote (NIP-46) and signing fails/times
    out, show an inline message ("Signer must be online to schedule") and abort —
    do NOT store. (see brainstorm: Resolved / bunker offline)
  - `store.add(ScheduledPost(... signedEventJson, relayUrls = outbox snapshot ...))`.
- No draft deletion coupling yet (Desktop composer draft autosave handled in Phase 4).

**Success:** Scheduling a note writes a `PENDING` row; app compiles
(`:desktopApp:compileKotlin`), spotless clean.

#### Phase 2 — In-app timer publisher
- App-level coroutine (in `Main.kt` scope) runs `ScheduledPostPublisher.drainDue()`
  on a ticker (e.g. every 30–60s) while the app is open.
- `resolveRelays` for the in-app path = **re-resolve** `iAccount.nip65RelayList.outboxFlow.value`
  for the post's account, falling back to the stored `relayUrls` snapshot if empty.
- On launch, run one **catch-up** drain → overdue posts auto-publish. (see brainstorm §4)

**Success:** A post scheduled for ~1 min out publishes while the app is open;
overdue post publishes on relaunch.

#### Phase 3 — OS-level scheduler + `amy publish-scheduled`
- `amy publish-scheduled` subcommand (`cli/.../commands/`, wired into `Main.kt`
  `when(head)` dispatch): resolves the shared store path, `drainDue(now)`, publishes
  via `publishAndConfirmDetailed`, marks status, exits 0/1. Honors `--json`.
  - Key-free: reads `signedEventJson` — no signer needed. Uses `Context.publish()`
    → `publishAndConfirmDetailed` (accepts pre-signed events).
  - Relay resolution for the amy path: prefer amy's own `ctx.outboxRelays()` (reads
    kind:10002 from its store, or a fresh network fetch) to honor "re-resolve at
    publish"; fall back to the stored `relayUrls` snapshot if amy has no synced
    kind:10002 for that account. (see Risks — divergence is now bounded, not total.)
- `expect/actual OsScheduler` (commons `jvmMain` or desktopApp):
  - `fun ensureRegistered()` / `fun unregister()` — idempotent.
  - **macOS**: write a `launchd` `~/Library/LaunchAgents/com.vitorpamplona.amethyst.scheduledposts.plist`
    with `StartInterval` ~300s calling the bundled `amy publish-scheduled`; `launchctl load/unload`.
  - **Windows**: `schtasks /create /sc minute /mo 5 …` invoking `amy.bat publish-scheduled`; `/delete` on unregister.
  - **Linux**: `systemd --user` timer (`.timer` + `.service`) or crontab fallback.
- **Lifecycle management**: observe `store.flow`; when pending count goes 0→>0 call
  `ensureRegistered()`, when it drains to 0 call `unregister()`. (see brainstorm §1
  user refinement)
- **Bundle `amy` with the desktop distribution** so the OS job has a stable path.
  `amy` is a Gradle `application` (launcher `amy`/`amy.bat`, mainClass `…cli.MainKt`)
  with its own jlink+jpackage bundle. Options (decide in impl): (a) add `:cli` to the
  desktop jpackage image and resolve the launcher path at runtime, or (b) ship amy's
  jlink image inside the desktop app resources. Path must survive app updates.

**Success:** With the app closed, a due post publishes via the OS job on macOS
(dogfood platform); registration appears/disappears with queue transitions.

#### Phase 4 — "Drafts & Scheduled" unified screen + draft sync
- Nav: extend `DeckColumnType` — rename `Drafts` destination presentation to
  "Drafts & Scheduled" (keep `Drafts` object; add `ScheduledPosts` OR make the
  existing Drafts screen a two-tab host). One sidebar entry, two tabs (Drafts |
  Scheduled). (see brainstorm §7)
- **Scheduled tab**: list from `store.flow.listFor(account)`; each row = content
  preview + scheduled time + status chip (PENDING/PUBLISHING/SENT/FAILED). Actions:
  - **Publish now** → `store.publishNow(id)` (+ trigger drain).
  - **Cancel** → `store.cancel(id)`.
  - **Edit** → cancel + reopen content in `ComposeNoteDialog` as a draft to
    re-schedule. (see brainstorm §8)
  - FAILED rows show `lastError` + attemptCount and a retry (= publishNow).
- **Drafts tab**: reconcile with existing `DesktopDraftStore`. Add composer
  "Save as draft" for short notes; **opt-in NIP-37 sync** toggle publishes the draft
  as `DraftWrapEvent` (kind 31234). One list; synced rows get a small cloud badge;
  same `dTag` dedups local+synced. (see brainstorm §5 / Resolved)

**Success:** Screen lists both; all actions work; synced badge shows for NIP-37
drafts.

#### Phase 5 — Retry, cleanup, notifications, tests, docs
- Retry/backoff policy (reuse Android `attemptCount`); retention purge (SENT >7d,
  CANCELLED >30d, FAILED kept) already in the store — verify on Desktop.
- Desktop notifier `actual`: system tray / OS notification on SENT/FAILED (optional;
  can stub+log for v1).
- Tests: commons unit tests for store state machine, `claimDuePosts` atomicity,
  publisher drain, preset/rounding utils. Manual testing sheet.
- Docs: update `desktopApp` plans; note the OS-job files created per platform.

## Alternative Approaches Considered

- **One OS job per post** (brainstorm Approach B): precise but create/cancel churn
  and orphan risk. Rejected for lifecycle-managed single job.
- **In-app-only + catch-up** (brainstorm Approach A-lite): simplest but never fires
  when the app is closed. Rejected — user explicitly wants app-closed firing.
- **Sign at publish time**: would force the OS job / amy to hold keys. Rejected —
  breaks the key-free guarantee. (see brainstorm §3)

## System-Wide Impact

### Interaction Graph
`ComposeNoteDialog.Publish(scheduled)` → `signer.sign` → `store.add` → `store.flow`
emits → lifecycle observer → `OsScheduler.ensureRegistered()`. At fire time: OS job
→ `amy publish-scheduled` → `store.claimDuePosts` (flips PENDING→PUBLISHING) →
`publishAndConfirmDetailed` → `markSent/markFailed` → `store.flow` emits → if queue
now empty → `OsScheduler.unregister()`. Parallel in-app timer path claims the same
rows via the same atomic `claimDuePosts`, so only one path publishes each post.

### Error & Failure Propagation
- Sign failure at schedule (bunker offline) → surfaced inline, nothing stored.
- Publish failure at fire time → `markFailed(id, error)`, row stays FAILED with
  `lastError`; retryable from the Scheduled tab.
- Two-process write race (app + amy) → store must be safe across processes, not just
  coroutines (see Risks).

### State Lifecycle Risks
- A post claimed as PUBLISHING by amy, then amy crashes → `releaseClaim`/timeout must
  return it to PENDING so it isn't stuck. Verify the store's claim has a recovery path.
- OS job registered but queue emptied by the in-app path → observer must still
  `unregister()` (don't leak launchd/schtasks/systemd entries).

### API Surface Parity
- Android composer path and Desktop composer path must produce identical
  `ScheduledPost` rows (same re-stamp + relay snapshot semantics).
- `amy publish-scheduled` and the in-app timer must share `ScheduledPostPublisher`.

### Integration Test Scenarios
1. Schedule → close app → OS job fires → note appears at scheduled `created_at`.
2. Schedule → keep app open → in-app timer fires before OS job; amy later finds
   nothing due (claim already consumed).
3. Queue 0→1→0 registers then unregisters the OS job (inspect launchd/schtasks/systemd).
4. Overdue on relaunch auto-publishes.
5. Bunker offline at schedule → blocked, no row written.
6. Change write relays after scheduling → in-app path uses new outbox; amy path uses
   stored snapshot (documented divergence).

## Acceptance Criteria

### Functional
- [ ] Clock icon in Desktop composer opens `ScheduleAtPicker` with presets.
- [ ] Scheduling pre-signs and stores a `PENDING` row; blocks if bunker offline.
- [ ] In-app timer publishes due posts while app is open; catch-up on launch
      auto-publishes overdue posts.
- [ ] OS job registered on queue 0→1, cancelled on →0 (macOS/Windows/Linux).
- [ ] `amy publish-scheduled` drains + publishes pre-signed posts key-free (text +
      `--json`, exit 0/1/2).
- [ ] "Drafts & Scheduled" sidebar screen with two tabs; Scheduled rows show
      status + edit(cancel+recompose)/cancel/publish-now.
- [ ] Drafts tab shows local + opt-in NIP-37 synced drafts with a synced badge.

### Non-Functional
- [ ] Publisher never accesses signing keys; **publish path performs zero keychain reads**.
- [ ] Store is safe against concurrent drains — no duplicate publish, no lost row
      (test: concurrent double-drain → single publish).
- [ ] Stuck `PUBLISHING` rows auto-recover after `CLAIM_TTL` (test: crash-mid-publish → row returns to PENDING).
- [ ] `claimDuePosts` is account-scoped; a row publishes only under its own `accountPubkey`.
- [ ] Both drain paths re-verify signature + `event.pubKey == accountPubkey` before broadcast.
- [ ] Store file + scheduler files are `0600`; write refused if parent dir is group/world-writable.
- [ ] OS trigger uses an absolute, canonical, non-other-writable path (no `PATH` lookup); scheduler files built via argv-array/strict escaping, never `sh -c`.
- [ ] No orphaned OS-scheduler entries after queue drains / logout; registration reconciled idempotently on startup (level-triggered, not only Flow-edge-triggered).
- [ ] Future-`created_at` relay rejection handled as FAILED with a clear error; stale-overdue (> 24h) auto-publish is bounded, not silent.

### Quality Gates
- [ ] `:commons:jvmTest`, `:desktopApp:compileKotlin`, Android build green.
- [ ] `./gradlew spotlessApply` clean.
- [ ] Manual testing sheet executed (Desktop macOS at minimum).

## Success Metrics
- A note scheduled with the app closed publishes within one OS-tick (~5 min) of its
  time. Zero duplicate publishes across in-app + OS paths. Zero leaked OS jobs.

## Dependencies & Risks
- **Two-process store safety (HIGH):** Android's `ScheduledPostStore` uses an
  in-process `Mutex` + atomic file rename. With amy and the app both writing, need
  cross-process safety (file lock, or amy-only-writes-when-app-absent, or a lock
  file). Must resolve in Phase 3.
- **amy bundling/path (HIGH):** OS job needs a stable amy executable path surviving
  updates. Ties into desktop jpackage config.
- **Data-dir reconciliation (HIGH):** amy's `~/.amy/<account>/` ≠ desktop's
  `~/.amethyst/`. OS job must point amy at the shared scheduled file (new flag or
  convention). See Architecture note.
- **Relay re-resolution divergence (MED):** in-app path re-resolves via
  `nip65RelayList.outboxFlow`; amy path re-resolves via `ctx.outboxRelays()` (needs
  synced kind:10002) else falls back to snapshot. Bounded divergence, acceptable v1.
- **Compose Multiplatform Material3 pickers (MED):** `DatePicker`/`TimePicker` on
  Desktop — verify parity or build a custom picker.
- **OS integration fragility (MED):** launchd/schtasks/systemd differences; macOS
  is the dogfood target, others need testing.
- **Icon subset font (LOW):** new `MaterialSymbols.Schedule` codepoint requires
  regenerating the subset font (CLAUDE.md rule).

## Open Questions (resolve during implementation)
1. **PR2 mechanism (decide before PR2):** thin-trigger launching a headless mode of the
   desktop binary (recommended — single-writer, same dir) vs separate bundled `amy` vs
   SQLite-WAL store enabling safe concurrent drain. Deepen-plan recommends thin-trigger;
   confirm before building PR2.
2. **Store engine:** keep JSON (single-writer discipline) or migrate to SQLite-WAL
   (true cross-process ACID, repo already uses it)? Ties to Q1.
3. ~~`claimDuePosts` stuck-PUBLISHING recovery~~ — **RESOLVED: no recovery exists today;
   Phase 0 adds a `CLAIM_TTL` staleness sweep + test.**
4. **Screen shape (simplicity):** a standalone `Scheduled` destination beside the
   existing `Drafts` one is cleaner for PR1 than refactoring the working Drafts screen
   into a two-tab host. Revisit the two-tab decision — brainstorm said one screen/two
   tabs, but a sibling destination may ship faster. Confirm with user.
5. Desktop notifier: system tray vs log-only for v1 (log-only acceptable).
6. Time zone / DST — keep all storage/firing in epoch seconds (already the case);
   confine TZ logic to the picker's presentation layer.
7. Filename: unify on Android's existing `scheduled_posts.json` (not `scheduled.json`).

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-07-09-feat-desktop-note-scheduling-brainstorm.md](../brainstorms/2026-07-09-feat-desktop-note-scheduling-brainstorm.md)
  — carried forward: OS-level lifecycle-managed publishing, pre-sign/key-free
  publisher, local+opt-in-NIP-37 drafts, one-screen-two-tabs, cancel+recompose edit.

### Internal References
- Desktop composer: `desktopApp/.../ui/ComposeNoteDialog.kt` (`publishNote`, ~636–673)
- Desktop nav: `desktopApp/.../ui/deck/DeckColumnType.kt`, `DeckSidebar.kt` (`NAV_ITEMS`)
- Desktop publish + write relays: `desktopApp/.../network/RelayConnectionManager.kt`,
  `desktopApp/.../model/DesktopIAccount.kt` (`nip65RelayList.outboxFlow`)
- Desktop store pattern: `desktopApp/.../service/drafts/DesktopDraftStore.kt`,
  `.../highlights/DesktopHighlightStore.kt`
- Android scheduling: `amethyst/.../service/scheduledposts/{ScheduledPost,ScheduledPostStore,ScheduledPostWorker,ScheduledPostNotifier}.kt`
- Android composer scheduling block: `amethyst/.../home/ShortNotePostViewModel.kt` `sendPostSync()` (~812–906)
- Scheduling UI: `amethyst/.../creators/scheduling/{ScheduleAtButton,ScheduleAtPicker}.kt`
- Publish primitive: `quartz/.../nip01Core/relay/client/accessories/NostrClientPublishExt.kt` (`publishAndConfirmDetailed`)
- NIP-37: `quartz/.../nip37Drafts/DraftWrapEvent.kt` (kind 31234)
- amy dispatch: `cli/src/main/kotlin/.../cli/Main.kt` (`dispatch` `when(head)`), `cli/build.gradle.kts` (application + jpackage)
