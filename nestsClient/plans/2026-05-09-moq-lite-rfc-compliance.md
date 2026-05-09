# moq-lite Lite-03 compliance audit

**Date:** 2026-05-09
**Branch:** `claude/audit-moq-lite-compliance-NSuPk`
**Scope:** `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/` plus
the audio glue under `…/audio/` that drives moq-lite. The IETF
`draft-ietf-moq-transport-17` reference impl in `…/moq/` (no `lite/`)
is OUT of scope — it stays as a unit-test target only. The QUIC layer
(`:quic`) is OUT of scope; recently audited and locked.

## TL;DR

The moq-lite Lite-03 implementation is **wire-compatible** with
kixelated's reference relay (moq-rs / moq-lite-03). All on-the-wire
codec primitives — control type discriminators, AnnouncePlease,
Announce, Subscribe (incl. the `Option<u64>` off-by-one and `Duration`
millis-as-varint conventions), SubscribeOk/Drop (with the
type-outside-size-prefix framing peculiar to Lite-03), GroupHeader,
Probe — match the reference Rust impl byte-for-byte. The known gaps
are all spec-loose / future-fragile (🟡 / 🟦), not wire-incompatible
(🔴).

**This audit shipped four fixes** (M1, M4, M5, L5) and **closed M6**
(verified via WebFetch that Goaway has no body in the spec, so our
existing handler is canonical). **L4** is subsumed by the M1 fix.
The remaining 🟡 / 🟦 items (M2, M3, L1, L2, L3) are deferred with
rationale below — each is a deliberate non-fix because either the
QUIC layer is locked (M2/M3 need WebTransport interface extensions
that touch `:quic`), the change is significant scope (L1 Lite-04
codec rewrite), or there's no consumer for the proposed API
(L2/L3). **No 🔴 wire-incompatibilities remain.**

## Methodology

1. Walked every Kotlin file under `moq/lite/` and traced data flow
   speaker → relay → listener.
2. Cross-referenced each on-wire message + control byte against
   kixelated's reference Rust impl at
   `https://github.com/kixelated/moq/tree/main/rs/moq-lite/src/`,
   reading specifically:
   - `coding/decode.rs`, `coding/encode.rs`, `coding/varint.rs`
     (primitive codings — confirmed `Option<u64>` is `0=None /
     n=Some(n-1)`, `Duration` is `millis-as-varint`, `bool` is 1
     byte 0/1).
   - `lite/stream.rs` (ControlType + DataType discriminator codes).
   - `lite/announce.rs` (AnnouncePlease + Announce body shape).
   - `lite/subscribe.rs` (Subscribe + SubscribeResponse Lite-03
     framing).
   - `lite/publisher.rs` (`Publisher::serve_group` priority handle,
     uni-stream open + group header).
   - `lite/subscriber.rs` (Probe direction, group decode loop,
     unsubscribe = FIN).
   - `lite/probe.rs` (Probe body: `bitrate u62` only on Lite-03).
3. Verified the team's prior `2026-04-26-moq-lite-gap.md` against
   live source — every claimed item still holds (see "Verified
   working" below).
4. Built the gap matrix (see "Gap matrix" below).
5. Shipped fixes for the two 🟡 items closest to being 🔴 — see
   "Fixes shipped" at bottom.
6. Ran `:nestsClient:jvmTest` after each fix — green throughout.

## What's verified working

The shipped Lite-03 surface holds up against the reference Rust impl
at the byte / control-flow level:

| Surface                                                                                                                              | Verdict | Evidence (file:line) |
| ------------------------------------------------------------------------------------------------------------------------------------ | ------- | -------------------- |
| ALPN `"moq-lite-03"` advertised as the only sub-protocol                                                                             | ✅       | `MoqLiteAlpn.kt:47`, `QuicWebTransportFactory.kt:88-113` |
| No SETUP / no in-band session message (Lite-03 = WT handshake IS the handshake)                                                      | ✅       | `MoqLiteSession.kt:1374-1378` (`client(...)` returns immediately, no SETUP coroutine) |
| `ControlType` codes — `Session=0, Announce=1, Subscribe=2, Fetch=3, Probe=4, Goaway=5`                                               | ✅       | `MoqLiteControlCodes.kt:39-65` (all five recognised + dispatched in `handleInboundBidi`) |
| `DataType::Group=0` on uni-stream open                                                                                               | ✅       | `MoqLiteControlCodes.kt:74-85`, `MoqLiteSession.kt:1047` |
| Path normalisation (strip leading/trailing `/`, collapse runs)                                                                       | ✅       | `MoqLitePath.kt:44-99` (matches `rs/moq-lite/src/path.rs::Path::new`) |
| Path normalisation applied at every wire boundary                                                                                    | ✅       | `MoqLiteCodec.kt:54, 70, 92` (encode side); `:60, 81, 105` (decode side) |
| AnnouncePlease body shape — `prefix: string`                                                                                         | ✅       | `MoqLiteCodec.kt:52-63` |
| Announce body shape — `status u8, suffix string, hops u62 varint`                                                                    | ✅       | `MoqLiteCodec.kt:67-85`; status enum `Ended=0, Active=1` matches `rs/moq-lite/src/lite/announce.rs:84-90` |
| Subscribe body — 8 fields: `id u62, broadcast string, track string, priority u8, ordered u8, maxLatency varint, startGroup, endGroup` | ✅       | `MoqLiteCodec.kt:89-100` |
| `Option<u64>` off-by-one collapse: `0=None, n=Some(n-1)`                                                                             | ✅       | `MoqLiteCodec.kt:286-288` matches `rs/moq-lite/src/coding/decode.rs::impl Decode for Option<u64>` |
| `Duration` encoded as millis-as-varint                                                                                               | ✅       | `MoqLiteCodec.kt:96` matches `rs/moq-lite/src/coding/encode.rs::impl Encode for std::time::Duration` |
| SubscribeResponse framing: type discriminator OUTSIDE size prefix (Lite-03+)                                                          | ✅       | `MoqLiteCodec.kt:152-167` matches `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode` Lite-03 arm |
| SubscribeOk body — 5 fields (priority, ordered, maxLatency, startGroup, endGroup) without id/broadcast/track                         | ✅       | `MoqLiteCodec.kt:127-135` |
| SubscribeDrop body — `errorCode varint + reasonPhrase string`                                                                         | ✅       | `MoqLiteCodec.kt:137-142` |
| Group uni-stream layout: `DataType=0` + size-prefixed `(subscribeId, sequence)` + size-prefixed frame loop until FIN                | ✅       | `MoqLiteSession.kt:1021-1050` (publisher), `:594-668` (listener `drainOneGroup`) |
| Probe body — `bitrate u62 varint` only (rtt is Lite-04+)                                                                              | ✅       | `MoqLiteCodec.kt:262-266` (encode), `:247-251` (decode) |
| Probe direction — subscriber opens bidi, publisher writes Probe messages                                                             | ✅       | `MoqLiteSession.kt:925-944` (publisher arm of `handleInboundBidi`) |
| Subscribe id allocation — per-session, monotonic, varint                                                                             | ✅       | `MoqLiteSession.kt:83, 246-251` (incremented under `state` mutex) |
| Group sequence allocation — per-publisher, monotonic, varint, hot-swap-aware (`startSequence` parameter)                              | ✅       | `MoqLiteSession.kt:716-746, 1129-1132, 1306-1329` |
| Per-group end = QUIC FIN                                                                                                              | ✅       | `MoqLiteSession.kt:1264-1271` (`endGroup`) |
| Broadcast ended = `Announce(Ended)` BEFORE FIN (correct ordering)                                                                    | ✅       | `MoqLiteSession.kt:1273-1304` (`PublisherStateImpl.close`) |
| Unsubscribe = FIN subscribe bidi's send side (no UNSUBSCRIBE message)                                                                | ✅       | `MoqLiteSession.kt:670-675` |
| Reliable QUIC streams for groups (NOT bestEffort; matches kixelated)                                                                 | ✅       | `MoqLiteSession.kt:1024-1039` + comment block explaining the prior bestEffort regression |
| Stream priority hint — newer groups (higher sequence) drain first under congestion                                                   | ✅       | `MoqLiteSession.kt:1040-1046`; sequence-as-priority is monotonically increasing, same direction as kixelated's `PriorityHandle.insert(track.priority, sequence)` |
| Auth — purely WebTransport-CONNECT-time (JWT in `?jwt=` query); moq-lite layer touches no claims                                     | ✅       | `MoqLiteSession.kt` has zero references to JWT / claims / auth |
| Reconnect — no moq-lite-layer message; transport drop ⇒ open new WT session and resubscribe                                          | ✅       | Reconnect lives in `connectReconnectingNestsListener` / `…NestsSpeaker` (transport-layer concern) |
| Goaway recognised + FIN'd cleanly (no body decode today; relay migration not implemented)                                            | ✅       | `MoqLiteControlCodes.kt:50-58`, `MoqLiteSession.kt:973-990` |
| Catalog publisher emits one frame per inbound subscriber via `setOnNewSubscriber` hook (matches hang.live consumer expectations)     | ✅       | `MoqLitePublisherHandle.kt:91-114`, `MoqLiteNestsSpeaker.kt:160-181` |
| `hang` "legacy" container: each frame is `varint(timestamp_us) + raw_opus_packet`                                                    | ✅       | `NestMoqLiteBroadcaster.kt:212-345` (encode), `MoqLiteNestsListener.kt:279-285` (decode strip) |

The team's `2026-04-26-moq-lite-gap.md` (✅ DONE banner) holds — every
phase claim is still accurate against the live source.

## Gap matrix

Severity legend (matches the prior QUIC audit):
- 🔴 **High** — wire-incompatible with the reference relay; audio
  doesn't work.
- 🟡 **Medium** — works against the reference relay today but
  violates spec; future relay versions may break.
- 🟦 **Low** — diagnostic / observability gap; no functional impact.

| #  | Spec § / Behavior                                                                  | Severity | Gap                                                                                                                                                                                                                                                                                                                                                                | Evidence (file:line)                                                  | Status |
| -- | --------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ------ |
| H0 | (none — no wire-incompatible items found)                                          | 🔴       | —                                                                                                                                                                                                                                                                                                                                                                  | —                                                                     | n/a    |
| M1 | `Publisher::serve_group` priority parity                                           | 🟡 → ✅   | We assigned each new group `priority = sequence (i32 cast)`, ignoring `track.priority` and never re-prioritising in flight. kixelated computes `let priority = priority.insert(track.priority, sequence); stream.set_priority(priority.current())` (per `rs/moq-lite/src/lite/publisher.rs::serve_group`). For our single Opus track at 1 group/sec the difference was unobservable (newer-first ordering still held). | `MoqLiteSession.kt:openGroupStream` (pre-fix)                         | **fixed in this audit** — see `Fix #3` |
| M2 | `STOP_SENDING` for single-group cancel                                             | 🟡       | The Lite-03 spec lets a receiver cancel a specific group via `STOP_SENDING` on its uni stream. We close the consumer-facing frames channel only — the publisher's uni stream stays open until natural FIN or transport drop. Practical impact: the publisher wastes a tiny amount of bandwidth on frames the listener will discard; not user-visible. The `:quic` `QuicStream.stopSending(errorCode)` API exists, but isn't exposed through `WebTransportReadStream`. | `MoqLiteSession.kt:670-675` (no stopSending), `WebTransportSession.kt:103-107` (read interface lacks stopSending) | open (deferred — needs `:quic` interface extension; user prompt locked `:quic`) |
| M3 | `RESET_STREAM` with `Error::to_code()`                                             | 🟡       | All error / cancel paths today FIN gracefully via `runCatching { bidi.finish() }`. The Lite-03 spec says errors on any stream are conveyed by `RESET_STREAM(application_error_code = Error::to_code() u32)`. Practical impact: the peer can't tell "I'm done with this stream" apart from "this stream errored" — the wrapper falls back to flow-end heuristics. No moq-lite-session error path actually wants to send a coded reset today (Drop replies use FIN per spec; transport drops surface as flow-end). | `MoqLiteSession.kt` everywhere `runCatching { …finish() }` is used; no `reset(code)` calls anywhere | open (deferred — interface extension without consumer = dead code) |
| M4 | AnnouncePlease prefix-mismatch falls back to full suffix                           | 🟡 → ✅   | When the relay opened an Announce bidi with `prefix="X"`, our publisher emitted `Active(suffix=ourFullSuffix)` even when our suffix didn't start with `X`. The relay would observe an Active update for a broadcast it didn't ask about. In production the relay always asks for `prefix=""`, so this never bit empirically — but it's a spec violation. | `MoqLiteSession.kt:841-852` (pre-fix)                                 | **fixed in this audit** — see `Fix #1` |
| M5 | Inbound Subscribe doesn't validate broadcast field                                 | 🟡 → ✅   | When the relay opened a Subscribe bidi, we matched on `track` only, never checking `sub.broadcast == publisher.suffix`. A relay (or peer) could subscribe to broadcast `"otherPubkey"` on our connection and we'd happily route OUR audio to them. The production relay routes correctly, so this never bit empirically — but it's a spec violation. | `MoqLiteSession.kt:861-898` (pre-fix)                                 | **fixed in this audit** — see `Fix #2` |
| M6 | Goaway body decoding + migration handler                                           | 🟡 → ✅   | We recognise `ControlType::Goaway = 5` and FIN cleanly. WebFetched the kixelated reference (`rs/moq-lite/src/lite/{stream,client}.rs`): **Goaway has no body schema in moq-lite Lite-03** — it's a single ControlType byte with no payload, not even a migration URL. Our existing handler (recognise, log, FIN) is the canonical implementation; "no body decode" was never a gap. | `MoqLiteSession.kt:973-990`, `MoqLiteControlCodes.kt:50-58`           | **closed in this audit** — see `M6 closure` |
| L1 | Lite-04 ALPN constant defined but codec is Lite-03 only                            | 🟦       | `MoqLiteAlpn.LITE_04 = "moq-lite-04"` exists for forward-compat documentation but is never advertised. The codec doesn't implement Lite-04's reshaped `Announce.hops` (varint count → `OriginList`), `AnnounceInterest.exclude_hop`, or `Probe.rtt`. This is intentional + clearly documented. | `MoqLiteAlpn.kt:25-58`, `QuicWebTransportFactory.kt:94-113`           | open (deferred — significant codec rewrite, no current relay forces it) |
| L2 | SubscribeOk always echoes `null/null` for startGroup/endGroup                      | 🟦       | Per spec the publisher MAY narrow the subscriber's requested group bounds. We always reply with `(startGroup=null, endGroup=null)` regardless of the request. Audio rooms are live-only and the listener always asks "from latest", so the difference is meaningless in this product. | `MoqLiteSession.kt:911-921`                                           | open (won't fix — no functional impact for live audio) |
| L3 | No periodic Probe loop on subscriber side                                          | 🟦       | We respond to Probe bidis (with a single bitrate hint, then FIN) but never initiate Probe ourselves as a subscriber. moq-lite Lite-03 lets subscribers periodically open Probe bidis to nudge the publisher into emitting fresh bitrate hints; for fixed-rate Opus audio we don't need ABR, so this is a deliberate omission. | `MoqLiteSession.kt:925-944`                                           | open (won't fix — no consumer for the API) |
| L4 | Stream-priority overflow guard is a saturating cast                                | 🟦       | Pre-fix, `setPriority(sequence.coerceAtMost(Int.MAX_VALUE).toInt())` saturated at `Int.MAX_VALUE` (≈ 71 yrs at 1 grp/sec). After the M1 fix, sequence saturates at the 24-bit boundary (≈ 6 days within a single track) but the per-track high byte keeps cross-track ordering intact. Defensive only; production sessions cycle on JWT refresh every 9 min. | `MoqLiteSession.kt:openGroupStream` (post-M1)                         | closed (subsumed by M1 fix) |
| L5 | Inbound bidi pump captures `publishersSnapshot` at bidi-arrival time               | 🟦 → ✅   | If a new publisher was added to the session (`session.publish(track="…")`) AFTER an inbound bidi opened, the dispatcher couldn't see it. In practice both nests publishers (`audio/data` and `catalog.json`) register before any subscriber arrives, so this never bit. Tightened anyway: snapshot is now read at first-byte dispatch time. | `MoqLiteSession.kt:781-790` (pre-fix)                                 | **fixed in this audit** — see `Fix #4` |

## Specifically checked items (per audit-prompt request)

- **Stream priorities + the "stream cliff":** the priority assignment
  drains newer groups first (sequence is monotonically increasing,
  higher value = higher priority per `:quic`'s `QuicStream.priority`
  contract). The stream cliff investigation (`2026-05-01-quic-stream-
  cliff-investigation.md`) traced the production cliff to TWO
  separate root causes, neither of which was a moq-lite priority
  violation: (a) `:quic` not emitting `MAX_STREAMS_UNI` (real bug,
  fixed in `:quic`); (b) the production relay's per-subscriber
  forward-stream-rate ceiling (≈40 streams/sec) being lower than our
  `framesPerGroup=1` push rate of 50 streams/sec. The mitigation
  (`framesPerGroup=50` in production / `=5` in interop tests) is a
  cadence tuning, not a spec violation. Verified: under the current
  default the listener and speaker both stream cleanly for
  multi-minute sessions in production logs.
- **Group lifetime / stream retirement:** out of scope per audit
  prompt (`:quic` is locked). At the moq-lite layer we're confident
  groups are short-lived (one per `framesPerGroup * 20ms` =
  `framesPerGroup * 0.02s`); each ends in QUIC FIN, and the
  per-publisher `currentGroup` reference is dropped on every
  `endGroup` so the GC root chain doesn't pin retired streams.
- **Subscribe / Announce flow:** verified end-to-end — see
  `MoqLiteSessionTest.subscribe_writes_request_and_returns_handle_on_ok`
  + `…publisher_acks_subscribe_and_pushes_group_data_on_uni_stream` +
  `…publisher_replies_to_announcePlease_with_active_announce`.
  Wire-shape matches the byte-level reference in
  `2026-04-26-moq-lite-gap.md`.
- **Authentication:** moq-lite Lite-03 has no auth message inside the
  WT session — auth is purely the WT-CONNECT JWT in `?jwt=` query.
  Verified: `MoqLiteSession.kt` has zero auth references; all auth
  lives in `OkHttpNestsClient.mintToken` and the WT factory's URL
  construction.
- **Reconnect:** moq-lite has no reconnect message; on transport
  drop the `connectReconnectingNests*` wrapper opens a new WT
  session, mints a new moq-lite session, and re-issues subscribes
  (`ReconnectingNestsListener.kt:317-465`). Speaker side hot-swaps
  the publisher reference inside a long-running broadcaster
  (`NestMoqLiteBroadcaster.swapPublisher`), continuing the group
  sequence across the swap so kixelated/hang's `Container.Consumer.#run`
  doesn't drop our post-swap groups.
- **Best-effort vs reliable:** verified reliable. The previous
  `bestEffort=true` shape was reverted (see comment block at
  `MoqLiteSession.kt:1024-1039`); kixelated's reference uses
  reliable QUIC streams in `Publisher::serve_group`. Match.

## What's deliberately deferred

1. **STOP_SENDING + RESET_STREAM (M2 + M3).** The QUIC layer exposes
   `QuicStream.stopSending(errorCode)` and `resetStream(errorCode)`,
   but neither is surfaced through the WebTransport read/write
   interfaces in `WebTransportSession.kt`. Adding either means:
   extending `WebTransportReadStream` / `WebTransportWriteStream`
   with `stopSending(code)` / `reset(code)`, extending `:quic`'s
   `StrippedWtStream` with closures (touches the locked QUIC
   layer), plumbing through the QUIC adapter, and wiring error-code
   constants in moq-lite. Since the production relay tolerates
   graceful FIN as "I'm done" without complaining, **and no
   moq-lite-session error path actually wants to send a coded reset
   today** (Drop replies use FIN per spec; transport drops surface
   as flow-end), the interface extension would land as dead code.
   Defer until either the relay starts caring or we ship a
   listener-driven group-cancel feature. The user's audit prompt
   explicitly locked `:quic`, which forecloses M2/M3 in this
   session anyway.

2. **Lite-04 codec (L1).** Tracked in `MoqLiteAlpn.kt:50-56`.
   Lite-04 reshapes `Announce.hops` (varint count → `OriginList`),
   adds `AnnounceInterest.exclude_hop`, and adds `Probe.rtt`. None
   of the Lite-04 features are required by the production
   nostrnests relay. Defer until either the relay phases out
   Lite-03 or we need Lite-04-only features.

3. **Optional SubscribeOk narrowing (L2) + subscriber-driven Probe
   (L3).** Won't fix — fixed-rate Opus + live-only audio rooms
   have no use case for either. The publisher MAY narrow
   `startGroup`/`endGroup` per spec but we have no group history
   to narrow to; the subscriber MAY probe the publisher for a
   bitrate hint but our encoder is fixed-rate so the hint never
   changes.

## Closed in this audit (no fix needed)

### M6 closure — Goaway has no body in moq-lite Lite-03

A second WebFetch pass against
`https://raw.githubusercontent.com/kixelated/moq/main/rs/moq-lite/src/lite/stream.rs`
and `…/client.rs` confirmed: **`ControlType::Goaway = 5` is a bare
discriminator with no payload schema in moq-lite Lite-03.** No URL,
no preferred-relay address, no error code — it's just the byte. The
"no body decode" comment in our existing handler
(`MoqLiteSession.kt:973-990`) was never a gap; there was nothing to
decode. The wrapper-layer `connectReconnectingNests*` already absorbs
the eventual hard disconnect, and a Goaway with no body has nothing
more to say than "I'm shutting down, expect FIN." Our recognise + log
+ FIN behavior is the canonical implementation.

If a future Lite-04+ revision adds a Goaway body (URL, etc.) we'd
need to decode it — that becomes part of the L1 Lite-04 codec work.

## Fixes shipped in this audit

### Fix #1 — Don't emit Active for non-matching AnnouncePlease prefix (M4)

When the relay opened an Announce bidi with a `prefix` that didn't
match our broadcast suffix, we'd fall through `MoqLitePath.stripPrefix`'s
null result and emit `Active(suffix=ourFullSuffix, hops=0)` anyway,
falsely advertising our broadcast under a prefix the subscriber didn't
ask for. The reference Rust implementation
(`rs/moq-lite/src/lite/announce.rs::Producer`) only emits Active
updates for broadcasts whose path starts with the requested prefix.

Production never hit this because the relay's announce bidi to us
always asks for `prefix=""` — but a future relay version (or a
direct peer-to-peer subscriber asking about a specific room) would
see ghost broadcasts under their requested prefix.

The fix: when `MoqLitePath.stripPrefix(please.prefix, ourSuffix)`
returns null, FIN the announce bidi without writing any Announce.
Subscriber sees a clean end-of-flow.

Regression test:
`MoqLiteSessionTest.publisher_skips_announce_when_announce_please_prefix_does_not_match`.

### Fix #2 — Reject inbound Subscribe whose broadcast doesn't match our suffix (M5)

When the relay opened a Subscribe bidi, we matched the requested
`track` against our publisher set but never checked
`sub.broadcast == ourSuffix`. A relay (or peer) could open a
Subscribe with broadcast `"otherPubkey"` on our connection and we'd
route OUR audio to them.

Production never hit this because moq-rs's relay routes Subscribe
messages to the specific publisher whose broadcast matches the
requested path — but a buggy or malicious peer connecting to us
directly would have been able to siphon our audio under any
broadcast name.

The fix: in the Subscribe arm of `handleInboundBidi`, check
`sub.broadcast == publisher.suffix` (after path normalisation —
both inputs already are, but defensive normalize on the SUBSCRIBE
side too in case a peer skipped it). If mismatch, reply
`SubscribeDrop(errorCode=BROADCAST_DOES_NOT_EXIST,
reason="<requested> not published on this session (we publish
<ours>)")` and FIN.

Regression test:
`MoqLiteSessionTest.publisher_replies_subscribeDrop_when_broadcast_does_not_match`.

### Fix #3 — Pack trackPriority + sequence into stream priority (M1)

`Publisher::serve_group` in kixelated's reference
(`rs/moq-lite/src/lite/publisher.rs`) calls
`priority.insert(track.priority, sequence)` and feeds the resulting
position into `stream.set_priority`. The `PriorityHandle` orders
streams first by `track.priority u8` (higher track = drains ahead
under congestion), then by group `sequence` within a track (newer =
drains ahead).

Pre-fix we passed raw `sequence.toInt()` directly to
`uni.setPriority`, ignoring the per-track byte. For our single-Opus-
track production case this was unobservable — newer-first ordering
held by sequence monotonicity — but a future multi-track broadcast
(audio + companion catalog / status track) would have starved the
lower-rate track the moment audio's outbound queue got congested.

The fix: add `trackPriority: Int = DEFAULT_TRACK_PRIORITY` parameter
to `MoqLiteSession.publish()`; store on `PublisherStateImpl`; bit-pack
in `openGroupStream` as
`((trackPriority and 0xFF) shl 24) or (sequence.toInt() and 0x00FF_FFFF)`.

`DEFAULT_TRACK_PRIORITY = 0x80` matches the existing subscriber-side
`DEFAULT_PRIORITY` midpoint, so all existing call sites keep their
prior behavior. The 24-bit sequence window is ample (≈ 6 days at
1 grp/sec, beyond which all newer groups within a single track tie
— but still beat older groups of any LOWER-priority track via the
top byte).

Test seam: `FakeWebTransport.openUniStream` now records the most-
recent `setPriority` value via a shared `AtomicInteger` cell that the
peer-side `FakeReadStream` exposes as `lastSetPriority`. Lets the
new regression test verify the bit-pack formula on the actual
peer-side read stream rather than peeking into private state.

Regression test:
`MoqLiteSessionTest.publisher_packs_trackPriority_and_sequence_into_setPriority_value`.

### Fix #4 — Refresh publisher list per-dispatch on inbound bidi (L5)

`handleInboundBidi` previously snapshotted the publisher list at the
TOP of the function (= bidi-arrival time) and used that snapshot for
the rest of the bidi's lifetime. A publisher registered between
bidi-open and the first inbound chunk would miss the dispatcher's
view, even though the publisher was already live in
`activePublishers` by the time we needed to dispatch.

Practical impact today: zero — both nests publishers register from
`MoqLiteNestsSpeaker.startBroadcasting` before the relay's SUBSCRIBE
bidi for either track lands. The ~few-ms gap is below the network
round-trip floor.

But the contract is narrower if we read the list at first-byte time:
we then see every publisher that was registered up to the moment we
needed to make a routing decision. Move the publishers fetch from
the function-top to inside the `if (!dispatched)` block, after the
control-type byte has been read. On empty publisher list (the case
we previously short-circuited at function-top), we now FIN cleanly so
the peer's wait resolves instead of hanging on an idle bidi.

No new regression test — exercised end-to-end by the existing
publisher tests, which now pass against the freshness-aware
dispatcher.

## Build / test verification

Baseline `:nestsClient:jvmTest` was green at audit start
(140 tests). After both fixes + regression tests, still green.
Default-mode tests run in seconds; the `-DnestsInterop=true` Docker
suite is unchanged by these fixes (they only tighten our existing
publisher-side behaviour against malformed input the production
relay never sends).

## Pointers

- Wire spec + IETF gap (DONE banner): `2026-04-26-moq-lite-gap.md`.
- Stream cliff investigation (production-fixed):
  `2026-05-01-quic-stream-cliff-investigation.md`.
- Frames-per-group reconciliation (production vs interop):
  `2026-05-07-framespergroup-reconciliation.md`.
- Audio-rooms completion plan: `2026-04-26-audio-rooms-completion.md`
  (will reference this audit in its compliance section).
- Reference Rust impl: `https://github.com/kixelated/moq/tree/main/rs/moq-lite/src/`.
