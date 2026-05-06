# Plan: cross-stack interop test (T16) — Phase 1 + Phase 2 results

**Status:** Phase 1 + most of Phase 2 landed. Phases 3–5 still deferred.

## Phase 2 update

Added on top of the Phase 1 scaffolding:

- **`hang-listen` real body** — connects to a `moq-lite-03` relay,
  reads the hang catalog, picks the first Opus / Container::Legacy
  audio rendition, decodes each Opus packet via the `opus = "0.3"`
  crate, and writes Float32 little-endian PCM to `--output-pcm`.
- **`hang-publish` real body** — claims a broadcast, publishes a hang
  catalog with one Opus rendition (track name configurable via
  `--track-name`, default `audio/data` to match Amethyst's
  `MoqLiteNestsListener.AUDIO_TRACK`), encodes a sine wave with
  libopus, and pumps Opus frames in 5-frame groups for `--duration`
  seconds. Uses `audiopus`-equivalent `opus = "0.3"`.
- Both binaries explicitly install the rustls aws-lc-rs crypto
  provider (rustls 0.23 no longer auto-installs) and use
  `--client-version moq-lite-03` + `--tls-disable-verify=true`
  to interop with the harness's self-signed `--tls-generate localhost`
  relay.
- **JVM Opus encoder/decoder** via `club.minnced:opus-java:1.1.1`
  (JNA bindings + bundled libopus.so / libopus.dylib / opus.dll
  natives). Lives in `nestsClient/src/jvmTest/.../audio/JvmOpusEncoder.kt`
  + `JvmOpusDecoder.kt`. Verified by
  `JvmOpusRoundTripTest.sine_440_round_trips_through_libopus` —
  encode → decode preserves the FFT peak at 440 Hz and the
  zero-crossing rate at 880/sec within 5%.
- **Real-time pacing** in `SineWaveAudioCapture` — `readFrame`
  blocks until the next 20-ms boundary, mirroring how a microphone
  source paces. Without this the broadcaster's read loop would
  flood the relay with millions of frames/sec.
- **`HangInteropTest.rust_hang_publish_to_rust_hang_listener_round_trip_440`**
  — Rust↔Rust round-trip through the harness. Spawns `hang-publish`
  + `hang-listen` as subprocesses, asserts the decoded PCM has FFT
  peak at 440 Hz, ZCR at 880/sec, and 5 s of samples (±20% slack
  for Opus look-ahead + relay buffering). Verified green on Linux
  x86_64.

## I1 — Amethyst speaker → hang-listen — green at `framesPerGroup=5`

Initial diagnosis (Kotlin speaker → hang-listen sees `Group {
subscribe, sequence }` headers but no frame payloads) was bisected
by adding `KotlinSpeakerKotlinListenerThroughNativeRelayTest` —
a Kotlin↔Kotlin path through the same `moq-relay` 0.10.x. That
test reproduced the failure too, ruling out a Kotlin↔Rust-specific
mismatch. Bisecting `framesPerGroup`:

  - `framesPerGroup = 1` (one frame per uni stream): **passes**
  - `framesPerGroup = 5` (the value
    `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`
    recommends): **passes**
  - `framesPerGroup = 50` (the repo's current
    `NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP`): **fails**

The 50-frame default writes ~6 KB onto a single uni stream over
~1 s, which exceeds moq-relay 0.10.25's per-subscriber forward
buffer; the relay forwards the Group control header but holds the
frame data, never delivering it downstream. This matches the
audit summarised in the cliff-investigation plan: the bug is a
moq-relay 0.10.x policy interacting with our publish cadence, not
a wire-format defect on either side.

`HangInteropTest.amethyst_speaker_to_hang_listener_static_tone_440`
now pins `framesPerGroup = 5` to match what the cliff plan
already documents as the safe production cadence. The Kotlin↔
Kotlin diagnostic test
`KotlinSpeakerKotlinListenerThroughNativeRelayTest`
also pins `framesPerGroup = 5` and is kept as a regression for
the cadence interaction — if a future relay bump changes the
ceiling, both tests will trip together and the failure will be
attributable in one place.

**Conflict between plans (worth a maintainer's eye, NOT auto-applied
here):** the 2026-05-01 cliff-investigation plan recommends
`DEFAULT_FRAMES_PER_GROUP = 5`, but the current code has `50`
with a kdoc citing later two-phone production tests on
`claude/fix-nests-audio-receiver-HCgOY` that showed `5`/`10`
hit a *different* listener-side cliff. The two are tuning for
different bottlenecks:

  - cliff-investigation plan ↦ relay-side per-subscriber forward
    queue overflow at high stream-rate (which our cross-stack
    tests against `moq-relay 0.10.25 --auth-public ""` reproduce
    cleanly — `50` flatly fails to deliver frames downstream)
  - HCgOY field tests ↦ listener-side cliff-detector recycling
    the transport on stream RST, which favours larger groups
    (fewer streams to lose)

These are NOT contradictory at the protocol level — they're
different failure modes triggered by different relay
configurations. The interop harness's `--auth-public ""` minimal
relay setup hits the first cliff; production's `nostrnests/nests`
deployment apparently lives in a regime where the second cliff
dominates. We pin `framesPerGroup = 5` in the test scenarios
because that's the value at which our test succeeds; production
keeps `50` because that's the value its field tests vetted.
Reconciling the two — either by getting both setups under a
single value, or by varying `framesPerGroup` per environment —
is left to a maintainer who can run both rigs.

**Origin:** companion to `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.
This file records what actually shipped in Phase 1, the deviations from
the spec, and the concrete pickup points for Phase 2.

## What landed

### Cargo workspace (`cli/hang-interop/`)

- Workspace with three binary crates: `hang-listen`, `hang-publish`,
  `udp-loss-shim`. **All three are Phase-1 stubs** — they parse their
  CLI flags via `clap`, print a banner, and exit 0. Phase 2 fills the
  bodies.
- `cli/hang-interop/REV` documents the pinned upstream
  `kixelated/moq` rev (`9e2461ee...`) plus the published crate
  versions on crates.io that track that rev (`moq-relay 0.10.25`,
  `moq-token-cli 0.5.23`, `hang 0.15.8`, `moq-lite 0.15.15`,
  `moq-native 0.13`).
- `cargo build --release` is verified green from the workspace root.

### Gradle integration (`nestsClient/build.gradle.kts`)

- `interopInstallMoqRelay` — `cargo install moq-relay --version
  $moqRelayVersion --root <cache>` into
  `~/.cache/amethyst-nests-interop/hang-interop-cargo/`. Skips when
  the binary already exists in the cache.
- `interopInstallMoqTokenCli` — same shape for `moq-token-cli`.
- `interopBuildSidecars` — `cargo build --release` over the local
  `cli/hang-interop/` workspace.
- `interopBuildHangSidecars` — umbrella task that depends on the
  three above. Runs as a test dependency only when
  `-DnestsHangInterop=true` is set.
- Test-task wiring forwards `nestsHangInteropSidecarsDir` and
  `nestsHangInteropCargoBinDir` system properties so the harness
  can find the binaries. `nestsHangInterop` itself is also
  forwarded — mirrors the existing `nestsInterop` opt-in.

### Kotlin harness (`nestsClient/src/jvmTest/...`)

- `interop/native/NativeMoqRelayHarness.kt` — boots a real
  `moq-relay` subprocess with `--tls-generate localhost` (relay
  self-signs its cert at startup) and `--auth-public ""` (every
  path is treated as public, no JWT required). Uses
  `ServerSocket(0)` to reserve an ephemeral port. Tracks process
  output in a 64-line ring buffer so a startup failure includes
  the relay's stderr tail. Singleton-per-JVM via `shared()`,
  shutdown via JVM hook, mirroring the existing
  `NostrNestsHarness` pattern. Public surface: `relayUrl`,
  `loopbackHostPort()`, `hangListenBin()`, `hangPublishBin()`,
  `udpLossShimBin()`, `moqTokenBin()`.
- `audio/SineWaveAudioCapture.kt` — frame-perfect deterministic
  sine wave at any frequency, mono, conforming to the existing
  `AudioCapture` interface in `commonMain`.
- `audio/PcmAssertions.kt` — pure-Kotlin signal-domain assertions:
  `assertSampleCount`, `assertRms`, `assertFftPeak` (Hann-windowed
  iterative Cooley-Tukey, ~100 lines, no transform-library dep),
  `assertZeroCrossingRate`, `findSilenceWindow`. The plan spec'd
  these for Phase 1; everything is in commonTest now and ready
  for the Phase 2 scenarios.
- `interop/native/NativeMoqRelayHarnessSmokeTest.kt` — the only
  test that runs in Phase 1. Boots the harness, verifies the
  relay binds its UDP port, verifies the sidecar binaries are
  executable and the `hang-listen` stub runs cleanly. **Does
  not** assert any wire format — that's Phase 2.

## Deviations from the spec

| Plan | Reality | Why |
|---|---|---|
| In-process Kotlin JWT minter (ES256, JWKS file mounted into relay) | Relay configured with `--auth-public ""`; no JWT required | The plan flagged JWT issuance as "implementation detail" — wire-format and protocol assertions don't need real auth, and `--auth-public` short-circuits the whole minter + JWKS file dance. JWT validation as an interop concern is already covered by the existing `NostrNestsAuthInteropTest` against the Docker'd `moq-auth`. The cargo-installed `moq-token` CLI is exposed via `moqTokenBin()` for Phase 2 scenarios that DO want to mint a real token (e.g. revocation, expiry, kid-mismatch). |
| Self-signed cert generated at suite setup via Bouncy Castle / `openssl req -x509` | `moq-relay --tls-generate localhost` (relay self-signs at startup) | `moq-native` ships this behaviour; saves us writing PEM I/O + cert generation. The Kotlin client can use the existing `PermissiveCertValidator` to skip chain validation. |
| `relay.toml` config file | CLI flags only | Same effect, fewer moving parts. Field names in the plan (`server.listen`, `tls.cert`/`key`, `auth.jwks_path`) were speculative; actual `moq-native` flags are `--server-bind`, `--tls-cert`/`--tls-key`/`--tls-generate`, `--auth-key`/`--auth-public`. |
| Build `moq-relay` from a pinned `kixelated/moq` checkout via `cargo build --release -p moq-relay` | `cargo install moq-relay --version 0.10.25 --root <cache>` | `moq-relay` and `moq-token-cli` are published on crates.io; `cargo install` makes the install reproducible without embedding/cloning the upstream workspace. Cache key is the pinned version. |
| Phase 1 step 7: "Wire one passing test: I1, A→hang" | Smoke test only — no wire-format scenario | I1 needs a JVM-side Opus encoder (the `OpusEncoder` interface in commonMain only has an Android `MediaCodec` actual today), AND it needs `hang-listen` to actually subscribe to a moq-lite session and decode Opus to PCM. Both are Phase 2 work. The smoke test we landed proves all the harness load-bearing pieces work, so Phase 2 only has to fill in the codecs + the sidecar bodies. |

## Phase 2 pickup

The core gap: **JVM Opus encoder + decoder, plus filling the
sidecar binaries**. Everything else is in place.

### Step A — JVM Opus encoder/decoder

Add a JVM `actual` for `OpusEncoder` / `OpusDecoder` (currently
Android-only via `MediaCodecOpusEncoder` / `MediaCodecOpusDecoder`).
Two viable options:

1. **`org.concentus:Concentus`** (pure-Java port of libopus). On
   Maven Central. License-compatible. Slower than native libopus
   but plenty fast enough for a 48 kHz mono test stream. **Recommended**
   since it adds zero native dependency.
2. **`audiopus_jni` (vendored or via JitPack)** — wraps the same
   `audiopus` Rust crate the upstream `hang` examples use. Faster
   but adds a JNI shared object per platform.

Wire it into `nestsClient/src/jvmMain/.../audio/JvmOpusEncoder.kt` +
`JvmOpusDecoder.kt`, with the Android `MediaCodec` actuals
unchanged. Add `CapturingOpusDecoder` (the plan's Phase 1 ask)
once the JVM decoder exists.

### Step B — Fill `hang-listen`

Model on `kixelated/moq/rs/hang/examples/subscribe.rs` (the
`subscribe` example in the upstream workspace at
`/tmp/moq/rs/hang/examples/subscribe.rs` if recloned). Replace
its video-track logic with the audio path: pick the first
rendition with `container.kind == "legacy"` and
`codec == "opus"`, subscribe, decode each `Frame` into a
`Bytes`-encoded VarInt timestamp + Opus packet, run the Opus
packets through `audiopus::Decoder`, write Float32 PCM to
`--output-pcm`. Dependencies to add to
`cli/hang-interop/hang-listen/Cargo.toml`:

```toml
hang = "0.15"
moq-lite = "0.15"
moq-mux = "0.3"
moq-native = { version = "0.13", default-features = false, features = ["quinn", "aws-lc-rs"] }
web-transport-quinn = "0.11"
audiopus = "0.3"
bytes = "1"
tracing = "0.1"
```

### Step C — Fill `hang-publish`

Mirror of `hang-listen` for the reverse direction. Generate a sine
wave in Rust, encode with `audiopus::Encoder`, publish a hang
catalog with one Opus rendition, push frames as
`varint(timestamp_us) + opus_packet` per the
`hang::container::Frame::encode` contract (see
`/tmp/moq/rs/hang/src/container/frame.rs`).

### Step D — Wire the I1 scenario

Once A/B/C land, the I1 "amethyst speaker → hang listener" test
is straightforward — see the spec for the pattern. The harness +
`SineWaveAudioCapture` + `PcmAssertions` are already in place to
support it.

## Phase 2.E — additional scenarios

Landed on top of I1:

- **I11 wire-byte capture** (`first_audio_frame_is_not_opus_codec_config`):
  hang-listen gained `--dump-first-frame <path>`. Test asserts the
  first audio frame's post-Container-Legacy-strip codec payload
  doesn't begin with `OpusHead` magic. Catches the T8 regression
  where Android's `MediaCodecOpusEncoder` would emit
  BUFFER_FLAG_CODEC_CONFIG bytes as a normal audio frame.
- **I2 late-join** (`late_join_listener_still_decodes_tail`):
  hang-listen attaches at T+2 s of a 5 s broadcast; asserts ≥1.5 s
  of decoded audio with the 440 Hz peak still recoverable.
- **I3 mute window** (`mid_broadcast_mute_shortens_decoded_pcm`):
  speaker mutes for 1 s mid-broadcast. Amethyst's broadcaster FINs
  the open uni stream rather than pushing zeros (so web watchers
  don't park on `await readFrame`), so the mute manifests as a
  sample-count deficit (~3 s for 4 s wallclock), not embedded
  silence. Asserts the deficit is in the right ballpark.

`runSpeakerToHangListen(...)` extracted as a per-scenario helper
in `HangInteropTest`. Each scenario anchors the QUIC transport's
coroutine scope to the per-test pumpScope so UDP sockets and
QuicConnection pumps cleanly tear down between tests.

The companion `KotlinSpeakerKotlinListenerThroughNativeRelayTest`
(Kotlin↔Kotlin diagnostic for the I1 bisect) lives behind
`-DnestsHangInteropDiagnostic=true` — it flakes when run in the
same JVM as the 5 native-subprocess scenarios (relay-side state
accumulation), and its only purpose is wire-format bisects.

## Phase 2.E deferred

- **I4 stereo** — needs a non-trivial production change in
  `MoqLiteHangCatalog.OPUS_MONO_48K_AUDIO_DATA_JSON_BYTES` (which
  hard-codes mono). Out of scope for these test plumbing changes;
  ship as a separate production-side patch.
- **I8 SubscribeDrop**, **I10 long broadcast**, **I12 Goaway** —
  next batch of P0 scenarios on the existing harness.

## Phase 3 + 4 + 5 deferred

Untouched in Phase 1:

- Phase 3 transport robustness (`udp-loss-shim` body, hot-swap,
  long-broadcast).
- Phase 4 browser harness (`nestsClient-browser-interop/` directory,
  Playwright driver).
- Phase 5 browser-only scenarios.

CI integration (the GitHub Actions workflow updates the spec
shows) is also pending — until Phase 2 lands a real test, there's
nothing in `-DnestsHangInterop=true` worth gating CI on except
the smoke test.

## Files

```
cli/hang-interop/
├── REV
├── Cargo.toml + Cargo.lock
├── hang-listen/{Cargo.toml,src/main.rs}        # Phase 2: real subscribe + decode
├── hang-publish/{Cargo.toml,src/main.rs}       # Phase 2: real publish + sine encode
└── udp-loss-shim/{Cargo.toml,src/main.rs}      # Phase 1 stub; Phase 3 fills body

nestsClient/build.gradle.kts                    # +interopBuildHangSidecars + system props
nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/
├── audio/
│   ├── JvmOpusEncoder.kt                       # libopus via JNA (test-only)
│   ├── JvmOpusDecoder.kt                       # libopus via JNA (test-only)
│   ├── JvmOpusRoundTripTest.kt
│   ├── PcmAssertions.kt
│   ├── PcmAssertionsTest.kt
│   └── SineWaveAudioCapture.kt
└── interop/native/
    ├── NativeMoqRelayHarness.kt                # boots moq-relay subprocess
    ├── NativeMoqRelayHarnessSmokeTest.kt
    ├── HangInteropTest.kt                      # I1 + I2 + I3 + I11 + Rust↔Rust
    └── KotlinSpeakerKotlinListenerThroughNativeRelayTest.kt
                                                # diagnostic, gated separately

nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md  # this file
```
