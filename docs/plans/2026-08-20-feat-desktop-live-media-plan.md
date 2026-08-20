---
title: Desktop Live Media (NIP-53) — Consume & Discover
type: feat
status: active
date: 2026-08-20
origin: docs/brainstorms/2026-08-20-feat-desktop-live-media-brainstorm.md
---

# ✨ Desktop Live Media (NIP-53) — Consume & Discover

## Enhancement Summary

**Deepened on:** 2026-08-20 (7 parallel research/review agents: performance, architecture,
simplicity, flow/edge-cases, security, HLS+chat best-practices, in-repo Compose patterns.)

### Key improvements folded in
1. **Player reality check (kdroidFilter 0.11.4):** no `isLive` flag, no buffered ranges, no
   stall detector (except Linux). `duration == 0.0` is the live signal **but is ambiguous with
   loading** → detect live/VOD from the **NIP-53 `status` tag** (+ manifest `#EXT-X-ENDLIST`
   for precise ended), and **build our own stall/reconnect watchdog**. `seekTo` is a hard no-op
   at `duration<=0`, so suppressing the seek bar for live is confirmed safe.
2. **Chat = one coalescing pipeline**, reusing the existing 250ms `BasicBundledInsert` feed path
   (do **not** hand-roll a chat list). Ingest→dedup-by-id→moderate→classify→batch→ring-trim(500)
   →immutable-commit→`LazyColumn(reverseLayout,key=id)`→auto-scroll(`derivedStateOf`+`yield`).
3. **Three factual corrections** to the original plan (see ⚠️ callouts): `OnlineChecker` is
   Android-only (must extract + swap `android.util.LruCache`); `DesktopLocalCache` has **no channel
   abstraction** (must stand up the first one); the watch-screen host I assumed (window-level
   overlay) **doesn't exist** — `DesktopScreen`/`navState` are per-column.
4. **Security:** URL-scheme allowlist gating both the player **and** the "Open in default player"
   fallback; probe-on-intent (not on discovery) via the proxy-aware client; verify participant
   `proof`; sanitize chat (bidi/auto-load).
5. **Performance:** the player singleton has a **real unsynchronized race** + 60/s position-tick
   recompose churn; Desktop has **no pruning** today (must add the trigger); snapshot **all**
   comparator keys (incl. volatile `current_participants`) to avoid TimSort violations.
6. **Scope correction:** the standalone "Lives" sidebar destination is **removed** — it
   contradicted the brainstorm decision to *fold lives into Discover*.

### New considerations discovered
- Mid-watch lifecycle (live→ended, URL rotation, stall) is the highest-risk cluster and is now
  first-class in Phase 0/3.
- Mute/block enforcement in live chat is a **known silent-no-op gap** on Desktop (memory
  `desktop_moderation_safety.md`) — must be the explicit enforcement point, not assumed.
- Stream-zap recipient semantics (single author vs NIP-53 zap splits) needs a decision.

---

## Overview

Bring NIP-53 Live Streaming to Amethyst **Desktop** as a **consumption + discovery** feature:
find who/what is live, open a stream, watch the HLS video, read + post live chat, and zap.
**Broadcasting is out of scope.**

Quartz has the full NIP-53 protocol; `commons/` has the `LiveActivitiesChannel` model +
top-zappers aggregation; Desktop ships an HLS-capable player (kdroidFilter, MIT); Android has a
complete consumer UX to copy. This plan is **wiring + Desktop-native layouts + closing real gaps**
(cache/channel routing for 30311/1311; live-vs-VOD player handling + stall recovery; online-probe
extraction; a full-window watch host).

Four surfaces (decided in the brainstorm):
1. **Per-column "live now" bar** — pinned above a feed column, scoped to that column's audience;
   one host (ranked by viewers) + `+N live ›`.
2. **Discover integration** — a Lives section + search folded into the existing Discover (no
   standalone sidebar destination).
3. **Full-window watch screen** — HLS player (left) + kind-1311 chat (right) + metadata/zap.
4. **Zaps** — zap the stream (30311 address) + chat messages (1311).

Statuses: **live + planned + ended (recording/VOD)**. Profile gets a **Streams tab**.

---

## Problem Statement / Motivation

Desktop has **zero** NIP-53 UI today while Android is mature and zap.stream has set the "live bar
+ watch-with-chat" expectation. Live streaming is high-engagement; Desktop's large screen suits a
side-by-side watch+chat layout mobile can't match. Clear parity gap with heavy reuse leverage.

---

## Proposed Solution (high level)

- **Reuse** Quartz protocol + `commons` model/aggregator/player/zap infra.
- **Extract pure ranking/status/freshness logic** to `commons/.../nip53LiveActivities/`
  (shared by Android + Desktop); keep each platform's filter thin over its own cache.
- **Close the gaps:** stand up Desktop's first channel cache + route 30311/1311; extract the
  online-probe to a shared source set; add a live-vs-VOD player mode + stall/reconnect watchdog;
  add a real full-window watch host.
- **Build Desktop-native** surfaces: per-column live bar, Discover Lives section + search,
  full-window watch screen, profile Streams tab, event-driven planned reminders.

---

## Technical Approach

### Architecture & reuse map

**Reuse as-is — Quartz (`quartz/.../nip53LiveActivities/`):** `streaming/LiveActivitiesEvent.kt`
(kind 30311; `checkStatus()` 8h live→ended; `LiveStreamLike`), `chat/LiveActivitiesChatMessageEvent.kt`
(kind 1311; builders `.message(content, hint)` / `.reply(content, replyingTo)`), tag parsers under
`streaming/tags/`.

**Reuse as-is — commons:** `model/nip53LiveActivities/LiveActivitiesChannel.kt` (CLI-safe: address,
creator, `info`, `presenceNotes`, `pruneStalePresence`), `nip53LiveActivities/LiveActivityTopZappersAggregator.kt`
+ `viewmodels/LiveStreamTopZappersViewModel.kt`, `nip53LiveActivities/ui/StreamSystemCard.kt`,
feed DAL (`ui/feeds/`), `viewmodels/FeedViewModel.kt` + `ListChangeFeedViewModel`.

**Extract Android → commons (pure logic only):** `convertStatusToOrder` + the
`compareBy(status, participantCount, allParticipants, startTime, idHex).reversed()` comparator from
`amethyst/.../discover/nip53LiveActivities/DiscoverLiveFeedFilter.kt:116-134` → new
`commons/.../nip53LiveActivities/LiveActivitySorting.kt`. Freshness cutoff + viewer/follow ranking
from `HomeLiveFilter.kt`. **Keep the `MeetingRoom`/`MeetingSpace` branches intact** (Android
audio-rooms callers depend on the same helper) or provide a `LiveActivitiesEvent`-only overload.
`HomeLiveFilter` (extends amethyst-only `AdditiveComplexFeedFilter<Channel,Note>`) is **not**
portable — extract only its helpers.

> ⚠️ **Correction 1 — `OnlineChecker` is an extraction, not a reuse.** It lives only in
> `amethyst/src/main/.../service/OnlineCheck.kt` and hard-depends on **`android.util.LruCache`**
> (won't compile in `jvmAndroid`). Task: **move it to `commons/jvmAndroid`, swapping `LruCache`
> for a KMP-safe bounded cache** (existing `LargeCache`/`KmpLock` primitives). It already exposes
> a `(String) -> OkHttpClient` factory + a **5-min TTL, 100-URL LRU** with same-URL dedup — reuse
> those. Confirm Desktop supplies an OkHttp client factory (route it through the **proxy-aware**
> `RoleBasedHttpClientBuilder.okHttpClientForVideo` — see Security). OkHttp is banned in
> `commonMain` (`verifyKmpPurity`), so `jvmAndroid` is the correct home.

### Player: live-vs-VOD + stall recovery (kdroidFilter 0.11.4 reality)

The engine collapses every backend's live signal into `duration == 0.0` and exposes **no** live
flag / buffered ranges / (Win+Mac) stall detector. Therefore:

- **Classify live/VOD from NIP-53, not the player.** Use the 30311 `status` tag as source of truth
  (`live`/`planned`/`ended`). Use `duration == 0.0` only as a corroborating hint (it's ambiguous
  with "still loading"). For precise *ended* detection, optionally fetch the `.m3u8` and check
  `#EXT-X-ENDLIST` (RFC 8216) — resolve a variant first if it's a master playlist.
- **Live mode UI:** suppress the seek `Slider` (extend `VideoControls`' existing `viewMode` param;
  `seekTo` is inert at `duration<=0` so this is safe), show a **"● LIVE" pill** (with hysteresis),
  no `current/duration` row. "Jump to live" = `stop()` + `openUri(url)` (re-open lands at the edge).
  Read viewer count from 30311 `current_participants`, not client-side.
- **Own stall watchdog (mandatory Win/Mac):** sample `currentTime` ~every 1s; if `isPlaying` but no
  advance for ~1.25–2s → stalled. OR it with Linux's `isLoading`. Reconnect via `stop()`+`openUri`
  with bounded exponential backoff + jitter (~1s→cap ~8–30s). **Do not trust** `onPlaybackEnded` /
  AVFoundation's `DidPlayToEndTime` for "ended" (false-fires on live). Terminal "Stream ended/offline"
  card only on an authoritative signal (`status=ended`, stale-`live` fallback, or `#EXT-X-ENDLIST`).
- **VOD path** (ended streams' `recording`) keeps the existing seekable controls unchanged — the
  *easy* path.

> ⚠️ **Player singleton is a real latent bug.** `GlobalMediaPlayer` is an `object`; `playVideo`/
> `seek`/`volume`/`toggle` are **not synchronized** (only engine init is). Two callers (NowPlayingBar
> feed video + LiveWatch) racing `playVideo(urlA/urlB)` do unguarded RMW on the same engine → torn
> state. **Serialize player mutations behind a `Mutex`/`initLock`.** Also: `videoState` updates
> ~60×/sec (position) and callers `collectAsState()` the whole struct → 60/s recompose churn.
> **Split `position` out** (pass `() -> Float` / a separate `StateFlow<Float>` to the slider); in
> **live mode read no position at all** (seek bar suppressed) → zero churn on the common path.
> Watch-close `DisposableEffect` must stop playback, cancel the sync job, and reset
> `MediaPlaybackState` so stale duration/position doesn't leak into a later VOD/feed video.

### Data flow: channel cache + routing (the other gap)

> ⚠️ **Correction 2 — Desktop has no channel abstraction.** `DesktopLocalCache.getAnyChannel()`
> returns `null` ("Desktop doesn't support channels yet"). Routing 1311 requires **standing up the
> first Desktop channel cache**, not just two `when` cases:
> - Add `DesktopLocalCache.liveChatChannels = LargeCache<Address, LiveActivitiesChannel>` +
>   `getOrCreateLiveChannel(address)` (mirror Android; keep it **live-activity-only**, don't port
>   the 7-type Android abstraction).
> - **30311** → `AddressableNote` in `addressableNotes` with `existing.createdAt >= event.createdAt`
>   supersession (replaceable ⇒ one entry/stream; bounded, safe). Attach `info` to the channel.
> - **1311** → attach to its stream's channel `notes` by root `a` tag.
> - **`LocalRelayStore` hydration policy** (Desktop-only write-through SQLite Android lacks):
>   **hydrate 30311** (replaceable, bounded), **skip 1311** (avoid unbounded chat replay).

> ⚠️ **Desktop has no pruning today.** `pruneOldMessages()` (caps `channel.notes` to 500 newest,
> emits `SetDeletion`) exists on the model but **nothing calls it on Desktop** (no `CachePruner`/
> `MemoryTrimmingService`). Add a trigger: prune when the 1311 bundler tick sees `notes.size > 500`;
> `pruneStalePresence(now - 20min)` for presence; **evict the channel's `notes` on watch-close** so
> watching 5 busy streams doesn't retain 5×∞.

### Chat: one coalescing pipeline (reuse, don't hand-roll)

Route 1311 through the **existing** `ListChangeFeedViewModel` → `FeedContentState` path (same as
Android's `ChannelFeedViewModel`/`ChannelFeedFilter`). The **250ms `BasicBundledInsert`** already
coalesces hundreds of arrivals into ~4 list emissions/sec; stable `key = { it.idHex }`; list capped
at `limit()=500`. The pipeline order: **ingest → dedup by event id (multi-relay dups) → moderate
(mute/block + spam) → classify (message vs zap tier) → batch (250ms; bump to 300–500ms if jank) →
ring-trim (500, only while at bottom) → commit one immutable list → `LazyColumn(reverseLayout=true,
key=id, @Immutable rows)` → auto-scroll**.
- Auto-scroll: stick to bottom only while at/near bottom (`derivedStateOf` on
  `firstVisibleItemIndex==0 && offset<threshold`); floating "↓ N new" pill when scrolled up;
  **`yield()` before `animateScrollToItem(0)`** (layout-race fix). Desktop: **hover-to-pause**
  auto-scroll (mouse-first).
- Reuse the existing desktop `ChatPane.kt` (already `reverseLayout` + auto-scroll) as the template.
- **Note:** `DesktopCacheEventStream` is `DROP_OLDEST` (buffer 64) → chat may gap under sustained
  burst (acceptable for chat) **but the 30311 metadata refresh must not share that lossy stream**
  (never drop a status→live/ended transition).

### Pinned chat + inline zaps
- **Pinned** messages come from 30311 `["pinned", "<1311 id>"]` tags (host-controlled, we're already
  subscribed to 30311) → resolve to the 1311 and render a sticky banner above the list. *(Nice-to-have;
  keep if cheap.)*
- **Zaps inline**: model rows as a sealed `ChatRow.Message | ChatRow.Zap(amountSats, zapper, comment)`;
  amount from the 9735 receipt's bolt11; amount-based visual tier. Same id-dedup as messages.

### Watch-screen host

> ⚠️ **Correction 3 — there is no window-level overlay host.** `DesktopScreen`/`navState`/
> `OverlayContent` are **per-column** (`navState = remember(column.id)`), so in **deck mode** a push
> lands in a ~400px column — exactly the side-by-side-impossible layout the brainstorm rejected.
> Only **single-pane** mode's overlay `fillMaxSize()`. **Decision needed (blocks Phase 3):**
> - **(a) ✅ CHOSEN** — Add a genuine window-level overlay host at the `App()`/composition root in
>   `Main.kt` (above both `DeckColumnContainer` and `SinglePaneLayout`), app-scoped nav state. The
>   watch screen spans the whole window (player left / chat right). This is the first true full-window
>   route — new app-scoped nav state, **not** the per-column `ColumnNavigationState`.
> - (b) Separate top-level Compose `Window` — not chosen.
> - (c) Single-pane overlay + max-width column — not chosen.

### State-holder placement
Per `commons/ARCHITECTURE.md`: the watch/chat **state holder** (chat list assembly, status,
zapper wiring) goes in **`commons/viewmodels`** (reuse `LiveStreamTopZappersViewModel`); only the
Compose screen + subscription wiring live in `desktopApp`. `LiveActivitySorting.kt` (CLI-safe pure
logic) sits beside `LiveActivityTopZappersAggregator.kt` in `commons/.../nip53LiveActivities/`.

### Sort stability
`LiveActivitySorting` must take a **pre-snapshotted** `Map<Note,Int>` of comparator keys (status
**and** the volatile `current_participants` — the desktop bar sorts by it, more exposed than
Android's home bar) — never a live accessor inside the comparator (else `"Comparison method
violates its general contract"`). Unit-test with concurrent mutation during sort.

### Integration points (verified file:line)

| Surface | File:location | Change |
|---|---|---|
| Column type | `desktopApp/.../ui/deck/DeckColumnType.kt:25-145` | *(no new Lives destination — folded into Discover)* |
| Per-column live bar | `desktopApp/.../ui/FeedScreen.kt` (above feed `LazyColumn`; `feedMode` @~649) | insert `LiveNowBar(scope=feedMode)` pinned header (Following/Global; see scope note) |
| Discover Lives + search | `desktopApp/.../followpacks/ui/DiscoverScreen.kt:79-265` | add Lives section + client-side search box |
| Profile Streams tab | `desktopApp/.../ui/UserProfileScreen.kt` (tabs ~1191, `when` ~1249) | add "Streams" tab #11 |
| Watch host | `Main.kt` App root / new window | per Correction 3 |
| Cache routing | `desktopApp/.../cache/DesktopLocalCache.kt` `route()` | 30311/1311 + `liveChatChannels` + prune trigger |
| Player | `desktopApp/.../ui/media/{DesktopVideoPlayer,VideoControls}.kt`, `service/media/GlobalMediaPlayer.kt` | live mode + watchdog + `Mutex` + position split |
| Zap | `desktopApp/.../ui/NoteActions.kt` (`zapNote`, `ZapAmountDialog`) | target 30311 addr + 1311 |
| Subscriptions | `desktopApp/.../subscriptions/SubscriptionUtils.kt` (`rememberSubscription`) | `{kinds:[30311]}` discovery, `{kinds:[1311],#a}` watch |
| Online probe | extract → `commons/jvmAndroid` | per Correction 1 |

### Implementation Phases

#### Phase 0 — De-risking spikes (do first; some are now blocking)
- **Live HLS spike:** point `DesktopVideoPlayer` at a real live zap.stream `.m3u8`; confirm
  `duration==0.0` on live, seek no-op, error surface on a dead stream; prototype the stall watchdog
  + `stop()`+`openUri` reconnect. Validate on macOS at minimum; note Win/Mac lack a native stall flag.
- **Watch host decision** (Correction 3): choose (a)/(b)/(c) — **blocks Phase 3**.
- **`OnlineChecker` extraction** (Correction 1): move to `commons/jvmAndroid`, swap `LruCache`,
  confirm Desktop OkHttp factory + proxy-aware routing.
- **Deliverable:** spike note in `desktopApp/plans/`.

#### Phase 1 — Data layer (commons + desktop cache)
- `commons/.../nip53LiveActivities/LiveActivitySorting.kt` — pure status-order + freshness +
  viewer/follow ranking, **snapshot-map API** (+ unit tests incl. overdue-planned, zombie-live→ended,
  latest-per-address dedupe, host-I-follow vs participant, concurrent-mutation). Refactor Android
  filters to call it (hard AC — prevents drift).
- `DesktopLocalCache`: `liveChatChannels` + `getOrCreateLiveChannel`; route 30311 (supersession) +
  1311 (attach); prune trigger (cap 500 + `pruneStalePresence(20min)`); `LocalRelayStore` hydration
  (30311 yes, 1311 no).
- Desktop filters: `DesktopLiveActivityDiscoverFilter` (status LIVE>PLANNED>ENDED + online-downgrade +
  viewer sort) and `DesktopLiveNowFilter` (follows + 15-min fresh + rank by `current_participants`).

#### Phase 2 — Discovery + per-column live bar
- `LiveNowBar` composable: single host (ranked by `current_participants`, snapshotted) + `+N live ›`;
  scoped by column `feedMode`; **online-gated (one probe loop, visible-only, reuse 5-min LRU)**;
  immutable `StateFlow` + `distinctUntilChanged` (no recompose when ranking unchanged); pinned above
  the feed `LazyColumn`. **Scope: Following + Global columns** (see note); generalize later.
- Discover Lives section + client-side search (title/host/hashtag over cached 30311s) — grid of
  live + planned + VOD cards with `LiveFlag`/`ScheduledFlag`/"starts in…" badges. No standalone
  sidebar destination.

#### Phase 3 — Watch screen (player + chat + zap)
- `LiveWatchScreen(address)` in the chosen host: player (live mode, watchdog, no seek bar) left;
  chat pipeline right (read/post via `.message`/`.reply`); header (title, host, participant roles,
  viewer count, status badge, live metadata bound to StateFlow). Online-probe before mount → offline/
  ended placeholder (play `recording` VOD if present). Mid-watch transitions: live→ended banner
  (disable composer, offer recording; never auto-close); URL rotation → re-point player.
- Zap **the stream** (30311 addr); reuse `ZapAmountDialog`. **Decide zap recipient semantics**
  (single author vs NIP-53 zap splits). Disable-with-reason when no LN address / NWC not connected.
  Chat mute/block filtering is the enforcement point (memory `desktop_moderation_safety.md`).
  **Per-message chat zap + top-zappers header are deferred to v1.5** (chat still renders inline zap
  rows received from relays — just no zap-a-message action / leaderboard).
- 2nd-stream-open + close teardown: cancel both subscriptions + reset player before mounting next.

#### Phase 4 — Profile Streams tab + planned badge
- `UserProfileScreen` "Streams" tab: user's 30311s (live + past; VOD via `recording`). **Kept in v1.**
- Planned streams: **"starts in…" badge only** (nearly free; reuse `ScheduledFlag`). Overdue-planned
  relabeled/downranked. **"Remind me" notify machinery is deferred** (no OS scheduler; event-driven
  in-app-only had low hit-rate — revisit when an OS-notification path lands).

#### Phase 5 — Polish, tests, format
- Reuse `MaterialSymbols.Videocam` (no new codepoint → **no font subset regen**); LIVE badge = red
  dot + text.
- Unit tests (see Phase 1). `./gradlew spotlessApply`; compile `commons` (JVM+iOS purity),
  `desktopApp`, `amethyst`, `cli`.
- Manual testing sheet `desktopApp/plans/2026-08-20-live-media-manual-testing.md` (Linux GStreamer note).

### Optional v1 trim (simplicity review — user decision)

The simplicity reviewer recommends a smaller v1 (some items conflict with brainstorm choices — flagged
for you, **not** auto-applied): live bar on **Following/Global only** (already adopted above; the
per-`feedMode` scoping for hashtag/list/search columns is the riskiest part); **defer** the Profile
Streams tab; **cut** planned "Remind me" (fires only when app is open + stream happens to flip live —
high plumbing-to-payoff, gated on absent OS scheduler; keep the passive badge); **defer** per-message
chat zap + top-zappers header + host-pinned. Core loop (find → watch → chat → zap-stream + VOD)
survives every cut. See Open Questions.

---

## Alternative Approaches Considered
- **Watch as a deck column** — rejected (400px can't hold side-by-side video+chat).
- **Standalone "Lives" sidebar destination** — removed; brainstorm chose *fold into Discover*.
- **Single global sidebar "Live Now"** — rejected for per-column contextual bar.
- **New player dep (VLCJ/ffmpeg)** — rejected: VLCJ is GPL (MIT-licensing rule); kdroidFilter (MIT)
  already does HLS.
- **NIP-50 relay search for lives** — deferred; v1 search is client-side over cached 30311s.
- **Hand-rolled chat throttling** — unnecessary; the 250ms `BasicBundledInsert` already exists.

---

## System-Wide Impact

### Interaction graph
Discover/bar → subscribe `{kinds:[30311]}` → `DesktopLocalCache.consume` builds `LiveActivitiesChannel`
→ filters scan cache → cards render. Open card → watch host → `GlobalMediaPlayer.playVideo(streaming())`
(serialized) + `rememberSubscription(1311,#a)` → chat pipeline → `zapNote()`.

### Error & failure propagation
- Dead `status=live` → probe false → offline placeholder (never a spinner). Player stall → watchdog
  → "Reconnecting…" → Retry. Player error → scheme-checked "Open in default player" fallback.
- Zap failure → existing `ZapFeedback.Error/Timeout` snackbar. No relays → `rememberSubscription`
  returns null (no crash).

### State lifecycle risks
- **Cache growth (1311 unbounded):** Desktop has no prune — add the trigger (cap 500) + evict on
  watch-close. 30311 bounded (replaceable). Presence pruned at 20-min cutoff.
- **Player singleton:** race (fix with `Mutex`) + 60/s position churn (split position out; live mode
  reads none) + source-switch must reset `MediaPlaybackState`.
- **Reminder persistence:** expire dormant reminders.
- **Lossy event stream** (`DROP_OLDEST`): fine for chat; 30311 metadata must not share it.

### API surface parity
- Android + Desktop share `LiveActivitySorting` (hard AC → no ordering drift). `amy` CLI out of scope
  for v1, but the shared helper makes a future `amy lives` cheap.

### Integration test scenarios
1. Two follows live → bar shows higher-`current_participants` + "+1 live ›".
2. `status=live` but `.m3u8` dead → treated offline (probe), not shown live.
3. Post chat → 1311 with correct root `a` tag → appears; muted author's messages filtered.
4. Zap stream → request carries 30311 `a` tag → receipt attributed to stream (recipient per decision).
5. Planned flips live while app open → reminded user gets in-app snackbar.
6. Ended stream with `recording` → VOD plays with normal seekable bar.
7. live→ended mid-watch → end banner, composer disabled, "Play recording" offered, no auto-close.
8. Open 2nd stream while watching → first's player+subscription torn down before second mounts.

---

## Acceptance Criteria

### Functional
- [ ] Discover has a **Lives** section + client-side search (title/host/hashtag over cached 30311s).
- [ ] Each **Following/Global** feed column shows a pinned live-now bar: one host (ranked by
      `current_participants`) + `+N live ›`; hidden when none live.
- [ ] Opening a live shows a **full-window watch screen** (chosen host): live player (**no seek bar**,
      LIVE pill, stall→reconnect watchdog) left + kind-1311 chat right + header (title/host/roles/
      viewer count/status).
- [ ] Live metadata (title/roles/viewer count/status) updates live as the 30311 is re-published.
- [ ] User can **read + post** live chat; posts are 1311 with the correct root `a` tag; chat filters
      **muted/blocked** authors (private + public) and re-filters on live mute-list change.
- [ ] Chat composer disabled-with-reason when: stream ended, logged out, or send in flight/failed
      (draft preserved).
- [ ] User can **zap the stream** (recipient semantics defined); disabled-with-reason when no LN
      address / NWC not connected. (Per-message chat zap deferred to v1.5.)
- [ ] **Planned** streams show a "starts in…" badge; overdue-planned relabeled/downranked.
- [ ] **Ended** streams with `recording` play as **VOD** (seekable); ended-without-recording shows a
      non-playable "no recording" state everywhere it can appear.
- [ ] Profile has a **Streams** tab (user's live + past streams).
- [ ] Mid-watch `live→ended` shows an end banner, disables composer, offers recording; never auto-closes.
- [ ] Streaming-URL change or mid-playback stall re-points/reconnects → offline+Retry, never a frozen frame.
- [ ] Opening a 2nd stream cleanly tears down the first (both subscriptions + player) before mounting.
- [ ] Direct naddr/nevent open resolves + fetch-first (loading→branch); unknown/not-found handled, no hang.

### Non-functional
- [ ] Chat UI ≤ 4 list updates/sec (250ms bundler); list ≤ 500 rows; no recompose storm.
- [ ] Live-bar recompose only when top-host/count changes (immutable `StateFlow` + `distinctUntilChanged`).
- [ ] ≤ 1 network HEAD per `.m3u8` per 5 min; visible-only probing; one poll loop (not per-card).
- [ ] Player mutations serialized; position ticks don't recompose header/chat; live mode reads no position.
- [ ] Player + "Open in default player" both gated by a URL-scheme allowlist (https only; block
      `file://`/`smb://`/custom schemes); probe routed through the proxy-aware client; probe-on-intent,
      not on discovery.
- [ ] Chat text sanitized (bidi controls escaped per repo rule / CVE-2021-42574; no remote auto-load
      abuse); participant `proof` verified before showing a claimed role (`hasValidProof`).
- [ ] No new copyleft dependency (player stays kdroidFilter MIT).

### Quality gates
- [ ] Unit tests: status order, freshness, ranking, offline-downgrade, overdue-planned, zombie-live,
      latest-per-address dedupe, concurrent-mutation sort stability.
- [ ] `commons` JVM + iOS-purity, `desktopApp`, `amethyst`, `cli` compile; `spotlessApply` clean.
- [ ] Manual testing sheet executed on macOS (+ Linux GStreamer note).

---

## Dependencies & Risks
- **Live `.m3u8` handling + stall recovery** — highest risk; Phase 0 spike. Win/Mac have no native
  stall flag → app-level watchdog mandatory.
- **Channel cache is new on Desktop** — first channel abstraction + first prune trigger; core-file change.
- **Player singleton race** — fix before shipping (Mutex).
- **Watch host** — no window-level host exists; pick (a)/(b)/(c) in Phase 0.
- **Mute/block in chat** — known Desktop silent-no-op gap; must not regress.
- **No OS scheduler** — "Remind me" is in-app/event-driven only.
- **Linux runtime** — GStreamer required (documented in `desktopApp/build.gradle.kts`).

---

## Non-Goals / Deferred
Broadcasting; audio rooms / Nests (30312/10312); raids (`LiveActivitiesRaidEvent`); clips
(`LiveActivitiesClipEvent`); zap-goal progress bar + zap-to-highlight (fast follow — `goal` tag
parsed); OS-level/system-tray notifications; `amy` live verbs; PiP/mini live-player; NIP-53 `proof`
signature *generation*; per-column bar on hashtag/list/search columns (v2).

---

## Open Questions

**Blocking (resolve in Phase 0):**
1. **Watch host** — (a) app-root overlay [recommended] / (b) separate `Window` / (c) single-pane +
   max-width column?
2. **Live signal** — confirm `duration==0.0` on real live `.m3u8` across backends; is manifest
   `#EXT-X-ENDLIST` fetching worth it for precise ended, or rely on `status=ended`?
3. **Stream-zap recipient** — single 30311 author, or honor NIP-53 zap splits / `p`-tag recipient?

**Scope (RESOLVED 2026-08-20):**
4. Profile Streams tab → **kept in v1**.
5. Planned "Remind me" → **badge-only in v1** (notify machinery deferred).
6. Per-message chat zap + top-zappers header → **deferred to v1.5**.
7. Watch host → **(a) app-root overlay host** (also blocking Q1 above).

**During work:**
7. Chat windowing vs virtualization — confirm the 250ms-bundler + 500-cap path suffices (likely yes).
8. Search — client-side for v1; NIP-50 relay search later?

---

## Sources & References

### Origin
- **Brainstorm:** [docs/brainstorms/2026-08-20-feat-desktop-live-media-brainstorm.md](../brainstorms/2026-08-20-feat-desktop-live-media-brainstorm.md)
  — carried forward: per-column live bar (one host + `+N`, viewers-ranked); lives folded into Discover
  + search; full-window watch+chat; zaps in v1; live+planned+VOD; Streams tab; planned "Remind me";
  online HEAD-probe.

### Internal (verified file:line)
- Quartz `nip53LiveActivities/{streaming/LiveActivitiesEvent, chat/LiveActivitiesChatMessageEvent, LiveStreamLike}.kt`, `streaming/tags/`.
- commons `model/nip53LiveActivities/LiveActivitiesChannel.kt`, `nip53LiveActivities/{LiveActivityTopZappersAggregator, ui/StreamSystemCard}.kt`, `viewmodels/{FeedViewModel, LiveStreamTopZappersViewModel}.kt`, `ui/feeds/`, `ARCHITECTURE.md`, `build.gradle.kts` (`verifyKmpPurity`).
- Android `home/dal/HomeLiveFilter.kt`, `home/live/{RenderLiveActivityBubble,LiveStatusIndicator}.kt`, `discover/nip53LiveActivities/DiscoverLiveFeedFilter.kt:116-134`, `livestreams/dal/LiveStreamsFeedFilter.kt`, `note/types/{LiveActivity,LiveActivityChatMessage}.kt`, `chats/publicChannels/nip53LiveActivities/*`, `service/OnlineCheck.kt` (android-only, `LruCache`), `AccountViewModel.checkVideoIsOnline`.
- Desktop `ui/deck/{DeckSidebar,DeckColumnType,DeckColumnContainer,DeckState,SinglePaneLayout}.kt`, `ui/{FeedScreen,ReadingColumn,UserProfileScreen,SearchScreen,ChatPane}.kt`, `followpacks/ui/DiscoverScreen.kt`, `ui/media/{DesktopVideoPlayer,VideoControls}.kt`, `service/media/GlobalMediaPlayer.kt`, `ui/NoteActions.kt`, `nwc/NwcPaymentHandler.kt`, `cache/DesktopLocalCache.kt` (`getAnyChannel`→null, `route()`, `addressableNotes`), `subscriptions/SubscriptionUtils.kt`, `Main.kt` (`DesktopScreen` per-column), `DesktopPreferences`.
- Build: `gradle/libs.versions.toml` (`composemediaplayer = 0.11.4`, MIT).
- Memory: `desktop_moderation_safety.md` (mute/block enforcement gap).

### External
- NIP-53: https://github.com/nostr-protocol/nips/blob/master/53.md
- zap.stream: https://github.com/v0l/zap.stream
- kdroidFilter ComposeMediaPlayer: https://github.com/kdroidFilter/ComposeMediaPlayer (issues #244 buffered-ranges, #173 HLS Win/Mac, #77 403→blank)
- RFC 8216 (HLS) `#EXT-X-ENDLIST`/`PLAYLIST-TYPE`: https://www.rfc-editor.org/rfc/rfc8216.html
- Mux HLS ext tags: https://www.mux.com/articles/hls-ext-tags
- Twitch chat rendering perf: https://blog.twitch.tv/en/2016/08/08/improving-chat-rendering-performance-1c0945b82764/
- video.js live guide: https://videojs.com/guides/live/ · hls.js gap-controller: https://github.com/video-dev/hls.js/blob/master/src/controller/gap-controller.ts
- Safer flow collection: https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda
