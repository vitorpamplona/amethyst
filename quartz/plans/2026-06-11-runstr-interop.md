# RUNSTR interop: Quartz events + Amethyst fitness screens

Research date: 2026-06-11. Source: `RUNSTR-LLC/RUNSTR` @ `main`
(commit `398cdffab452c9e482b3ecf3e5b956b3c0fe1b7b`), its `docs/KIND_1301_SPEC.md`,
`docs/ARCHITECTURE.md`, and the companion `RUNSTR-LLC/runstr-fitness-skill`
repo. Upstream spec context: NIP-101h PR
[nostr-protocol/nips#1937](https://github.com/nostr-protocol/nips/pull/1937)
(health metric kinds 1351–1399; *not* implemented by RUNSTR today).

## 1. The critical architecture finding

RUNSTR (React Native + NDK) is **no longer a pure-Nostr app**. Teams, clubs,
chat, competitions, and global leaderboards migrated to **Supabase**. The
current app:

- **builds and signs kind 1301 workout events in the NIP-101e dialect, but
  submits them to Supabase instead of relays**
  (`src/services/nostr/workoutPublishingService.ts:1-13,130-140,373-376`);
- still **consumes kind 1301 from relays** with intentionally lax parsing
  ("nuclear pattern": `src/services/fitness/Nuclear1301Service.ts`,
  `src/services/competition/Competition1301QueryService.ts`) for workout
  history import and club/event leaderboards;
- keeps Nostr for identity (kind 0), social posts (kind 1 + reactions/reposts),
  encrypted workout backups (kind 30078), event discovery (kind 31923), WoT
  (kind 30382), and published leaderboards (kind 30150, external aggregator).

Consequences for us:

1. Amethyst-published 1301s **will be seen** by RUNSTR (history import, club
   and event leaderboards aggregate members' relay 1301s).
2. We should **not expect new RUNSTR workouts to appear on relays** from the
   current app — but historical RUNSTR 1301s, RUNSTR-iOS, and other NIP-101e
   clients do publish them.
3. RUNSTR's *global daily* leaderboards/rewards require their Supabase
   submission + anti-cheat (`v` rolling code, `wot_score`) — not reachable via
   relays. Out of scope.

## 2. Event catalog — what to implement in Quartz

Quartz has **no fitness kinds today** (verified: no 1301/1351-1357/workout
code anywhere). All of this is net-new. Suggested package:
`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip101eFitness/`
(name mirrors how RUNSTR refers to the draft; can be renamed if upstream
numbering lands differently).

### 2.1 Kind 1301 — `WorkoutRecordEvent` (the core interop surface)

Regular (non-addressable) event, but RUNSTR always emits a `d` tag (UUID) and
dedupes on it — parse it, don't rely on it. Authoritative dialect:
`docs/KIND_1301_SPEC.md` in the RUNSTR repo.

Tags RUNSTR always emits (treat all as optional when parsing — their own
parser validates nothing):

| Tag | Format | Notes |
|---|---|---|
| `d` | UUID string | client-side dedupe id |
| `title` | free text | e.g. "Morning Run" |
| `exercise` | lowercase verb | `running\|walking\|cycling\|hiking\|swimming\|rowing\|strength\|yoga\|meditation\|diet\|fasting` |
| `duration` | `HH:MM:SS` | parser must also accept raw seconds |
| `source` | `gps\|manual` | also seen: `healthkit`, `runstr` |
| `client` | `["client","RUNSTR",version]` | 3-element form, not NIP-89 |
| `t` | Capitalized hashtag | `Running`, `Strength`, … fallback `Fitness` |

Conditional metric tags: `["distance", "5.20", "km"|"mi"]` (2-decimal string),
`["elevation_gain"/"elevation_loss", n, "m"|"ft"]`, `["calories", n]`,
`["steps", n]`, `["avg_pace","MM:SS","min/km"|"min/mi"]`,
`["split", kmNumber, "HH:MM:SS"]` (cumulative), `["split_pace", n, seconds]`,
`["avg_heart_rate"/"max_heart_rate", bpm]` (spec-defined),
strength: `["sets",n]`, `["reps",n]`, `["weight",n,"lbs"]`,
`["weight_set", setNum, weight, "lbs"]`; plus `meditation_type`, `meal_type`,
`meal_size`, `exercise_type`, `data_points`, `recording_pauses`,
`workout_start_time` (unix seconds string).

Competition/reward tags (parse, surface, never required): `["team", id|"self"]`,
`["club", id]`, `["charity", id, name, lud16?]`, `["lightning", lud16]`,
`["reward_destination", "user"|"charity"|"ppq"]`, `["challenge", slug]`,
`["wot_score","0".."100"]`, `["v", rollingCode]` (anti-bot, from kind-30150
note), `verified`/`verification_*` tags.

**Content is plain text, never JSON** (user notes / human-readable summary).

Unit handling for parsing (match RUNSTR's lax rules): default `km`/`m`/`lbs`
when unit missing; `mi` → ×1609.344 m; `ft` → ×0.3048 m.

When *Amethyst publishes*, emit the strict canonical form above so RUNSTR's
leaderboard aggregation (`fastest_time` needs `distance` + `duration`;
`most_distance` sums `distance`; `participation` counts events) scores us
correctly.

### 2.2 Kind 30078 — RUNSTR encrypted workout backup (clean interop win)

NIP-78 app-data event, `d = "runstr-workout-backup"`. Plaintext metadata tags:
`["client","RUNSTR",v]`, `["encrypted","nip44"]`, `["compression","gzip"]`,
`["backup_version","1"]`, `["workout_count",n]`, optional `habit_count`,
`journal_count`, date ranges. Content = JSON → gzip → base64 → **NIP-44
self-encrypt** (to own pubkey). Decode: nip44-decrypt → base64 → gunzip.
Backup relays: damus + nos.lol. Quartz already has NIP-44 and NIP-78
machinery; we need the gzip step (JVM/Android trivial; check iOS source set)
and a `RunstrWorkoutBackupEvent` wrapper. This lets Amethyst **import a user's
entire RUNSTR history with just their key** — the highest-leverage interop
feature, immune to the Supabase migration.

### 2.3 Kind 31923 — RUNSTR fitness events (already-implemented base)

Quartz has NIP-52 (`nip52Calendar`, kind 31923 time-based calendar event).
RUNSTR layers extra tags on it (`src/services/events/RunstrEventPublishService.ts`):
discovery marker **`["t","runstr"]`** (their filter key) + activity-type and
distance hashtags, and RUNSTR-specific tags: `scoring`
(`fastest_time|most_distance|participation`), `payout`, `join_method`,
`duration_type`, `distance` (value+unit), `pledge_cost`, `pledge_destination`,
`captain_lightning_address`, `entry_fee`, `prize_pool`, `suggested_donation`,
`activity_type`, `image`, `team_competition`. Plan: extension accessors on the
existing calendar event (or a thin `RunstrEventTags` helper) rather than a new
kind. Note their kind-31925 RSVP is typed but dead code — joining is
local/Supabase; don't build RSVP interop expecting RUNSTR to read it.

### 2.4 Kind 30150 — published leaderboard note (read-only)

External aggregator (pubkey
`611021eaaa2692741b1236bbcea54c6aa9f20ba30cace316c3a93d45089a7d0f`,
`d = "runstr-leaderboards"`, on damus + nos.lol, refreshed ~5 min). Content is
JSON: `{v:1, updatedAt, competitions:[{id,name,activityType,scoringMethod,
status,entries:[{r,p,n,s,w}]}]}` (rank/npub/name/score/workout-count). A small
`RunstrLeaderboardEvent` (addressable) + Jackson DTO gives Amethyst live
RUNSTR leaderboards for free. This is also where the rolling `v` anti-bot
code is published.

### 2.5 Kind 30000 participant lists (already-implemented base)

Season participant lists are plain NIP-51 follow sets authored by the admin
pubkey (e.g. `d = "runstr-season-2-participants"`). Quartz `nip51Lists`
already parses these; only the well-known author/d-tag constants are needed.

### 2.6 Explicitly skip (dormant post-migration)

Kinds **33404** (team), **30100/30101** (league/event), **31013**
(competition), **30002** (participant list), **1104/1105** (join requests),
**9321/37375** (nutzap/wallet — they reverted to LNURL/NWC). Code exists in
their repo but nothing publishes or reads them anymore. Their custom
notification kinds **1101–1103** are backend-published and Supabase-coupled;
revisit only if their backend keeps emitting them. **21301** (paid anti-cheat
request) is RUNSTR-business-specific — skip.

NIP-101h (kinds 1351–1399, NIP-44-encrypted health metrics,
[nips#1937](https://github.com/nostr-protocol/nips/pull/1937)) has **no code
in RUNSTR today**; implement later as its own `nip101hHealth` package if/when
we want health-profile interop.

### 2.7 Quartz mechanics (per codebase survey)

- Event class per kind following e.g.
  `nip25Reactions/ReactionEvent.kt` (regular) /
  `nip53LiveActivities/streaming/LiveActivitiesEvent.kt` (addressable):
  `KIND` constant, `build()` via `eventTemplate` + `TagArrayBuilder` DSL,
  tag-accessor functions, `@Immutable`.
- Register each kind in
  `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt`.
- Unit tests with real RUNSTR event JSON captured from relays (damus/primal/
  nos.lol) as fixtures; `amy` can fetch live samples for interop checks.

## 3. Amethyst screens

Survey of existing UI says: no fitness UI exists; Vico charts (v3.1.0) is
already a dependency (used in the notifications summary chart at
`amethyst/.../notifications/chart/ShowChart.kt`); new kinds render via the
`when(event)` dispatch in `amethyst/.../ui/note/NoteCompose.kt`
(`RenderNoteRow`) with per-type composables under `ui/note/types/`.

Phased UI plan (ViewModels in `commons/.../viewmodels/`, shared cards in
`commons` per `commons/ARCHITECTURE.md`; screen scaffolding/nav per platform):

1. **Workout card in feeds** — `RenderWorkoutRecordEvent` showing
   activity icon, title, distance/duration/pace/elevation/calories chips, and
   splits. This alone makes Amethyst display every 1301 on the network.
2. **Profile "Fitness" tab / workout history feed** — `AdditiveFeedFilter`
   over kind 1301 by author; stats header (weekly distance, streak) with Vico.
3. **Workout composer** — manual entry first (type, duration, distance,
   notes), publishing canonical 1301s. GPS tracking is a much bigger,
   Android-only follow-up (foreground service, location permissions).
4. **RUNSTR events discovery** — feed of kind 31923 with `t=runstr`, detail
   screen showing scoring/entry-fee/prize tags, and a client-side leaderboard
   computed from participants' 1301s using RUNSTR's scoring rules (and/or the
   pre-computed 30150 note).
5. **Backup import** — settings action: fetch `30078:user:runstr-workout-backup`,
   decrypt, and ingest workouts into LocalCache (optionally re-publish as
   1301s with user consent).

## 4. Relays & constants

- RUNSTR defaults: `wss://relay.damus.io`, `wss://relay.primal.net`,
  `wss://nos.lol`; backups on damus + nos.lol.
- Admin/aggregator pubkey: `611021eaaa2692741b1236bbcea54c6aa9f20ba30cace316c3a93d45089a7d0f`.
- WoT assertions: kind 30382 from Brainstorm
  (`3eaeb02c4f94a0aabf016527c35222a2ede49b3981df32aa9096f5db2dad58e2`) on
  `wss://nip85.brainstorm.world` — only needed if we ever want RUNSTR reward
  parity; skip initially.

## 5. Suggested implementation order

1. `nip101eFitness` package: `WorkoutRecordEvent` (kind 1301) + tag classes +
   EventFactory registration + fixture tests.
2. Feed card (phase-1 UI) — immediate visible interop.
3. `RunstrWorkoutBackupEvent` (30078 dialect) + import flow.
4. RUNSTR tag accessors on NIP-52 31923 + events discovery screen.
5. `RunstrLeaderboardEvent` (30150) + leaderboard rendering.
6. Workout composer (manual), then evaluate GPS tracking as its own plan.
