# Desktop Feature Backlog — Inspiration from Nostr Ecosystem

Survey date: 2026-06-29. Source: cross-client research across top-5 used clients
(Damus, Primal, YakiHonne, Snort, Iris) + top-5 desktop-first / power-user
clients (Notedeck, Gossip, Jumble, Coop, Flotilla). Wisp investigated separately
(Android-only, not desktop).

Already shipped on Amethyst Desktop and excluded from this list: deck columns,
embedded local relay, NIP-46 bunker login, NWC zapping, custom feeds, advanced
search, follow packs (in progress).

---

## Priority queue (next up)

### 1. Hashtag-spam filter — NEXT
- **Inspired by:** Damus (auto-hide posts above N hashtags).
- **What:** Configurable threshold; posts with `> N` `t` tags get hidden or
  collapsed across all feeds. User-defined whitelist for legit multi-tag use
  (events, longform).
- **Why desktop:** Spam noise scales with column count on a deck UI; one filter
  cleans every column at once.
- **Module:** `commons/` (filter logic, shared with Android in future) +
  `desktopApp/` (settings UI).
- **Skills:** `feed-patterns`, `compose-expert`, `account-state`.

### 2. WoT (web-of-trust) score on avatars + filters
- **Inspired by:** Gossip, Snort.
- **What:** Friends-of-friends score (0–N) computed from follow graph. Visual
  badge/ring on avatar. Threshold filter for notifications/DM-requests.
- **Why desktop:** At-a-glance trust signal scales with multi-column deck.
- **Module:** `commons/services/WoTService.kt` (StateFlow<Map<HexKey, Int>>),
  avatar composable in `commons/.../note/`.
- **Skills:** `account-state`, `kotlin-flow-state-event-modeling`,
  `compose-expert`. Score compute is O(follows × follows) — must be lazy /
  throttled.

---

## Full ranked backlog (10 ideas)

Ranking heuristic: impact × novelty / implementation cost. Items 1–2 are the
priority queue above; 3–10 captured for later.

| # | Feature | Inspired by | Module | Notes |
|---|---------|-------------|--------|-------|
| 1 | Hashtag-spam filter | Damus | commons + desktopApp | **NEXT** |
| 2 | WoT score on avatars + filters | Gossip, Snort | commons | After #1 |
| 3 | Relay-as-column + Relay Sets first-class | Jumble, Flotilla | commons | Drag relay URL → column |
| 4 | Cashu ecash wallet alongside NWC | Iris, Primal Spark | quartz + commons | Bearer-token privacy |
| 5 | AI "Dave" column (timeline-aware assistant) | Notedeck | desktopApp | LLM reads adjacent columns |
| 6 | Algorithmic Feed Marketplace | Primal v3.0 | commons | Discover layer on custom feeds |
| 7 | Keyboard / command palette (`Ctrl+K`, `?` overlay) | Snort, Notedeck | desktopApp + commons | Power-user nav |
| 8 | Longform reader column + offline publish queue | Damus, YakiHonne | commons | NIP-23, uses local relay |
| 9 | WoT-scored notification filter + undo-send | Gossip, Snort | commons | Calmer UX |
| 10 | Per-relay column health (sparkline + dot) | Wisp, Gossip | commons | Reuse EOSE manager |
| 11 | Discord-style "communities" sidebar (NIP-29) | Flotilla, Chachi | desktopApp + commons | Sidebar second purpose |

(Extra row #11 added: communities sidebar was the 10th in the original survey;
WoT-notif-filter + undo-send promoted to its own row for clarity.)

---

## Per-item detail

### #3 Relay-as-column + Relay Sets first-class
- **Drop:** Drag relay URL onto the deck → new column showing that relay's
  global. Relay Sets become saveable column templates.
- **Impl:** New `RelayUrlFeedFilter` + `RelaySetFeedFilter` in `commons/feeds/`.
- **Gotcha:** Bypass write-through to local store for these feeds, otherwise
  local relay pollutes with arbitrary global content.

### #4 Cashu ecash wallet alongside NWC
- **Why:** Different privacy model (bearer tokens, no account). Power users
  want both — small/private = ecash, big/recurring = NWC.
- **Impl:** Check Quartz NIP-60/61 coverage; else new `wallet/cashu/`. UI is a
  second tab in existing wallet column.
- **Gotcha:** Mint trust UX; token backup/restore; encryption story
  (`accounts.json.enc` slot).

### #5 AI "Dave" column
- **Why:** Big-screen-only feature that breaks the deck-column metaphor wide
  open. Reads N adjacent columns as context.
- **Impl:** `desktopApp/` for API key config + `commons/ai/` for prompt builders.
  Pluggable model: Ollama local default, OpenAI/Anthropic optional.
- **Gotcha:** Privacy story — must be explicit which events get sent where.
  Default to local Ollama.

### #6 Algorithmic Feed Marketplace
- **Why:** Custom feeds already shipped — marketplace is the discovery layer.
- **Impl:** Feeds are published as kind:30000-ish lists or DVM-driven. Browser
  UI + subscribe button on top of existing custom-feed infra.
- **Gotcha:** DVM feeds vs static list feeds — two execution paths.

### #7 Keyboard / command palette
- **Why:** Mouse-first ≠ keyboard-hostile.
- **Impl:** `desktopApp/` global `onPreviewKeyEvent` + `commons/ui/CommandPalette.kt`.
- **Gotcha:** Focus management across deck columns; cmd vs ctrl on macOS;
  conflict with text-input fields.

### #8 Longform reader + offline publish queue
- **Why:** NIP-23 is barely surfaced on Desktop. Big screens are *the* surface
  for reading articles.
- **Impl:** New column types `LongformReader` + `LongformComposer`. Composer is
  block-based. Offline queue writes drafts to local relay; auto-broadcasts on
  reconnect.
- **Gotcha:** Markdown rendering on Compose Desktop is mid — pick or build.
  Blossom upload alongside drafts.

### #9 WoT notif filter + undo-send
- **Why:** Notifications scale poorly; slow-mode (delay sends 5–30 s with
  toast-cancel) calms power-user UX.
- **Impl:** `NotificationFilter.kt` extension + send-pipeline interceptor in
  `commons/relayClient/`.
- **Gotcha:** Sent-but-cancelled events already in local relay write-through —
  must scrub from local store on cancel.

### #10 Per-relay column health
- **Why:** Deck UI exposes the multi-relay reality. Latency/EOSE/dup-rate dot +
  sparkline in column header helps self-tune.
- **Impl:** Extend EOSE manager to emit `RelayHealth` per relay; render small
  sparkline + colored dot.
- **Gotcha:** Memory over time — rolling 60 s window, decay older.

### #11 Communities sidebar (NIP-29)
- **Why:** Amethyst Desktop sidebar is nav-only; mapping joined groups /
  favourite relays to Discord-like server icons doubles its purpose.
- **Impl:** `desktopApp/.../sidebar/` new section; on click, populate deck with
  group channels. `commons/groups/` for NIP-29 if not in Quartz already.
- **Gotcha:** NIP-29 spec churn — check Quartz coverage. Don't conflate
  "favourite relay" with "joined group".

---

## Sources

- nostr.com/clients, nostrapps.com, stats.nostr.band
- humai.blog "Best Nostr Apps 2026"; nostr.co.uk/clients; nostrcompass.org #15
- opensats.org "Advancements in Nostr Clients"
- Repos: damus-io/damus, damus-io/notedeck, mikedilger/gossip, CodyTseng/jumble,
  lumehq/coop, coracle-social/flotilla (active at gitea.coracle.social),
  barrydeen/wisp
