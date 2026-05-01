# QUIC stream cliff against nostrnests.com — investigation plan

**Status: ROOT-CAUSED + FIXED.** Our `:quic` client never emitted
`MAX_STREAMS_*` frames to extend the peer-initiated stream-id cap.
[QuicConnectionConfig.initialMaxStreamsUni] (default `100`) was the
*lifetime* maximum the peer could ever open. The relay forwards each
broadcast group as a fresh peer-initiated uni stream, so any
broadcast longer than 100 frames silently truncated at the listener.
Fixed by tracking [QuicConnection.peerInitiatedUniCount] and emitting
`MAX_STREAMS_UNI(newCap)` from [appendFlowControlUpdates] once the
peer's usage crosses the half-window threshold — same pattern as the
existing `MAX_DATA` / `MAX_STREAM_DATA` extension.

The `framesPerGroup = 5` mitigation in [NestMoqLiteBroadcaster] can
be reverted to `1` to match the JS reference broadcaster's wire
shape now that the underlying QUIC bug is gone.

**Reproduces against:** `https://moq.nostrnests.com:4443` (production
relay). Not consistently reproducible against the local
`kixelated/moq` reference relay (the local harness shows the same
shape but with much higher run-to-run variance).

## Symptom

When the speaker pumps Opus frames at the production 20 ms cadence
and packs **one frame per moq-lite group** (= one client-initiated
QUIC unidirectional stream per Opus frame), the listener stops
receiving frames somewhere around the 99th frame. From there to
end-of-broadcast, **`MoqLitePublisherHandle.send` keeps returning
`true` for every subsequent frame**, so the application has no signal
that anything is wrong; the audio simply cuts out a few seconds in.

The bug is invisible to:

- `NestMoqLiteBroadcaster`'s outer `runCatching {…}.onFailure {…}`
  (no exception is thrown on the silent-loss path).
- `MoqLitePublisherHandle.send`'s return value (always `true` past
  the cliff).
- `QuicConnection`'s peer-cap check (`nextLocalUniIndex >= peerMaxStreamsUni`
  doesn't fire because the relay raises `MAX_STREAMS_UNI` ahead of
  our consumption — pre-fix tests confirmed `firstFalseSend = -1`
  even at 1500 frames).

## Sweep evidence (production, before mitigation)

`NostrnestsProdAudioTransmissionTest` (commit
`2da18859`-era, before any QUIC changes), one row per scenario:

| Scenario | streams opened | `received` | cliff |
|---|---|---|---|
| `sweep-frames50` | 50 | 50/50 | none ✅ |
| `sweep-baseline` 100 frames | 100 | 99/100 | last 1 |
| any cadence (5/10/40/80/200 ms) at 100 frames | 100 | 99/100 | last 1 |
| any payload (80 B / 1 KB / 4 KB) at 100 frames | 100 | 99/100 | last 1 |
| `sweep-frames200` | 200 | **99**/200 | hard at 99 |
| `sweep-frames400` | 400 | **99**/400 | hard at 99 |
| `sweep-30s` (1500 frames) | 1500 | **99**/1500 | hard at 99 |
| `sweep-120s` (6000 frames) | 6000 | 52/6000 | hard at 52 |
| `sweep-burst` (cadence 0) | 100 | 65/100 | hard at 65 |
| `sweep-payload-16kb` 100 frames × 16 KB | 100 | 49/100 | hard at 49 |
| **`sweep-frames-per-group-5`** | **20** groups | **100/100** | none ✅ |
| **`sweep-frames-per-group-20`** | **5** groups | **100/100** | none ✅ |
| **`sweep-frames-per-group-all`** | **1** group | **100/100** | none ✅ |

The signal is unambiguous: **fewer streams → no loss, regardless of
frame count, payload, or duration**. The cliff scales with stream-
opening rate, not with byte volume or wall-clock time.

The number "99" is suspicious — it's exactly QUIC's typical
`initial_max_streams_uni = 100` minus one. But:

- Pre-mitigation `firstFalseSend = -1` for tests pumping 1500+
  streams says the cap WAS being raised dynamically. So the cliff
  is NOT `openUniStream` rejecting past the local cap.
- A 16 KB payload variant cliffed at 49 streams (~800 KB total),
  while 80-byte variants cliffed at 99 (~8 KB total). These are
  inconsistent if the cause were a single fixed cap.
- The cliff is *consistent* across many scenarios at ~99 — too
  consistent for plain network packet loss.

## What we tried and what we learned

### Attempt 1 — make `openUniStream` suspend instead of throw

Branch / commit: `f0705e3a` on `claude/audio-transmission-tests-zKzlB`,
**reverted in `96a585a6`**.

Rationale: the obvious failure mode is `peerMaxStreamsUni` being hit
without us ever waiting for the relay to extend it; we'd just throw
locally. So we'd:

1. Make `QuicConnection.openBidiStream` / `openUniStream` suspend
   on a `CompletableDeferred<Unit>` notifier when the cap is hit,
   re-acquire the lock and re-check on every wake.
2. Fire the notifier from `QuicConnectionParser` whenever an inbound
   `MAX_STREAMS` frame raises a cap.
3. Emit `STREAMS_BLOCKED` (RFC 9000 §19.14) when an opener registers
   itself blocked, so the peer knows we want more credit.
4. Wake blocked openers with `QuicConnectionClosedException` on
   close so they don't hang.

Unit tests for the suspend / wake / STREAMS_BLOCKED / close paths
pass (`PeerStreamLimitTest`).

**Result against production:** the cliff didn't move. Same scenarios
still hit ~99 frames received, and the run-to-run variance got
*worse* (numbers swung between 19/100 and 99/100 on consecutive
identical baseline runs against the same relay). The fix also
introduced no observable change in the per-frame `tx i=… ok dt=…us`
traces (`firstFalseSend = -1` still on every test), which means the
suspend path isn't being triggered in production — the peer's cap
isn't what we're hitting.

The fix is RFC-correct on its own (we should suspend rather than
throw, and we should send STREAMS_BLOCKED), but it does not address
the production cliff. Reverted to keep the tree clean while we
chase the real cause.

### Attempt 2 — pack multiple frames per moq-lite group (shipping)

Commit on `claude/audio-transmission-tests-zKzlB` (this commit).

Rationale: `sweep_frames_per_group_*` was 100/100 across every
multi-frame-per-group variant. The cliff scales with the number of
new uni streams opened, not with anything else. Packing N Opus
frames per moq-lite group reduces the new-stream rate by N×.

Default chosen: `framesPerGroup = 5` ≈ 100 ms of audio per group.
Stream-creation rate drops from 50/sec to 10/sec, well below the
cliff.

Trade-off: a brand-new subscriber that attaches mid-broadcast picks
up at the next group boundary per moq-lite "from-latest" semantics.
With 5 frames/group the late-join initial gap is up to 100 ms
(perceptually inaudible). With 1 frame/group it was up to 20 ms.

This is a **mitigation, not a fix.** Other workloads with a high
per-stream cost (file transfer, metadata fan-out, anything that
naturally wants a fresh stream per message) would still hit the
cliff if they exceed roughly 50 streams/second.

## What `flowControlSnapshot` proved (the smoking gun)

After wiring [QuicConnection.flowControlSnapshot] into [SendTraceScenario],
running `sweep_frames_200` against production produced:

```
fc-pre:        peerInitMaxData=4611686018427387903   peerInitMaxStreamDataUni=1250000
               peerInitMaxStreamsUni=10000           sendCredit=4611686018427387903
               consumed=786                          peerMaxStreamsUniNow=10000
               nextLocalUniIdx=1                     pendingBytes=0   pendingStreams=0/4
fc-post-pump:  sendCredit=4611686018427387903        consumed=18729
               peerMaxStreamsUniNow=10000            nextLocalUniIdx=201
               pendingBytes=0                        pendingStreams=0/205
fc-post-grace: (identical to fc-post-pump)
sub[0]:        received=99/200                       firstSentButLost=99
```

This rules out every speaker-side hypothesis at once:

- **`peerInitMaxData = 2⁶²−1`** ⇒ ~5 EB connection-level send credit;
  we used **18 KB**. Not a connection-level flow-control wedge.
- **`peerInitMaxStreamsUni = 10000`, `nextLocalUniIdx = 201`** ⇒
  speaker opened all 200 broadcast streams + 1 control stream, well
  under cap. Not a peer-granted stream-id wedge.
- **`pendingBytes = 0`, `pendingStreams = 0/205`** ⇒ no bytes are
  stuck in any stream's send buffer; everything we wrote made it
  past the writer.
- **`consumed` is identical between post-pump and post-grace** ⇒ we
  weren't even *trying* to send more after the pump finished.

So the speaker → relay path was healthy. The 18 KB of stream data
made it onto the wire. The 99-frame ceiling on the listener side
had to be on the **relay → listener** half of the connection.

Listener-side, our [QuicConnection] advertises `initial_max_streams_uni
= 100` (from [QuicConnectionConfig], default 100) at handshake. The
relay can open up to 100 uni streams to us *for the lifetime of the
connection* unless we send `MAX_STREAMS_UNI(N)` frames to extend it.
Grepping `:quic` for `MaxStreamsFrame` showed it was only ever
*parsed inbound* — there was no outbound emission anywhere. So the
peer's stream-id allowance was capped at 100 forever.

For MoQ over WebTransport this is fatal: the listener's relay
forwards every broadcast group as a new peer-initiated uni stream.
With one group per Opus frame at 20 ms cadence, the 100-stream cap
is exhausted at frame 99 → relay-side blocked → no more groups → no
more audio.

The fix mirrors the existing [appendFlowControlUpdates] pattern for
`MAX_DATA` / `MAX_STREAM_DATA`: track lifetime peer-initiated stream
count, emit `MAX_STREAMS_UNI(currentCount + initialCap)` when count
crosses the half-window threshold.

## Hypotheses still on the table

In rough order of likelihood, none confirmed:

1. **Connection-level send credit** (`initial_max_data` / `MAX_DATA`).
   If the relay's connection-level grant is ~8 KB and only extends
   on `DATA_BLOCKED`, we'd cliff at ~99 frames × ~85 bytes = ~8.4 KB.
   `:quic` parses inbound `MAX_DATA` (`QuicConnectionParser.kt:266`)
   but never emits `DATA_BLOCKED` when our writer hits
   `connBudget == 0` in `appendStreamFrames`. That asymmetry would
   stall the producer with no signal. The 16 KB payload data point
   (cliff at 49 × 16 KB ≈ 800 KB) doesn't match a single fixed
   8 KB cap, but it could match a different per-byte budget the
   relay calibrates to its initial offer. Worth instrumenting.

2. **Per-stream send credit** (`initial_max_stream_data_uni` /
   `MAX_STREAM_DATA`). Each new uni stream gets its own credit
   window from the peer's `initial_max_stream_data_uni`. If that's
   small enough to require a `MAX_STREAM_DATA` on every stream and
   the peer is conservative about issuing it, the writer would
   buffer frames forever waiting for credit while `publisher.send`
   reports success. `:quic` honours the per-stream credit
   (`QuicConnectionWriter.kt:280`) but never emits
   `STREAM_DATA_BLOCKED`.

3. **Reference-relay default `MAX_STREAMS_UNI` extension policy.**
   `kixelated/moq-rs` is built on Quinn; Quinn's stream-cap
   extension is automatic when streams complete. If the production
   nostrnests deployment uses a custom Quinn config that only
   extends in response to `STREAMS_BLOCKED` (which we don't send
   on the un-fixed code path), we'd starve. Attempt 1 added
   STREAMS_BLOCKED emission and didn't shift the cliff, weak
   evidence against this hypothesis.

4. **Relay-side per-subscription buffer.** If the relay has a
   per-subscriber forward buffer that's smaller than the total
   in-flight uni-stream count, and it drops streams that don't
   fit, the cliff would scale with stream count and be invisible
   to the speaker. Hardest to investigate without relay logs.

## Next steps to actually fix

The investigation order I'd take, given the data:

1. **Instrument outbound `appendStreamFrames`.** Log every iteration
   where `connBudget <= 0` (connection-level credit exhausted) or
   where a stream's `streamRemaining <= 0` (per-stream credit
   exhausted). Re-run the prod sweep with this on. If the cliff
   correlates with one of those, the hypothesis is confirmed and the
   fix is to emit `DATA_BLOCKED` / `STREAM_DATA_BLOCKED` and on the
   write path, suspend the writer until a fresh `MAX_DATA` /
   `MAX_STREAM_DATA` arrives.

2. **Capture the production peer transport parameters.** Dump
   `tp.initialMaxData`, `tp.initialMaxStreamDataUni`, and
   `tp.initialMaxStreamsUni` once at handshake time so we know
   exactly what the relay grants. Compare against the cliff
   thresholds.

3. **Test with `:quic` flow-control diagnostics in place.** Re-run
   `sweep_frames200` and `sweep_30s` — the cliff is rock-solid at
   exactly 99 in production, so a single pass is enough to confirm
   the hypothesis.

4. **If it's not connection / stream data flow control,** instrument
   the relay (file an issue on `nostrnests/nests` or `kixelated/moq`)
   asking whether their relay imposes a per-subscriber stream
   buffer that drops on overflow.

## Re-running the sweep after a candidate fix

The test infrastructure is already in place:

```bash
./gradlew :nestsClient:jvmTest \
  --tests com.vitorpamplona.nestsclient.interop.NostrnestsProdAudioTransmissionTest \
  -DnestsProd=true -DnestsInteropDebug=true --rerun-tasks
grep -E "sub\[|missing=" \
  nestsClient/build/test-results/jvmTest/TEST-com.vitorpamplona.nestsclient.interop.NostrnestsProdAudioTransmissionTest.xml \
  | sort
```

Acceptance criterion: `sweep-30s` reports `received≈1500/1500`
without any `framesPerGroup` mitigation. That removes the only
remaining audio-quality tradeoff (the 100 ms late-join initial gap)
and lets us reset `DEFAULT_FRAMES_PER_GROUP` back to 1 to match the
JS reference broadcaster's wire shape.

## Files / lines to touch when picking this up

- `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/QuicConnectionWriter.kt`
  — `appendStreamFrames` is where the writer skips streams with no
  credit; instrument here.
- `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/QuicConnection.kt:160-165`
  — `sendConnectionFlowCredit` / `sendConnectionFlowConsumed`. The
  values to log + diff.
- `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/TransportParameters.kt`
  — `decode` is where peer TPs land at handshake.
- `quic/src/commonMain/kotlin/com/vitorpamplona/quic/connection/QuicConnectionParser.kt`
  — currently parses `MAX_DATA`, `MAX_STREAM_DATA`, `MAX_STREAMS`,
  `STREAMS_BLOCKED` (last one ignored). The reverse —
  `DATA_BLOCKED` / `STREAM_DATA_BLOCKED` emission — would live in
  `QuicConnectionWriter`.
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/SendTraceScenario.kt`
  — extend per-frame trace with `connBudget` / `streamRemaining`
  if the writer instrumentation surfaces them via diagnostics on
  `QuicConnection`.
