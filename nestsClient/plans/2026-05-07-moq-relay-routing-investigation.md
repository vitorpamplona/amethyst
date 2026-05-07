# Plan: investigate moq-relay 0.10.x per-broadcast subscribe-routing race

**Status:** specced — pickup ready.

**Owns:** the residual flake that affects four T16 scenarios:
`late_join_listener_still_decodes_tail`,
`packet_loss_1pct_does_not_kill_audio`,
`long_broadcast_60s_tone_round_trips`, and the new
`chromium_publisher_*_kotlin_listener_recovers` tests in browser-tier.

**Blocks:** CI gating for `:nestsClient:jvmTest -DnestsHangInterop=true`
and `-DnestsBrowserInterop=true`. Re-evaluate the
`hang-interop` / `browser-interop` workflow jobs once this is closed.

**Cross-refs:**
- `nestsClient/plans/2026-05-07-late-join-catalog-flake-investigation.md`
  (smoking-gun trace + 4 mitigation attempts, 2 of which were
  net-negative and reverted).
- `nestsClient/plans/2026-05-07-i7-post-reconnect-cliff-investigation.md`
  (same kind of routing issue surfacing across publisher cycles).

## What we know

For broadcasts that fail (sample suffixes from the trace:
`10d4b6f2…`, `c75e2648…`, `f1be27ef…`):

1. The Kotlin speaker side logs:
   - `ANNOUNCE inbound prefix='' → emitted Active suffix='<broadcast>'`
   - …then NOTHING for the entire test window.
   - Audio publisher's `send()` repeats `no inboundSubs` at 50 fps
     until the test times out.

2. The Rust hang-listen side logs:
   - `connected, version=moq-lite-03`
   - `broadcast announced path=<broadcast>`
   - `subscribe started id=0 broadcast=<broadcast> track=catalog.json`
   - …then `subscribe error err=remote error: code=0` exactly when
     the speaker tears down at the broadcast-window end (= relay
     forwarding `Cancel`).

The relay accepts the listener's wire SUBSCRIBE on its downstream
connection but **never opens an upstream SUBSCRIBE bidi to the
speaker** for the failing broadcast. The upstream subscribe-pump
that's supposed to forward downstream subscribes to the speaker
isn't wired up by the time the listener subscribes.

For broadcasts that succeed (same trace, same JVM, different test):

```
ANNOUNCE inbound prefix='' → emitted Active suffix=<broadcast>
SUBSCRIBE inbound id=0 broadcast=<broadcast> track='catalog.json'
SUBSCRIBE registered id=0 …
openGroupStream subId=0 seq=0
…
```

All log lines fire; the relay forwards the upstream subscribe
within ~1 ms of the downstream subscribe. Failure mode is binary:
the relay does or does not forward.

## Hypotheses, ranked by next step

### H1 — moq-rs 0.10.x bug in `Origin::announced()` → upstream-pump setup race

`Origin::announced().await` returns the broadcast as soon as the
speaker's announce lands in the relay's origin map. The relay's
upstream-subscribe pump for that broadcast is set up on a separate
async path. If a downstream listener subscribes before the pump is
fully wired, the SUBSCRIBE accepts on the listener's wire (the
relay has the broadcast in its origin) but never propagates
upstream.

**Status:** prime suspect; see "smoking gun" in
`2026-05-07-late-join-catalog-flake-investigation.md`.

### H2 — interaction with the `--auth-public ""` minimal config

The harness boots moq-relay with `--auth-public ""` to skip JWT
issuance. Production runs with full auth. It's possible the
auth-public path takes a different code path through the relay's
origin/subscribe wiring that's racier than the auth'd path.

**Status:** plausible; would explain why the flake isn't reported
against the production deployment.

### H3 — local-only timing race that resolves at higher latency

Loopback (127.0.0.1) has near-zero RTT. The relay's internal
async setup may rely on the natural RTT cushion of a real network
to sequence upstream-subscribe-pump setup vs. downstream-subscribe
acceptance. We bypass that cushion in the test.

**Status:** less likely (the cliff plan's evidence shows lossy
network actually makes things *worse* via the `serve_group` task
pool) but worth ruling out.

## Investigation plan

### Step 1 — capture relay-side traces

`NativeMoqRelayHarness.boot` currently launches `moq-relay` with
`RUST_LOG=info`. Bump to `RUST_LOG=moq_relay=trace,moq_lite=trace`
and capture stderr to a per-test tempfile. Cross-reference with
the failing test's hang-listen stdout AND the speaker-side
`Log.d("NestTx")` traces (already captured in
`<system-err>` per JUnit XML).

Concretely: in `NativeMoqRelayHarness.kt` add a `--log-stderr`
option that the @BeforeTest hook sets to a `<test-method>.log`
path under `nestsClient/build/relay-logs/`. The Kotlin side
already has the speaker-side traces; the Rust side is the gap.

What to look for in the failed-broadcast log:
- Was a SUBSCRIBE bidi opened to the speaker for the failing
  broadcast suffix? (moq_lite span: `subscribe`).
- Did the relay's `Origin::publish_broadcast` call complete
  before the listener's SUBSCRIBE arrived?
- Any `track.unused()` resolves on the publisher-side track that
  would explain immediate cancellation?

### Step 2 — write a minimal reproducer

If Step 1 shows the bug is independent of our test framework,
extract a minimum reproducer:

```rust
// reproducer.rs
let mut cmd = std::process::Command::new("moq-relay")
    .args(&["--server-bind", "127.0.0.1:0", "--auth-public", "",
            "--tls-generate", "localhost"])
    .spawn()?;
// Run a moq-lite SPEAKER on one client, a moq-lite LISTENER on
// another, both pointed at the relay. Listener subscribes immediately
// after the speaker announces. Repeat 100×; count how many succeed.
```

Then strip the SPEAKER's announce timing, the LISTENER's subscribe
timing, the relay's `--auth-public` flag — bisect to the smallest
form that still reproduces.

### Step 3 — file upstream

If Step 1 / 2 confirm a moq-rs bug, file a `kixelated/moq` issue
with:
- The reproducer.
- Smoking-gun trace pair from our test harness.
- Pin to moq-rs version `0.10.25` (per `nestsClient/tests/hang-interop/REV`).
- Cross-link to existing
  `2026-05-01-quic-stream-cliff-investigation.md`'s open follow-up
  #1 (the per-subscriber forward-queue cliff is a sister bug).

### Step 4 — try newer moq-relay version

Bump `MOQ_RELAY_VERSION` in `nestsClient/tests/hang-interop/REV`
and `nestsClient/build.gradle.kts` to the next minor release on
crates.io (whatever's current at the time of pickup). Run the 5×
sweep. If the flake disappears, the upstream may have already
fixed it; we can pin past 0.10.x.

**Risk:** newer moq-relay versions may have wire-format changes
that break our current `moq-lite-03` ALPN pin. The browser
harness's `@moq/lite` 0.2.x client offers `moq-lite-04` AND
`moq-lite-03`, so a newer relay that drops `03` would still
negotiate fine via 04.

## Acceptance criteria

- Sweep `for i in 1 2 3 4 5; do ./gradlew :nestsClient:jvmTest
  --tests HangInteropTest -DnestsHangInterop=true --rerun-tasks; done`
  passes 5/5.
- Browser-tier sweep similarly stable.
- Either:
  (a) Upstream issue filed with reproducer (if the bug is in
      moq-rs and we can't fix it locally), OR
  (b) Local fix applied (e.g. version bump + REV update +
      Cargo.lock regenerate).

## Out of scope

- The `:quic` module's `MAX_STREAMS_UNI` extension fix
  (`d391ae1d`) — already shipped, separate concern.
- The production-side `framesPerGroup` reconciliation
  (`2026-05-07-framespergroup-production-rerun.md`) — independent.
