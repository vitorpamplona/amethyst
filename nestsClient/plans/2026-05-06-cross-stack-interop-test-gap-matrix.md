# T16 gap matrix — wire fixes ↔ asserting interop scenarios

**Status:** documentation. Closes Definition of Done #5 from
`2026-05-06-cross-stack-interop-test.md`.

This document maps each audit-branch T# wire fix that landed in
`main` to ≥ 1 hang-tier and/or browser-tier interop scenario that
asserts its wire output. A regression on any T# fix would trip the
listed scenario(s) deterministically.

**Source of truth for the T# series:** `git log --grep "fix(nests):
T"` against `origin/main` — I list only fixes that exist as concrete
commits, not the spec's aspirational T1–T14 enumeration. Scenarios
are addressed by their interop-suite ID (I1–I15) per
`2026-05-06-cross-stack-interop-test.md` and the
`HangInteropTest` / `BrowserInteropTest` Kotlin classes that
implement them.

## Wire fixes ↔ scenarios

| T# | Fix | Commit | Asserting scenario(s) | Tier | Status |
|---|---|---|---|---|---|
| **T8** | Skip `BUFFER_FLAG_CODEC_CONFIG` outputs in `MediaCodecOpusEncoder` (don't emit `OpusHead` as an audio frame). | `96cfa1235` | **I11** (`first_audio_frame_is_not_opus_codec_config`) — strips Container::Legacy and asserts the first audio frame's payload doesn't begin with the `OpusHead` magic. **I14** (browser warmup) is the spec's intended browser-side mate but **NOT YET LANDED** — Phase 4.C deferred it. | hang ✅ + browser ⏳ | **partial** |
| **T10** | `endGroup()` on unmuted → muted transition (don't park the open uni stream when the speaker mutes). | `c23da5279` | **I3** (`mid_broadcast_mute_shortens_decoded_pcm`) — speaker mutes 1 s mid-broadcast; asserts the listener-side decoded PCM has a sample-count deficit consistent with stream FIN, NOT embedded zeros. A regression to "push zeros instead of FIN" would trip the upper bound. | hang ✅ | **green** |
| **T11** | Drop `bestEffort = true` on moq-lite group uni streams (unreliable streams behave irregularly under loss). | `7e76ab113` | **I9** (`packet_loss_1pct_does_not_kill_audio`) — drives the QUIC client through `udp-loss-shim` at 1 % loss; asserts ≥ 60 % of expected samples + FFT peak intact. With `bestEffort = true` re-introduced, frames lost on dropped packets would NOT be retransmitted and the sample count would crash through the floor. | hang ✅ | **green** |
| **T12** | Carry audio group sequence across hot-swaps (don't reset to 0 on speaker re-issuance — listener decoder caches by group ordering). | `be4e0b9f9` | **I5** (`speaker_hot_swap_does_not_crash`) — speaker calls `connectReconnectingSpeaker` mid-broadcast; asserts the listener sees no broadcast end and the post-swap window decodes cleanly with the 440 Hz peak intact. Group-sequence resets to 0 would cause the listener to drop "old" frames as duplicates. | hang ✅ | **green** |
| **T13** | Reset Opus decoder on publisher boundary in `NestPlayer` (so the new publisher's pre-roll doesn't start mid-frame on stale decoder state). | `4714e3c72` | **I7** (`rust_hang_publish_reconnect_kotlin_listener_recovers`, in sister branch `feat/nests-i7-publisher-reconnect`) — Rust `hang-publish` cycles its session at T+2.5 s of a 5 s broadcast; asserts ≥ 2.5 s of decoded mono PCM with the 440 Hz peak intact across the cycle. A failed decoder reset would either crash or corrupt samples mid-stream — a corrupted half would skew the FFT peak away from 440 Hz. | hang ✅ | **green (in sister branch)** |
| **T14** | Recognise `GOAWAY` control type instead of silent FIN. | `73722d2ad` | **N/A in moq-lite-03.** moq-lite has no `GOAWAY` frame on the wire; the fix protects the IETF moq-transport-17 control-decoder path (`MoqSession.kt:417`). I12 was originally specced for this but doesn't apply — see `2026-05-06-cross-stack-interop-test-results.md`'s I12 section. The IETF code path is exercised by the existing `MoqCodecTest` unit test (`unknown_control_type_skips_message_without_corruption`) only — no cross-stack scenario covers it because no cross-stack peer speaks IETF moq-transport. | unit-test only | **green** |

## Aspirational T1–T7, T9, T15

The spec's "T1–T14" enumeration is not all wire fixes. Searching
`main` for `fix(nests): T1` … `T7`, `T9`, `T15` returns no commits;
those numbers existed in the audit's findings list but didn't
crystallise into named patches before audit closure. If a future
fix re-uses one of those numbers, this matrix should be updated to
record the asserting scenario(s) alongside the listed T# above.

The spec's claim "Catches every audio-path wire regression (T1–T14)
with at least one cross-stack scenario" should be read as: catches
every audio-path wire regression that has a concrete fix in `main`,
which is the T8 / T10–T14 set.

## Spec scenario ↔ T# reverse index

For maintainers reading the test code who want to know *which* fix
each scenario protects:

| Scenario | Protects |
|---|---|
| **I1** (forward 440 Hz mono) | Baseline path: T8 + T11 (frames carry pristine Opus over reliable streams). A break of either trips the FFT or sample-count assertion. |
| **I2** (late-join) | T11 implicitly (streams must arrive in order without RST under no-loss). |
| **I3** (mute window) | **T10** explicitly. |
| **I4 fwd** (stereo 440/660) | Stereo plumbing through `AudioBroadcastConfig` (PR #2755) — not a T-series fix; protects the per-channel catalog → encoder pipeline. |
| **I4 rev** (stereo Rust → Kotlin) | Listener stereo decode path. |
| **I5** (speaker hot-swap) | **T12** explicitly. |
| **I6** (multi-listener) | T11 fan-out behaviour (in `feat/nests-i6-multi-listener`). |
| **I7** (publisher reconnect) | **T13** explicitly (in `feat/nests-i7-publisher-reconnect`). |
| **I8** (SubscribeDrop on unknown track) | Subscribe rejection handling — moq-lite-03 protocol-level guard, not a T-series fix. |
| **I9** (1 % packet loss) | **T11** explicitly. |
| **I10** (60 s long broadcast) | `framesPerGroup` cadence interaction at scale (see `2026-05-07-framespergroup-reconciliation.md`). |
| **I11** (wire-byte capture) | **T8** explicitly. |
| **I12** (Goaway) | N/A in moq-lite-03; **T14** is exercised only by the IETF moq-transport unit test path. |
| **I13** (browser `framesPerGroup=50` + `Container.Consumer`) | **NOT YET LANDED** — Phase 4.C deferred. Would protect the production cadence end-to-end against the WebCodecs decode path. |
| **I14** (WebCodecs warmup × CSD-skip) | **NOT YET LANDED** — Phase 4.C deferred. Browser-side mate of I11; together they'd cover T8 on both rendering paths. |
| **I15** (Chromium ALPN round-trip) | moq-lite ALPN drift detection (in `feat/nests-browser-interop`). |

## Coverage holes

Scenarios specced as P0 / P1 in the spec but **not yet landed**:

- **I13** (browser `framesPerGroup=50` long broadcast) — P0 browser-tier. Protects nothing today on the browser path; T11's browser-side coverage is implicit in I1 alone.
- **I14** (WebCodecs warmup × CSD-skip) — P0 browser-tier. Hang-tier I11 catches T8 on the wire; without I14 a browser-side regression where the WebCodecs decoder mishandles a warmup-period CSD blob would silently pass.

For the merge-ready interop test branches:

- T13's asserting scenario (I7) is in `feat/nests-i7-publisher-reconnect` — when that merges into `claude/cross-stack-interop-test-XAbYB` (or main), this matrix should drop the "in sister branch" qualifier.
- I6's asserting scenario is in `feat/nests-i6-multi-listener` — same caveat.

## Files referenced

- `nestsClient/plans/2026-05-06-cross-stack-interop-test.md` (spec — Definition of Done #5)
- `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md` (results — what landed)
- `nestsClient/plans/2026-05-07-framespergroup-reconciliation.md` (cadence reconciliation)
- `nestsClient/plans/2026-05-07-i7-post-reconnect-cliff-investigation.md` (I7 cycle-2 cliff)
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropTest.kt`
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropReverseTest.kt` (sister branch)
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/HangInteropMultiListenerTest.kt` (sister branch)
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/native/BrowserInteropTest.kt` (sister branch)
- T# fix commits: `96cfa1235` (T8), `c23da5279` (T10), `7e76ab113` (T11), `be4e0b9f9` (T12), `4714e3c72` (T13), `73722d2ad` (T14)
