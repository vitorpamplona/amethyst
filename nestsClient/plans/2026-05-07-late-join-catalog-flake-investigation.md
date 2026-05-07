# `late_join_listener_still_decodes_tail` catalog-cancelled flake investigation

**Status: partially fixed (commit `8cc7cbd42`), residual flake documented.**

`HangInteropTest.late_join_listener_still_decodes_tail` and (less
frequently) `packet_loss_1pct_does_not_kill_audio` intermittently
fail with `hang-listen` exiting non-zero on a `subscribe error`
during catalog read. Pre-fix flake rate: 5/5 fail in a sweep.
Post-fix rate: ~3/5 fail. The fix closes one root cause; the
residual is a separate, deeper bug that's beyond this investigation
session's budget.

## Pre-fix root cause: moq-rs cancel cascade

The previous hang-listen catalog-read shape:

```rust
for attempt in 0..3 {
    let catalog_track = broadcast.subscribe_track(...)?;
    let mut catalog = hang::CatalogConsumer::new(catalog_track);
    match tokio::time::timeout(Duration::from_secs(2), catalog.next()).await { ... }
    // catalog_track + catalog drop at iteration boundary
}
```

is broken on moq-rs 0.10.x. The flow:

1. Attempt 0: `subscribe_track` creates a TrackConsumer. Wire
   subscribe id=0 fires.
2. Speaker's `setOnNewSubscriber` hook is supposed to write the
   catalog via `send(catalogJson) + endGroup()`. **For some reason
   this doesn't deliver in time** — see "residual root cause" below.
3. 2 s timeout fires. Loop iteration ends. `catalog_track` drops.
4. moq-rs sees `track.unused()` resolve (no consumers left), aborts
   the wire subscribe with `Error::Cancel`.
5. **`Error::Cancel` maps to wire stream-reset code 0** per
   `moq-lite/src/error.rs:96-105`.
6. Attempt 1: `subscribe_track(catalog.json)` returns a consumer
   whose internal state is already in the just-cancelled state.
   `.next()` resolves immediately with `cancelled`.
7. Attempt 2+: subscribe_track itself returns `Err(cancelled)`.
8. Loop bails; `Error: subscribe catalog. cancelled.`

**Fix (commit `8cc7cbd42`):** hold ONE subscription open for the
full 10 s budget; inner timeouts on `.next()` poll for the first
group; outer timeout caps the total wait. Code is in
`hang-interop/hang-listen/src/main.rs`.

This eliminates the cancel-cascade failure mode. 2 of 5 sweep runs
post-fix go all-green; 3 hit the residual described next.

## Residual root cause (unidentified)

Same test, post-fix, fresh repro from sweep run 4:

```
12:35:45.333625  subscribe started id=0 catalog.json
                 (no further logs from hang-listen for 2.94 s)
12:35:48.267341  subscribe error id=0 err=remote error: code=0
                 Error: catalog read | moq lite error: cancelled
```

The single, long-lived subscribe is cancelled by the **peer** (relay
or speaker) ~3 s after start. Wallclock alignment:

- Speaker started broadcasting at T=0
- `delay(listenerLateJoinDelayMs = 2_000)` → T=2 s
- hang-listen connects + subscribes → T=2.05 s
- Speaker's broadcast window ends at T=5 s
  (helper does `delay(speakerSeconds * 1_000 - listenerLateJoinDelayMs)`
   = `delay(3_000)` AFTER hang-listen starts)
- Listener-observed cancel at speaker-T+~5 s = ~3 s after subscribe

So the cancel coincides with the speaker tearing down. The catalog
data **never arrived during the 3 s subscribe window**, despite the
speaker presumably having the hook installed AND the inbound
SUBSCRIBE arriving normally.

## Ruled out

- **Hook installation race.** `MoqLiteNestsSpeaker.setOnNewSubscriber`
  is called BEFORE the speaker transitions to `Broadcasting` state
  (`MoqLiteNestsSpeaker.kt:176-182`). Listener subscribes 2 s
  later — hook is definitively installed.
- **Stale `inboundSubs`.** `@BeforeTest` calls `resetShared()` which
  restarts the moq-relay subprocess. Speaker session is fresh per
  test (new `pumpScope` + fresh `MoqLiteSession`). No cross-test
  state leak.
- **Hook captured-but-null.** `registerInboundSubscription` reads
  `onNewSubscriberHook` inside the gate AFTER the sub is added.
  By T=2 s the hook is non-null.
- **`inboundSubs.isEmpty()` race in `send()`.** Hook is launched
  AFTER `inboundSubs += sub` inside the same gate. The hook's
  `send()` re-acquires the gate; sees `inboundSubs` non-empty.
- **`MAX_STREAMS_UNI` exhaustion at speaker.** Catalog uni stream
  is one stream per subscriber; cap is 10000.
- **Idle timeout.** Quinn's default (per moq-native) is 30 s, not 3.
- **Audio publisher's `onTerminalFailure` firing.** Only triggered
  by `MAX_CONSECUTIVE_SEND_ERRORS` thrown errors, not the
  no-subscribers `return false` path that the audio publisher
  takes during the 2 s warmup.

## Plausible remaining hypotheses

1. **Relay-side per-track state race.** The relay's downstream
   subscriber (forwarding to hang-listen) is created when
   hang-listen's SUBSCRIBE arrives. The relay's upstream subscriber
   (subscribed-on-speaker) might be created on-demand and might race
   with the speaker's hook firing. If the relay's upstream consumer
   isn't fully alive by the time the speaker's uni stream arrives at
   the relay, the relay drops the uni without forwarding.
2. **Catalog uni stream priority/scheduling.** The audio publisher
   (running silent — `inboundSubs.isEmpty() → return false`) could
   somehow contend with the catalog publisher's uni-stream open via
   the shared `transport.openUniStream()`, even though they're
   separate publishers with separate gates. Less likely.
3. **moq-rs CLIENT-side `subscribe_track` returning a stale
   consumer.** Even on the first call, if the broadcast's track-
   producer pool has ANY residual entry from an earlier test (despite
   `resetShared()`), the consumer might be born already-cancelled.
   The 2/5 pass rate suggests there's a timing component.
4. **`Track.unused()` racing with the long subscribe.** The hang
   crate's `CatalogConsumer::new(track)` may temporarily drop an
   internal handle, causing a brief `unused()` flicker that aborts
   the upstream subscription before the speaker's data arrives.

## What would confirm a hypothesis

1. **Speaker-side log instrumentation.** Add `Log.d("NestTx") {
   "catalog hook fired for subId=$id" }` inside the
   `setOnNewSubscriber` lambda; `"catalog send returned $result for
   subId=$id"` after each `send()`; `"catalog endGroup completed for
   subId=$id"`. Run the failing test under `--info` Gradle output
   to see if the hook fires + writes succeed on the SPEAKER side.
   If yes → the data is being written but the listener isn't
   receiving it (relay-side issue).
2. **Relay-side log instrumentation.** Boot moq-relay with
   `RUST_LOG=moq_relay=debug,moq_lite=debug` and capture per-test
   stderr to a file. Look for "subscribe started" / "serving group" /
   "subscribe cancelled" timing on the relay side. Cross-reference
   with hang-listen's view.
3. **Listener-side QUIC-level capture.** Wrap hang-listen's UDP
   socket via `udp-loss-shim` modified to packet-log instead of drop.
   See exactly which streams open + close.

## Mitigation if root cause stays elusive

The test's `listenerLateJoinDelayMs = 2_000` AND `speakerSeconds = 5`
together leave only 3 s of catalog-read window AFTER the listener
attaches. **Bumping `speakerSeconds` to 8 s** (or shrinking the
late-join delay to 1 s) would give the catalog read more headroom
without changing what the test asserts. This wouldn't fix the
underlying flake but would mask it for CI-purposes.

Currently rejected because masking a real bug isn't a fix; the
remaining 60% failure rate is a genuine signal.

## Files referenced

- `nestsClient/tests/hang-interop/hang-listen/src/main.rs:160-220`
  (post-fix catalog read shape)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/MoqLiteNestsSpeaker.kt:170-181`
  (`setOnNewSubscriber` hook installation)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/MoqLiteSession.kt:1167-1192`
  (`registerInboundSubscription` + hook-launch path)
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropTest.kt:170-180`
  (`late_join_listener_still_decodes_tail` scenario)
- Pre/post sweep results: `0/5 → 2/5` over 10 total sweep runs.
