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

## Known gap — Amethyst speaker → hang-listen (I1 forward direction)

Wired in `HangInteropTest` initially as
`amethyst_speaker_to_hang_listener_static_tone_440` but it doesn't
pass yet. Symptom: the hang `Container::Legacy` decoder receives
each `moq-lite Group { subscribe, sequence }` control message but
never receives the per-frame `varint(timestamp_us) + opus` payload
that should follow on the same uni stream. Both sides agree on
`moq-lite-03`, the audio rendition catalog parses correctly, the
audio SUBSCRIBE registers on the speaker's audio publisher
(`inboundSubs.size=1`), and the broadcaster's send loop reports
50 frames/sec going out — yet hang-listen sees no
`varint(size) + bytes` after each Group header.

The race fix in the test (`speaker.startBroadcasting()` before
spawning `hang-listen`) is needed to keep the catalog publisher's
`setOnNewSubscriber` hook installed in time, but doesn't unblock
the audio path. The catalog uni stream's frame data DOES make it
through — only the audio uni stream's frames are lost. The bug is
likely in `:nestsClient`'s audio uni-stream framing (in
`MoqLiteSession.openGroupStream` / `PublisherStateImpl.send`) and
needs a wire-byte capture against the existing Kotlin↔Kotlin
listener path to confirm the issue is symmetric (i.e. only Rust
fails to read) or producer-side (Kotlin fails to write the frame
size prefix the way the spec calls for). The smoke-test version
`HangInteropTest.rust_hang_publish_to_rust_hang_listener_round_trip_440`
proves the harness + cargo workspace + JVM Opus all work; the
Kotlin-speaker path is gated behind this open issue and tracked
in this doc.

When picking up: replace the test body with the speaker-→-listener
shape from the plan's "Patterns" section (already prototyped in
the deleted `amethyst_speaker_to_hang_listener_static_tone_440`),
and capture the first audio uni stream's bytes via a custom
`WebTransportFactory` that sniffs writes — then compare against
what the Rust subscriber's `run_group` parser expects.

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

## Files added in Phase 1

```
cli/hang-interop/
├── REV
├── Cargo.toml
├── hang-listen/{Cargo.toml,src/main.rs}
├── hang-publish/{Cargo.toml,src/main.rs}
└── udp-loss-shim/{Cargo.toml,src/main.rs}

nestsClient/build.gradle.kts                  # +interopBuildHangSidecars + system props
nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/
├── audio/
│   ├── PcmAssertions.kt
│   └── SineWaveAudioCapture.kt
└── interop/native/
    ├── NativeMoqRelayHarness.kt
    └── NativeMoqRelayHarnessSmokeTest.kt

nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md  # this file
```
