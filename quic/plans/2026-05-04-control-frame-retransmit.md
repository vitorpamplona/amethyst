# Control-frame retransmit for `:quic` — implementation plan

**Status:** **shipped 2026-05-05** on branch `claude/fix-nest-audio-display-3chAG`.
Steps 1–9 landed as planned; the deferred follow-ups (STREAM data, CRYPTO,
RESET_STREAM / STOP_SENDING / NEW_CONNECTION_ID) all shipped on top, plus an
audit cleanup pass. See [Implementation log](#implementation-log) at the end
for the full commit list.

## Why

Production tracing against `moq.nostrnests.com` (commits `36c707f` →
`c3d6cad` on `claude/fix-nest-audio-display-3chAG`) showed the audio
cliff lands at exactly the moment we emit our first `MAX_STREAMS_UNI`
extension at the half-window threshold. One emit, one packet, one
loss on the wire — and because `:quic` does not retransmit ack-eliciting
frames, the relay never sees the bump, silently stops sending, and our
QUIC state stays in split-brain (`udpRecvDatagrams` frozen) until the
listener manually disconnects.

The current shipping fix (`QuicConnectionConfig.initialMaxStreamsUni
= 1_000_000`) sidesteps the bug by advertising a cap so high the
half-window threshold doesn't trip until ~13.9 hours of audio. That
gets us multi-hour Nests but leaves the underlying frailty in place:
*every* ack-eliciting control frame we send today is one wire-loss
away from causing a silent stall. `MAX_DATA`, `MAX_STREAM_DATA`,
`RESET_STREAM`, `STOP_SENDING`, `NEW_CONNECTION_ID` — all have the
same exposure.

The fix that browser-grade QUIC stacks have and we don't:
RFC 9002 §6 loss detection + per-frame retransmit.

## Reference

We follow Firefox's [neqo](https://github.com/mozilla/neqo) design,
not Chrome's quiche, because:

- neqo's typed-token shape (a `RecoveryToken` discriminated union with
  one variant per retransmittable frame) maps onto Kotlin sealed
  classes naturally and matches the existing `Frame` /
  `MoqLiteControl…` patterns in the codebase.
- quiche's monotonic-control-frame-id deque is more compact in C++
  but loses compile-time safety in Kotlin; with its cost-of-bug
  history (a single `kInvalidControlFrameId` sentinel typo would
  silently break retransmit) it's not worth the conciseness.
- Both are correct per RFC 9002, both are interop-tested daily; the
  algorithm is identical, only the bookkeeping shape differs.

Specific neqo files we mirror:

| neqo (Rust) | `:quic` (Kotlin) |
|---|---|
| `neqo-transport/src/recovery/token.rs` | new `quic/connection/recovery/RecoveryToken.kt` |
| `neqo-transport/src/recovery/sent.rs` | new `quic/connection/recovery/SentPacket.kt` |
| `neqo-transport/src/recovery/mod.rs` | extend `QuicConnection` + new `QuicLossDetection.kt` |
| `neqo-transport/src/streams.rs` (lost dispatch) | new `QuicConnection.onTokensLost()` |
| `neqo-transport/src/fc.rs` (`frame_lost` flag) | extend `QuicConnection.advertisedMaxStreamsUni` etc. with a `pending` companion |

## Scope

**In scope (this plan):** retransmit for the receive-side flow-control
frames that are RFC-9002 ack-eliciting and cheap to make idempotent:

- `MAX_STREAMS_UNI`
- `MAX_STREAMS_BIDI`
- `MAX_DATA`
- `MAX_STREAM_DATA`

These are the frames that, when lost, can silently wedge the
connection. They're also the frames where the retransmit semantics
are simplest: re-emit if the sent value still matches our current
view; otherwise the newer-value emission supersedes.

**Out of scope (separate follow-ups):**

- `STREAM` data retransmit. Audio rooms tolerate gaps (Opus is
  best-effort streaming), and adding STREAM retransmit means
  rewriting `SendBuffer` to retain bytes until ACK rather than
  release on send. Tracked separately.
- `RESET_STREAM`, `STOP_SENDING`, `NEW_CONNECTION_ID`,
  `RETIRE_CONNECTION_ID`, `STREAMS_BLOCKED`, `DATA_BLOCKED`: not
  exercised by the moq-lite workload today. Add when needed.
- `CRYPTO` retransmit (handshake bytes). Important for handshake
  reliability but out of scope for the audio-cliff fix; we'd inherit
  this from a full RFC 9002 pass later.
- Congestion control (NewReno / CUBIC / BBR). Independent concern;
  `:quic` has no CC at all today, and adding it is its own multi-day
  project.
- 0-RTT, connection migration, multipath. We don't use any of these.

## Architecture

### 1. `RecoveryToken`

A sealed class enumerating each retransmittable frame type. Mirrors
neqo's `StreamRecoveryToken` enum at `recovery/token.rs:21`.

```kotlin
sealed class RecoveryToken {
    object Ack : RecoveryToken()  // tracked but not retransmitted
    data class MaxStreamsUni(val maxStreams: Long) : RecoveryToken()
    data class MaxStreamsBidi(val maxStreams: Long) : RecoveryToken()
    data class MaxData(val maxData: Long) : RecoveryToken()
    data class MaxStreamData(val streamId: Long, val maxData: Long) : RecoveryToken()
}
```

### 2. `SentPacket`

Per-packet metadata retained until the packet is ACK'd or declared
lost. Mirrors neqo's `recovery/sent.rs::Packet`.

```kotlin
data class SentPacket(
    val packetNumber: Long,
    val sentAtMillis: Long,
    val ackEliciting: Boolean,
    val sizeBytes: Int,
    val tokens: List<RecoveryToken>,
)
```

Held in a per-pn-space `MutableMap<Long, SentPacket>` on
`QuicConnection`. Three spaces: Initial, Handshake, Application.

### 3. Send path: emit + register

In `QuicConnectionWriter.appendFlowControlUpdates`, when we emit a
`MaxStreamsFrame` (or any retransmit-eligible control frame), we
also build a `RecoveryToken` and append it to the per-packet token
list. `buildApplicationPacket` returns `(packetBytes, tokens, pn,
ackEliciting)`. `drainOutbound` records a `SentPacket` in the
application space's map keyed by packet number.

### 4. ACK path: drop tokens

Existing `QuicConnectionParser` ACK handling at line 165
(`AckFrame -> state.ackTracker.purgeBelow(...)`) extends to also
remove the corresponding `SentPacket` entries from the map. Tokens
go away silently — they were delivered.

### 5. Loss detection

A new `QuicLossDetection` per pn-space, called from the writer or
on a timer. RFC 9002 §6.1 thresholds:

- Packet threshold: any sent packet with `pn < largest_acked - 3`
  is lost.
- Time threshold: any sent packet with `sent_time + (max_rtt *
  9/8) < now` is lost (where `max_rtt = max(smoothed_rtt,
  latest_rtt)`).

When a packet is declared lost: pull its `tokens`, dispatch each to
its `onLost` handler, and remove it from the sent map.

### 6. Loss dispatch: `onLost(token)`

Mirrors neqo's `streams.rs::lost()` and `fc.rs::frame_lost()`.

```kotlin
fun onLost(token: RecoveryToken) {
    when (token) {
        is RecoveryToken.MaxStreamsUni -> {
            // Only flag pending if we haven't since extended further.
            // The newer value would supersede this one anyway.
            if (token.maxStreams == advertisedMaxStreamsUni) {
                pendingMaxStreamsUni = token.maxStreams
            }
        }
        is RecoveryToken.MaxStreamsBidi -> { /* symmetric */ }
        is RecoveryToken.MaxData -> { /* symmetric */ }
        is RecoveryToken.MaxStreamData -> { /* per-stream symmetric */ }
        RecoveryToken.Ack -> { /* not retransmittable */ }
    }
}
```

The `if (token.maxStreams == advertisedMaxStreamsUni)` guard exactly
mirrors `fc.rs::frame_lost`'s `if (maximum_data == self.max_allowed)`
check at line 322. Without it, we'd resurrect stale superseded
extensions.

### 7. Re-emit: drain `pending*` on next write

`appendFlowControlUpdates` checks `pendingMaxStreamsUni` first;
if non-null, emit that value (with a fresh token, registered on the
new packet) and clear pending. Only when there's no pending
retransmit does it run the normal half-window threshold check.

### 8. PTO (Probe Timeout)

RFC 9002 §6.2. When no ack-eliciting packets have been ACK'd within
`PTO = smoothed_rtt + max(4 * rttvar, kGranularity) + max_ack_delay`,
the connection has lost contact. Send a PING packet to elicit an
ACK. PTO doubles on consecutive expirations; resets on ACK.

Drives loss detection forward when the peer has gone quiet — exactly
the failure mode where today's connection goes dead.

## Test inventory mirrored from neqo

These are the tests that already exist in neqo and that we must port
to land equivalent coverage. Each row is one test we owe.

### Token-level: receiver flow control (mirror `fc.rs`)

Total: **9 tests** to mirror.

| neqo test (file:line — fc.rs) | Asserts | Our equivalent |
|---|---|---|
| `lost_blocked_resent` | After STREAMS_BLOCKED loss, frame_pending re-set | `ReceiverFlowControlTest.lost_blocked_resent` |
| `lost_after_increase` | If newer extension was already sent, lost old one is *not* re-sent | `ReceiverFlowControlTest.lost_after_increase` |
| `lost_after_higher_blocked` | Same, applied to STREAMS_BLOCKED | `ReceiverFlowControlTest.lost_after_higher_blocked` |
| `need_max_allowed_frame_after_loss` | `frame_lost(N)` where N == current limit re-flags pending | `ReceiverFlowControlTest.maxStreamsLostMatchesCurrent_resent` |
| `no_max_allowed_frame_after_old_loss` | `frame_lost(stale)` after newer sent does NOT re-flag | `ReceiverFlowControlTest.maxStreamsLostStaleAfterNewer_dropped` |
| `multiple_retries_after_frame_pending_is_set` | Repeated `retire(...)` keeps `frame_needed` true; `frame_sent(N)` clears | `ReceiverFlowControlTest.multipleRetiresMaintainFramePending` |
| `new_retired_before_loss` | After loss following further `retire()`, the new (higher) limit is sent | `ReceiverFlowControlTest.lossAfterRetireUsesNewerLimit` |
| `force_send_max_allowed` | A small first-retire below threshold does not flag | `ReceiverFlowControlTest.smallFirstRetireDoesNotFlag` |
| `set_max_active_equal_does_not_set_frame_pending` | Setting same max-active as before does nothing | `ReceiverFlowControlTest.setMaxActiveEqualDoesNothing` |

### Recovery-level: loss-detection algorithm (mirror `recovery/mod.rs`)

Total: ~20 tests; we mirror the subset that doesn't require CRYPTO/handshake-space testing (we're scoped to Application space).

| neqo test (recovery/mod.rs) | Asserts | Our equivalent |
|---|---|---|
| `remove_acked` | ACK removes the right packet numbers from sent map | `QuicLossDetectionTest.ackRemovesPackets` |
| `time_loss_detection_gap` | Packet older than `max_rtt * 9/8` declared lost | `QuicLossDetectionTest.timeThresholdMarksLost` |
| `time_loss_detection_timeout` | Loss detection schedules wake-up at the right deadline | `QuicLossDetectionTest.timeThresholdSchedulesTimer` |
| `big_gap_loss` | Packet threshold (≥ 3 newer ACK'd) declares lost | `QuicLossDetectionTest.packetThresholdMarksLost` |
| `loss_timer_set_on_pto` | Timer scheduled when PTO arms | `QuicLossDetectionTest.lossTimerSetOnPto` |
| `loss_timer_expired_on_timeout` | Expired timer triggers loss callbacks | `QuicLossDetectionTest.expiredTimerTriggersLoss` |
| `loss_timer_cancelled_on_ack` | New ACK for in-flight packet cancels pending PTO | `QuicLossDetectionTest.ackCancelsPto` |
| `pto_works_basic` | PTO emits a probe packet | `PtoTest.basic` |
| `pto_works_full_cwnd` | PTO works even at congestion-window full | `PtoTest.fullCwnd` |
| `pto_works_ping` | Probe is a PING when nothing else to send | `PtoTest.probesWithPing` |
| `ack_after_pto` | ACK clears PTO state correctly | `PtoTest.ackResetsPtoCount` |
| `pto_retransmits_previous_frames_across_datagrams` | PTO probe carries the lost-but-pending frames, not just PING | `PtoTest.probeIncludesPendingFrames` |
| `pto_state_count` | PTO count doubles each fire, resets on ACK | `PtoTest.exponentialBackoff` |
| `loss_recovery_crash` | Two simultaneous loss events don't crash | `QuicLossDetectionTest.concurrentLossesNoCrash` |
| `lost_but_kept_and_lr_timer` | Lost packet retained in book-keeping until release timer | `QuicLossDetectionTest.lostPacketRetainedForReleaseWindow` |
| `loss_time_past_largest_acked` | Oldest pending loss-time is the one that matters | `QuicLossDetectionTest.oldestLossTimeIsScheduled` |
| `ack_for_unsent` | ACK referencing unsent PN closes the connection per RFC | `QuicLossDetectionTest.ackForUnsentClosesConnection` |
| `duplicate_ack_does_not_update_largest_acked_sent_time` | Re-ACK is a no-op | `QuicLossDetectionTest.duplicateAckIsNoop` |
| `should_probe_exact_boundary` | PTO fires exactly at deadline, not before | `PtoTest.firesAtBoundaryNotBefore` |
| `ack_only_boundary` | An ACK-only packet doesn't arm PTO | `PtoTest.ackOnlyDoesNotArmPto` |

### Connection-level integration (mirror `connection/tests/recovery.rs` + `connection/tests/stream.rs`)

Total: ~10 tests covering the full happens-after of "frame sent →
packet lost → frame re-emitted → ACK'd".

| neqo test (file) | Asserts | Our equivalent |
|---|---|---|
| `pto_works_basic` (recovery.rs) | End-to-end: lose a packet with control frames, observe retransmit | `QuicConnectionRetransmitTest.maxStreamsUniLossEmitsRetransmit` |
| `pto_works_ping` (recovery.rs) | PTO PING fires after silence, peer ACKs it | `QuicConnectionRetransmitTest.silentPathPtoEmitsPing` |
| `pto_retransmits_previous_frames_across_datagrams` | PTO carries the pending control-frame retransmits | `QuicConnectionRetransmitTest.ptoCarriesPendingMaxStreamsBump` |
| `lost_but_kept_and_lr_timer` (recovery.rs) | Timer eviction of lost-tracking entries doesn't break re-emit | `QuicConnectionRetransmitTest.lostTrackingEvictionLeavesPendingIntact` |
| `sending_max_data` (stream.rs) | MAX_DATA emitted, ACK observed, no spurious re-emit | `QuicConnectionRetransmitTest.maxDataAckedNoSpuriousRetransmit` |
| `max_data` (stream.rs) | MAX_DATA flow control end-to-end | `QuicConnectionRetransmitTest.maxDataIncreasePropagates` |
| `stream_data_blocked_generates_max_stream_data` | Receiver emits MAX_STREAM_DATA on blocked signal, retransmits if lost | `QuicConnectionRetransmitTest.maxStreamDataLossEmitsRetransmit` |

### Codec-level: token serialisation round-trip

Add: token equality, token data-class hashCode/equals correctness, token-list encode/decode-by-debug for log diagnosis. ~3 tests.

### Total

~50 tests. neqo has ~125 tests in this area; we're scoped to ~40% of
neqo's coverage because we're not reimplementing CRYPTO retransmit,
0-RTT, handshake-space PTO, etc.

## File-by-file implementation order

Each step is its own commit; each commit must compile + pass tests
before moving on.

### Step 1: types only (no behavior)

- New: `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/recovery/RecoveryToken.kt`
- New: `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/recovery/SentPacket.kt`
- Test: `quic/src/commonTest/.../recovery/RecoveryTokenTest.kt`
- No other code touched. Compiles, types ready for next step.

### Step 2: track sent packets in `QuicConnection`

- Add `sentApplicationPackets: MutableMap<Long, SentPacket>` to `QuicConnection.application`.
- `QuicConnectionWriter.buildApplicationPacket` builds a `tokens: List<RecoveryToken>` alongside its `frames`, returns both.
- On packet emission, store a `SentPacket` keyed by PN.
- Existing tests still pass; new behavior dormant (no loss detection yet).

### Step 3: drain on ACK

- `QuicConnectionParser.AckFrame` handler walks ACK ranges, removes `SentPacket` entries.
- Test: ACK removes a sent packet's tokens. ACK out of order is fine.

### Step 4: receiver-flow-control `pending*` companion fields

- Add `pendingMaxStreamsUni: Long?`, `pendingMaxStreamsBidi: Long?`, `pendingMaxData: Long?` to `QuicConnection`.
- Add per-stream `pendingMaxStreamData` map (small).
- `appendFlowControlUpdates` drains pending* first, only then runs the normal threshold check.
- Tests: mirror the 9 `fc.rs` tests above. Pure unit-level.

### Step 5: loss detection algorithm

- New: `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/recovery/QuicLossDetection.kt`.
- Implements packet-threshold + time-threshold per RFC 9002 §6.1.
- Hooks into ACK handler: each new ACK runs `detectLost(now)`.
- Tests: mirror the 20 `recovery/mod.rs` tests.

### Step 6: dispatch lost tokens

- `QuicConnection.onTokensLost(tokens)` dispatches each token to its `pending*` field.
- Wire the dispatch into the loss-detection callback.
- Tests: integration — send MAX_STREAMS, drop the packet, verify retransmit happens on next outbound. (~3 tests.)

### Step 7: PTO

- New: `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/recovery/Pto.kt`.
- Implements §6.2 PTO timer. Schedules wake-up; on expiry, emits a PING (if nothing else queued) or re-uses the pending retransmit machinery.
- `QuicConnectionDriver.sendLoop` learns to wake on PTO.
- Tests: mirror the ~7 PTO tests from neqo.

### Step 8: integration test against `FakeWebTransport`

- New: `nestsClient/src/commonTest/.../moq/lite/MoqLiteSessionRetransmitTest.kt` — drops a specific packet between client and server, asserts MAX_STREAMS retransmit lands and audio keeps flowing.
- This is what we couldn't write before; now we can.

### Step 9: revert the cap workaround (optional)

Once the retransmit path is durable, optionally revert
`initialMaxStreamsUni` from 1 000 000 back to a smaller value
(e.g. 1 000) so the bump path actually exercises in production.
Keeps the rolling-extension code path warm and validates retransmit
is healthy. Probably gate this on having shipped the retransmit
work for at least a week without regressions.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Existing `:quic` tests written against "send-and-forget" semantics break | Step 2 is purely additive; existing tests still see send-and-forget behavior because nothing reads the `sentApplicationPackets` map until step 5. |
| Token tracking grows unbounded if ACK never arrives | RFC 9002 §6.5: declare lost on time threshold; lost packets evicted on `kPacketDeclaredLostThreshold` after eviction window. We bound the map by both ACK and loss declarations. |
| RTT estimation is wrong → false-positive losses | Same RTT estimation logic as neqo, conservative defaults (initial RTT 333 ms per RFC 9002 §6.2.2). |
| PTO timer interacts with the existing send loop | Step 7 is the trickiest; bake in conservative tests that simulate slow-network conditions. |
| Memory: at 10 streams/sec a 2-hour session has ~72k tracked sent packets if no ACKs arrive | Bounded by ACK arrival; if ACKs are missing for long enough we'd PTO-out and close. Realistic ACK cadence is sub-second so the map stays small (a few hundred entries). |

## Effort

4–7 days for a full pass with tests. Steps 1–4 are 1–2 days; steps 5–7 are the bulk; step 8 is a day; step 9 is trivial. The test count is the dominant cost — each ported test is ~30 lines of Kotlin.

## Acceptance criteria

- All ~50 ported tests pass.
- `NostrnestsProdAudioTransmissionTest` (existing JVM interop test) continues to pass against production.
- A new `MoqLiteSessionRetransmitTest` simulates packet loss at the moment of `MAX_STREAMS_UNI` emission and confirms audio continues flowing past the threshold.
- Existing `QuicConnectionWriterTest`, `PeerStreamCreditExtensionTest`, etc. unchanged.
- No regression in handshake latency or throughput under steady-state.

## Implementation log

Shipped over 13 commits on `claude/fix-nest-audio-display-3chAG`:

| # | Commit | Subject |
|---|---|---|
| plan | `c246305` | `docs(quic): plan control-frame retransmit subsystem mirroring neqo` |
| 1 | `9e6fa3d` | `feat(quic): step 1 of RFC 9002 retransmit — RecoveryToken + SentPacket types` |
| 2 | `ea15a9a` | `feat(quic): step 2 of RFC 9002 retransmit — record SentPacket per outbound` |
| 3 | `0ced269` | `feat(quic): step 3 of RFC 9002 retransmit — drain SentPacket on ACK` |
| 4 | `2928263` | `feat(quic): step 4 of RFC 9002 retransmit — pending* fields + writer drain` |
| 5 | `1df6441` | `feat(quic): step 5 of RFC 9002 retransmit — loss detection + RTT estimator` |
| 6 | `15a6bfc` | `feat(quic): step 6 of RFC 9002 retransmit — dispatch lost tokens to pending*` |
| 7–9 | `c43c951` | `feat(quic): steps 7, 8, 9 of RFC 9002 retransmit — PTO + integration test + revert workaround` |
| follow-up A | `7f6d908` | `feat(quic): extend RecoveryToken — Stream, Crypto, ResetStream, StopSending, NewConnectionId` |
| follow-up B | `03cfb31` | `feat(quic): rewrite SendBuffer for retain-until-ACK with markAcked/markLost` |
| follow-up C | `f623e88` | `feat(quic): wire STREAM data retransmit — token emission + ACK/loss dispatch` |
| follow-up D | `0c847b4` | `feat(quic): wire CRYPTO retransmit per encryption level` |
| follow-up E | `996ab39` | `feat(quic): emit RESET_STREAM / STOP_SENDING + per-stream retransmit dispatch` |
| perf | `303caa8` | `perf(quic): binary-search SendBuffer overlap + insert (O(log N))` |
| audit | `086a9c7` | `fix(quic): RESET_STREAM/STOP_SENDING first-call-wins + threading contract` |

### What changed vs the plan

The original scope was **only** the receive-side flow-control frames
(`MAX_STREAMS_UNI/BIDI`, `MAX_DATA`, `MAX_STREAM_DATA`). Once the
RecoveryToken / SentPacket / loss-detection scaffolding existed, the
remaining retransmittable frames were a small extension:

- **STREAM data retransmit (B + C).** Required rewriting `SendBuffer`
  from "release on send" to "retain until ACK", with three logical
  regions (`in-flight` / `needs retransmit` / `unsent`) tracked as
  sorted offset ranges. Bytes are released on `markAcked`; lost ranges
  re-prioritise to the front of `takeChunk` via a FIFO retransmit queue.
  Removes the "STREAM truncates silently on loss" item from the
  deferred-work list.
- **CRYPTO retransmit (D).** Same `SendBuffer` machinery applied
  per-encryption-level (Initial / Handshake / Application). Closes the
  handshake reliability gap that previously relied on the driver's PTO
  re-pull-from-CRYPTO hack.
- **RESET_STREAM / STOP_SENDING / NEW_CONNECTION_ID emit + retransmit (E).**
  Public API on `QuicStream` (`resetStream(errorCode)` /
  `stopSending(errorCode)`); writer drain emits with a `RecoveryToken`;
  loss dispatcher re-flags per-stream emit-pending bits; ACK dispatcher
  latches `resetAcked` / `stopSendingAcked` so stale loss tokens don't
  re-emit. NEW_CONNECTION_ID retransmit drains
  `QuicConnection.pendingNewConnectionId` (no public emit API yet —
  `:quic` doesn't rotate connection IDs — but the wiring is in place).

### Performance optimisation

`303caa8` replaced the O(N) full-scan in `SendBuffer.removeOverlap` and
the O(N) middle-insert in `addToInFlight` with a binary-search-based
`firstOverlapIndex` + early-exit walk. Hot-path ACK / loss notification
is now O(log N + k) where k is the number of in-flight ranges actually
overlapping the ACK range (typically 1).

### Audit follow-up

`086a9c7` cleaned up correctness + threading issues found by re-reading
the emit commit:

1. `resetStream` / `stopSending` now no-op on the second call. RFC 9000
   §3.5 pins `finalSize` at first emission; the original "idempotent —
   second call overwrites with newer error code" claim was wrong (a
   retransmit after additional `enqueue` would replay with a larger
   `finalSize`, triggering `FINAL_SIZE_ERROR` on the peer). Two new
   tests — `resetStream_secondCallIsNoOp_finalSizeFrozen`,
   `stopSending_secondCallIsNoOp` — lock the contract.
2. `resetEmitPending`, `resetAcked`, `stopSendingEmitPending`,
   `stopSendingAcked` are now `@Volatile`. The public emit APIs are
   callable from any coroutine while the writer / dispatchers read the
   same fields under `QuicConnection.lock`; volatile gives the
   cross-thread happens-before, and the first-call-wins gate above
   eliminates the only multi-writer race.
3. Stale `SendBuffer` class KDoc claiming O(N) range arithmetic
   refreshed to reflect the actual O(log N + k) cost.
4. `removeOverlap`'s bulk-removal comment toned down — it had claimed
   O(k) per call but `ArrayDeque.removeAt(i)` shifts on every call;
   actual cost is O(k · (size − end + k)) worst case, fine in practice
   because k is 1–2 in steady state.

### Test coverage shipped

The 50 planned tests landed plus the follow-up suites:

- `RecoveryTokenTest`, `SentPacketTest` (codec + equality).
- `ReceiverFlowControlTest` (9 mirrored from neqo's `fc.rs`).
- `QuicLossDetectionTest` (~15 mirrored from `recovery/mod.rs`).
- `PtoTest` (~7 mirrored from `recovery/mod.rs` PTO subset).
- `QuicConnectionRetransmitTest` (integration: lost MAX_STREAMS bump
  re-emits and lands).
- `MoqLiteSessionRetransmitTest` (drops a packet at the
  half-window-threshold MAX_STREAMS_UNI emit; audio keeps flowing).
- `SendBufferRetainUntilAckTest` (14 cases for the retain-until-ACK
  rewrite — ack/loss/split/FIN/compaction).
- `StreamRetransmitTest`, `CryptoRetransmitTest` (token emission + loss
  re-queues bytes).
- `ResetStopSendingEmitTest` (7 cases: emit-and-token, retransmit on
  loss, ack-then-stale-loss-drop, stop-sending emission,
  NEW_CONNECTION_ID retransmit drain, first-call-wins for both APIs).

### Cap-workaround status

Step 9 of the original plan ("revert `initialMaxStreamsUni` from
1 000 000 back to a smaller value once retransmit is durable") landed
in `c43c951`. `QuicConnectionConfig.initialMaxStreamsUni` is now
`10_000L` — large enough to avoid the moq-rs cliff at startup but
small enough that the rolling-extension + retransmit path actually
runs in long sessions. The 1 000 000 emergency value is gone.
