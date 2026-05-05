# Congestion control for `:quic` — implementation plan

**Status:** plan, not started.

## Why (and why this is honestly low-priority)

`:quic` has loss detection (RFC 9002 §6) and per-frame retransmit
(shipped 2026-05-05; see [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md))
but no rate limiter — we send as fast as the application provides
bytes. For our actual production workload, that's fine:

- Audio rooms push ~8 KB/sec (Opus at 64 kbps). Way under any modern
  link capacity. Not a single audio Nest has wedged on rate so far.
- `nostrnests.com` (moq-rs relay) is forwarding, not absorbing — the
  bottleneck is per-subscriber stream-id cap, not bandwidth. The
  retransmit subsystem already addresses that.
- The "stream cliff" investigation
  ([`nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`](../../nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md))
  found nothing CC-shaped — the issues were stream-id exhaustion +
  per-subscriber forward queue overflow, both fixed by other means.

Where it actually matters:

1. **Bufferbloat on slow links.** A user on a degraded mobile connection
   (slow Wi-Fi, train tunnel, congested LTE cell) — without CC we keep
   sending into a full router queue, RTT inflates, and the listener
   experiences stale audio rather than back-pressured cleaner audio.
2. **Multi-flow fairness.** A single audio Nest sharing a slow uplink
   with a download — we'd grab unfair share and the download stalls
   (or vice versa).
3. **Burst handling after a stall.** When a paused listener reconnects,
   the relay forwards a burst of cached frames. Without CC, the burst
   either floods the path or causes immediate loss.
4. **Being a good citizen.** Every other QUIC stack in the wild
   implements CC. Without it we look like a misbehaving client; some
   relays/middleboxes might rate-limit us heuristically.

None of these are urgent, none are user-visible bugs today, and the
retransmit subsystem we just shipped is what the audio cliff actually
needed. **This plan stays open as the natural next item but should not
be prioritised over field-validation of the retransmit work.**

## Reference

We follow [RFC 9002 §7 NewReno](https://www.rfc-editor.org/rfc/rfc9002#section-7)
— the QUIC-flavored NewReno reference algorithm. Three reasons:

- It's the RFC's reference algorithm. Other stacks treat it as the
  baseline; if/when we ever interop-test against quiche/neqo with CC
  enabled, this is the variant they all support and verify.
- It's ~200 lines of Kotlin without pacing — the smallest credible CC
  implementation. Cubic is +200 lines for a modest win; BBR is +1500
  for a much bigger win (and a much bigger maintenance surface).
- Loss-based AIMD pairs naturally with the loss-detection +
  retransmit machinery already in place. Nothing new is needed in
  loss detection itself; CC just consumes the loss signal.

We mirror neqo's `cc/classic_cc.rs` shape because it's the cleanest
NewReno implementation we've found. If we later want CUBIC, neqo's
`cc/cubic.rs` is the obvious follow-up.

## Scope

**In scope (this plan):**

- NewReno per RFC 9002 §7 (slow start + congestion avoidance + recovery).
- Bytes-in-flight tracker.
- Send-side gating: writer checks `bytesInFlight + packetSize <= cwnd`
  before emitting ack-eliciting packets.
- Persistent-congestion handling (RFC 9002 §7.6): N consecutive PTOs
  collapse cwnd to the minimum.

**Out of scope (separate follow-ups):**

- **Pacing** (RFC 9002 §7.7). Audio at 50 packets/sec is already
  paced by the Opus encoder cadence; bursts are ≤ a few packets.
  Add when a real burst-loss problem is observed.
- **CUBIC** ([RFC 9438](https://www.rfc-editor.org/rfc/rfc9438)). Modest
  throughput win on long-RTT paths; not worth the complexity until we
  have a measured throughput regression.
- **BBR** (Google draft). Big throughput / fairness win on
  bottleneck-buffered paths; way out of scope.
- **ECN** (RFC 9000 §13.4 + RFC 9002 §B.4). Requires both the OS
  network stack to set ECN bits and the peer to reflect ECN counts in
  ACK frames. Defer until we hear from a peer that supports ECN.
- **0-RTT cwnd preservation**. We don't speak 0-RTT.
- **HyStart++** (RFC 9406). Slow-start exit heuristic; minor refinement.

## Architecture

### `CongestionController`

New class at `quic/.../connection/recovery/CongestionController.kt`.
Mirrors neqo's `cc/classic_cc.rs`.

```kotlin
class CongestionController(
    private val maxDatagramSize: Int = 1200,
) {
    /** RFC 9002 §B.2 — initial cwnd is min(10 * MSS, max(2 * MSS, 14720)). */
    var congestionWindow: Long = minOf(10L * maxDatagramSize, maxOf(2L * maxDatagramSize, 14720L))
        private set

    /** Sum of sizes of all ack-eliciting in-flight packets. */
    var bytesInFlight: Long = 0L
        private set

    /** ssthresh — initially infinity (= Long.MAX_VALUE). */
    var slowStartThreshold: Long = Long.MAX_VALUE
        private set

    /** RFC 9002 §B.2 — once cwnd >= ssthresh we're in CA. */
    val inSlowStart: Boolean get() = congestionWindow < slowStartThreshold

    /**
     * RFC 9002 §7.3.2 — start of the most recent congestion-recovery
     * period. Loss events with `sentAt <= recoveryStart` don't trigger
     * a fresh halving (we already responded to that loss epoch).
     */
    private var recoveryStartTimeMs: Long? = null

    /** Caller checks before emitting ack-eliciting bytes. */
    fun canSend(packetSize: Int): Boolean = bytesInFlight + packetSize <= congestionWindow

    fun onPacketSent(sentPacket: SentPacket) { /* +bytesInFlight */ }
    fun onPacketAcked(sentPacket: SentPacket) { /* -bytesInFlight + grow cwnd */ }
    fun onPacketLost(sentPacket: SentPacket, nowMs: Long) { /* -bytesInFlight + maybe halve */ }
    fun onPersistentCongestion() { /* cwnd = MIN_CWND, slow-start again */ }
    fun onPacketDiscarded(sentPacket: SentPacket) { /* key-discard path: -bytesInFlight, no cwnd change */ }
}
```

### Send-side gating

`QuicConnectionWriter.drainOutbound` consults the controller before
emitting an ack-eliciting packet.

```kotlin
val packet = buildApplicationPacket(...)
if (packet != null && packet.ackEliciting && !cc.canSend(packet.size)) {
    // Park the packet — return null so the driver waits for an ACK
    // (which will free cwnd) or for the PTO timer to fire.
    return null
}
```

ACK-only packets bypass the gate per RFC 9002 §7.2 ("ACK frames…
do not count toward bytes_in_flight").

PTO probes also bypass — RFC 9002 §6.2 says PTO probes MUST be sent
regardless of cwnd ("an endpoint can send packets in excess of the
congestion window when sending probe packets"). Existing
`pendingPing` flag is the integration point.

### Hook integration

| Existing hook | New CC call |
|---|---|
| `QuicConnectionWriter.buildApplicationPacket` records SentPacket | `cc.onPacketSent(sp)` after recording (only if ackEliciting) |
| `QuicConnectionParser` ACK handler iterates drained packets | `cc.onPacketAcked(sp)` per drained ack-eliciting packet |
| `QuicConnection.onTokensLost` (RFC 9002 §6) iterates lost set | `cc.onPacketLost(sp, nowMs)` per lost ack-eliciting packet |
| `LevelState.discardKeys()` clears Initial/Handshake sentPackets | `cc.onPacketDiscarded(sp)` per cleared packet (decrement only) |
| PTO fires `pendingPing = true` | `cc.consecutivePtoCount` tracking — at N=3, `cc.onPersistentCongestion()` |

### NewReno math

Slow start (`cwnd < ssthresh`):
- On each ACK: `cwnd += acked_bytes`. (Exponential growth — doubles per RTT.)

Congestion avoidance (`cwnd >= ssthresh`):
- On each ACK: `cwnd += MSS * acked_bytes / cwnd`. (Linear growth — +MSS per RTT.)

Loss event (RFC 9002 §7.3.2):
- If `sentPacket.sentAtMs > recoveryStartTimeMs ?: -1`:
  - `recoveryStartTimeMs = nowMs`
  - `ssthresh = cwnd / 2`
  - `cwnd = max(ssthresh, MIN_CWND)`  // MIN_CWND = 2 * MSS
- Else: in same recovery period, don't double-halve.

Persistent congestion (RFC 9002 §7.6):
- `cwnd = MIN_CWND`
- `recoveryStartTimeMs = null`
- Slow-start kicks in again.

## File-by-file plan

Each step compiles + passes existing tests before moving on.

### Step 1: type stub

- New `CongestionController.kt` with the public API + `canSend` (always
  true) + no-op hooks.
- Wired as `QuicConnection.cc: CongestionController`.
- Existing tests still pass; nothing observable changes.

### Step 2: bytesInFlight tracking

- Implement `onPacketSent` + `onPacketAcked` + `onPacketLost` +
  `onPacketDiscarded` to manage `bytesInFlight` only — leave cwnd
  fixed at the initial value.
- Wire from the writer (after SentPacket recording), parser (ACK
  drain loop), `onTokensLost` dispatch, and `LevelState.discardKeys()`.
- Test: `BytesInFlightTrackingTest` — invariants under send / ack /
  loss / discard / mixed.

### Step 3: slow-start growth

- Implement the slow-start branch of `onPacketAcked` (cwnd += acked).
- Test: `SlowStartGrowthTest` — cwnd doubles per RTT in steady-state
  ACK arrival.

### Step 4: congestion-avoidance growth

- Implement the CA branch (cwnd += MSS * acked / cwnd).
- Wire ssthresh — initial Long.MAX_VALUE means we never enter CA
  unless a loss has happened. We'll exercise this in step 5.
- Test: `CongestionAvoidanceGrowthTest` — cwnd grows ≈+MSS per RTT
  once `cwnd >= ssthresh`.

### Step 5: loss reaction

- Implement `onPacketLost` cwnd-halve + recovery-period gating.
- Test: `LossReactionTest` — single loss halves; second loss in same
  period doesn't re-halve; loss after recovery period elapses
  re-halves.

### Step 6: send-side gating

- Wire `cc.canSend(packetSize)` into the writer.
- ACK-only path bypasses (writer already distinguishes; we just need
  to compute the projected packet size).
- PTO probe path bypasses (writer's `pendingPing` branch).
- Test: `SendGatingTest` — driver returns null when bytesInFlight ≥
  cwnd; resumes after ACK frees cwnd.

### Step 7: persistent congestion

- Track consecutive-PTO count (already on QuicConnection as
  `consecutivePtoCount`).
- At N=3 (RFC 9002 §B.6 — `kPersistentCongestionThreshold` = 3 PTOs
  worth of time, simplified as 3 consecutive PTO fires here),
  call `cc.onPersistentCongestion()`.
- Test: `PersistentCongestionTest` — 3 consecutive PTOs collapse cwnd
  to min.

### Step 8: integration test

- New `nestsClient/.../moq/lite/MoqLiteSessionCongestionTest.kt`:
  simulate a 100ms-RTT path with 1% loss; observe cwnd settling
  around the bandwidth-delay product; audio continues without
  unbounded queue growth on either side.
- Maybe also an in-process-pipe variant under `quic/...` that injects
  loss at a rate above what the receiver's ACK can keep up with;
  observe the gate engaging.

### Step 9: documentation

- Update [`2026-04-26-quic-stack-status.md`](2026-04-26-quic-stack-status.md):
  Phase F flips from "done (no CC)" to "done"; "no congestion control"
  removed from deferred-work list.

## Test inventory mirrored from neqo

Total: ~12 unit tests + 2 integration tests = 14 new tests.

| neqo test (cc/classic_cc.rs) | Asserts | Our equivalent |
|---|---|---|
| `init` | initial cwnd matches RFC 9002 §B.2 | `CongestionControllerTest.initialCwnd` |
| `slow_start` | each ACK doubles cwnd in slow-start | `SlowStartGrowthTest.basicGrowth` |
| `slow_start_to_ca` | crossing ssthresh transitions to CA | `SlowStartGrowthTest.crossesSsthreshIntoCa` |
| `ca_growth` | linear growth in CA | `CongestionAvoidanceGrowthTest.linearGrowth` |
| `loss_event_halves` | first loss halves cwnd, sets ssthresh | `LossReactionTest.firstLossHalves` |
| `recovery_period_suppresses_re_halving` | second loss in same period no-op | `LossReactionTest.secondLossInPeriodSuppressed` |
| `loss_after_recovery_re_halves` | loss after recovery period re-halves | `LossReactionTest.lossAfterRecoveryRehalves` |
| `cwnd_floor_min_window` | cwnd never drops below 2 * MSS | `LossReactionTest.cwndFloor` |
| `app_limited_no_growth` | app-limited path freezes cwnd | `AppLimitedTest.noGrowthWhenAppLimited` |
| `bytes_in_flight_tracking` | invariants under send / ack / loss | `BytesInFlightTrackingTest.allHooks` |
| `persistent_congestion_resets_to_min` | 3 consecutive PTOs → min cwnd | `PersistentCongestionTest.threeConsecutivePtos` |
| `key_discard_decrements_in_flight` | key-discard path correctly drops in-flight bytes | `BytesInFlightTrackingTest.keyDiscardDecrements` |
| `gating_blocks_send_at_cap` | writer returns null when at cwnd cap | `SendGatingTest.blocksAtCap` |
| `gating_resumes_after_ack` | ACK frees cwnd, send resumes | `SendGatingTest.resumesAfterAck` |

Plus 2 integration tests as listed in step 8.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Throughput regression on good networks (cwnd starts at 14 KB and grows) | Initial 10 * MSS = ~12 KB. RTT 50 ms steady-state means cwnd doubles every 50 ms; saturation at link bandwidth in seconds. For audio at 8 KB/sec we never approach the cap. |
| Send-gate stalls audio when cwnd shrinks | PTO probe path bypasses cwnd. Worst case: a cwnd-blocked send waits for ACK + RTT. Audio room jitter buffer hides this. |
| Off-by-one in loss-event recovery-period gating | Mirror neqo's `time > recovery_start` strict comparison + tests. |
| Persistent-congestion false trigger | We use the conservative "3 consecutive PTOs" simplification; the strict RFC 9002 §B.6 definition needs a duration check we can add later. Risk is collapsing cwnd unnecessarily — surfaces as audio stall the user can fix by reconnecting. |
| Interaction with existing per-frame retransmit | None expected — retransmit operates on lost-token dispatch; CC operates on packet sizes. They're orthogonal. |
| `SendBuffer.takeChunk` doesn't know cwnd | The gate is at `drainOutbound` granularity — packet-level, not chunk-level. SendBuffer remains unchanged. |

## Effort

Realistic: **3–5 days** of focused work. Steps 1–4 ~1 day; steps 5–7
~2 days; integration test + doc ~1 day; bug-fixing slack 1 day.

## Acceptance criteria

- All ~14 ported tests pass.
- Existing `:quic` test suite unchanged (`QuicLossDetectionTest`,
  `RetransmitIntegrationTest`, `KeyDiscardTest`, etc.).
- `MoqLiteSessionCongestionTest` lossy-path scenario shows cwnd
  settling to a steady-state value rather than growing unboundedly.
- `NestsListenerProdTest` (existing JVM interop test against
  nostrnests.com) continues to pass with the gate wired in.
- No regression in handshake latency or audio start-up time.

## Open questions

- **What's the right `kPersistentCongestionThreshold`?** Simplified
  here as "3 consecutive PTOs without any ACK". Strict RFC version
  uses a duration formula that requires more PTO bookkeeping. Keep it
  simple unless field data suggests we're collapsing too aggressively.
- **MTU.** We use `maxDatagramSize = 1200` everywhere (no PMTUD).
  CC sizes off this constant. If we ever add PMTUD this needs to
  reflow. Track as a note for the PMTUD plan.
- **Should the gate be per-space?** RFC 9002 §B.4 ties cwnd to a
  single congestion controller, not per-pn-space. We follow the RFC.
  Initial / Handshake bytes count toward cwnd until the level is
  discarded; that's correct per the spec.
