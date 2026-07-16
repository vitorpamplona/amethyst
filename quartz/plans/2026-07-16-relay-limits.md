# Relay `LIMITS` message — status & roadmap

Support for the relay-to-client `LIMITS` frame (from
[nostr-protocol/nips#1434](https://github.com/nostr-protocol/nips/pull/1434)),
where a relay advertises the current connection's rights and limits — on
connect and again whenever they change (e.g. after a NIP-42 AUTH flips
`can_write`). Live-verified against `wss://pipe.imwald.eu/` (which sends `AUTH`
then `LIMITS`).

Note: `LIMITS` is **not** NIP-22 (that's Comment / kind 1111); it's the #1434
proposal and is label-based on the wire, so the code keys off the `"LIMITS"`
label rather than a NIP number.

## Done (branch `claude/limits-message-support-g5plrd`, PR #3597)

- **Parse** — `LimitsMessage` model (`nip01Core/relay/commands/toClient/`) with
  every #1434 field plus the two non-spec extensions this relay sends
  (`auth_for_read` / `auth_for_write`). Wired into both codecs:
  Jackson (`LimitsDeserializer` + `MessageSerializer`, jvmAndroid) and
  kotlinx (`LimitsKSerializer` + `MessageKSerializer`, iOS/native). Unknown
  fields are ignored, so a future field can't re-trigger the original
  "Message LIMITS is not supported" crash.
- **Cache/expose** — `RelayLimitsTracker` (`nip01Core/relay/client/limits/`),
  a passive `RelayConnectionListener` accessory (modeled on
  `RelayAuthenticator`) that caches the latest `LimitsMessage` per
  `NormalizedRelayUrl` and publishes a Compose-stable
  `StateFlow<PersistentMap<url, LimitsMessage>>`. Connection-scoped (dropped on
  disconnect). Wired in `AppModules` as `Amethyst.instance.relayLimits`.
- **Cleanup** — removed the unused experimental prototype
  (`experimental/limits/Limits.kt` + `LimitProcessor.kt`); renamed the client
  accessory to `…Tracker` to avoid colliding with the server-side
  `relay/server/policies/RelayLimits`.

## Audit fixes to fold in (small, do first)

From the 2026-07-16 review of the branch:

1. **kotlinx parse should be as lenient as Jackson.** `MessageKSerializer` is
   the *iOS* incoming path; today `LimitsKSerializer.deserializeFromElement`
   throws on a mistyped field (`(tag as JsonArray)`, `.jsonPrimitive.int`) or a
   payload-less `["LIMITS"]` (`array[1].jsonObject`), where Jackson coerces.
   Same frame → parsed on Android, dropped on iOS. Use `intOrNull` /
   `booleanOrNull` / `longOrNull`, guard the array cast, and default a missing
   payload object to an empty `LimitsMessage`.
2. **Drop the stale "NIP-22" labels** in `LimitsKSerializer` KDoc and the
   `// NIP-22 wire format` comments in `MessageKSerializer` / `MessageSerializer`
   (already removed from `LimitsMessage.kt`).
3. **Make `LimitsMessage` a `data class`** so `StateFlow.distinctUntilChanged`
   suppresses no-op emissions when a relay re-advertises identical limits, and
   tests get value equality.
4. Fold the explicit-`null`→`false`/`0` coercion nuance into (1) via the
   `…OrNull` accessors on both sides.

## Roadmap

### 1. Client-side apply/enforcement (highest user-facing value)

#1434 says clients MUST apply limits when sending. Provide **pure, testable
helpers on `LimitsMessage`** (mirror the server's `LimitsPolicy` logic; the
deleted `LimitProcessor` in git history is a starting sketch):

- `clamp(filter)` / `clamp(filters)` → cap each filter `limit` to `max_limit`.
- `rejectionForPublish(event): String?` → `can_write`, `accepted`/`blocked
  _event_kinds`, `max_content_length`, `max_event_tags`, `min_pow_difficulty`,
  `created_at_msecs_ago`/`ahead` window, `required_tags`.
- `canRead()` / `canWrite()` convenience.

Keep the helpers in quartz (pure, no side effects). Wire them into the send
path at the app layer (subscription/filter-assembly + publish), reading from
`RelayLimitsTracker`. Decide the UX for a rejected publish (surface vs
silently drop) with the maintainer — this is the one opinionated call.

### 2. Server-side emit (`geode`) — the symmetric half

The relay side already **enforces** (`LimitsPolicy` + `RelaySession`) and
**advertises via NIP-11** (`RelayLimits.toNip11Limitation()`), but never sends
the dynamic `LIMITS` frame. Add:

- `RelayLimits.toLimitsMessage(canRead, canWrite, authForRead, authForWrite)`
  next to `toNip11Limitation()`, so one source of truth drives NIP-11 *and* the
  frame.
- `RelaySession` / `EventSourceServer` sends `LIMITS` on connect and **re-sends
  after AUTH** when effective rights change.
- Then geode ↔ our own client becomes interop-testable (relayBench / the amy
  serve path).

### 3. NIP-11 ↔ LIMITS bridge

The two overlap ~80% (`RelayInformationLimitation` vs `LimitsMessage`). Add
`LimitsMessage.toNip11Limitation()` / `RelayInformationLimitation
.toLimitsMessage()`, and let `RelayLimitsTracker` **seed** from the NIP-11 doc
before the socket connects, then override with the live frame — one "limits of
relay X" model regardless of source.

### 4. Tooling, tests, docs

- `amy relay info` already prints the NIP-11 doc; add the live `LIMITS` frame
  (the interop tool the repo already leans on).
- Catalog `RelayLimitsTracker` in
  `nip01Core/relay/client/accessories/README.md`.
- Test the **AUTH → re-LIMITS** transition (`auth_for_write` flipping after a
  successful AUTH) — the one dynamic behavior not yet exercised live.

### 5. Spec follow-up

`auth_for_read` / `auth_for_write` are not in #1434. Worth a note on the PR;
we tolerate them as extras either way (they pair with the `AUTH` this relay
also sends).

## Suggested sequencing

audit fixes → (1) client apply helpers → (2) server emit → (3) NIP-11 bridge →
(4) tooling.
