# Audio rooms — completion plan

> **STATUS (2026-04-26 PM):** Listener and speaker paths are both live
> end-to-end on moq-lite Lite-03, the create-space flow ships, and the
> kind-10112 host-server list lives in Settings. What remains is
> hardening (reconnect / leave cleanup / per-speaker level metering),
> Nests-feature parity (chat, hand-raise approval, recordings — see
> the integration audit), and Desktop / iOS targets.
> `:nestsClient:jvmTest` (~140 unit tests) is green; integration tests
> behind `-DnestsInterop=true` work against a real Docker'd nostrnests
> stack.

## Implementation state

### Transport + protocol

| Surface | Status | Notes |
|---|---|---|
| `:quic` v1 + HTTP/3 + WebTransport | ✅ done | RFC 9000 + 9220; auditor passes |
| moq-lite Lite-03 codec (announce / subscribe / group) | ✅ done | `moq/lite/`, full unit suite |
| moq-lite session (listener + publisher) | ✅ done | `MoqLiteSession.client(...)` |
| `WebTransportSession.incomingBidiStreams` + `openUniStream` | ✅ done | needed for publisher |
| IETF `draft-ietf-moq-transport-17` codec | ✅ done | reference impl, no production caller |

### `:nestsClient`

| Surface | Status | Notes |
|---|---|---|
| HTTP `/auth` JWT mint (NIP-98 → ES256 JWT) | ✅ done | `OkHttpNestsClient.mintToken` |
| `connectNestsListener` → `MoqLiteNestsListener` | ✅ done | path = `/<namespace>?jwt=<token>` |
| `connectNestsSpeaker` → `MoqLiteNestsSpeaker` | ✅ done | publishes `audio/data` track |
| `AudioRecordCapture` + `MediaCodecOpusEncoder` | ✅ done | Android actuals |
| `MediaCodecOpusDecoder` + `AudioTrackPlayer` | ✅ done | Android actuals |
| `AudioRoomMoqLiteBroadcaster` + `AudioRoomPlayer` | ✅ done | encode / decode pumps |
| Interop harness (Docker compose nostrnests stack) | ✅ done | `NostrNestsHarness`, 4 test classes |

### Amethyst (Android UI)

| Surface | Status | Notes |
|---|---|---|
| `AudioRoomsScreen` feed of kind-30312 events | ✅ done | from followed users |
| `AudioRoomJoinCard` → launches `AudioRoomActivity` | ✅ done | passes service / endpoint / hostPubkey |
| `AudioRoomActivity` (full screen + PIP) | ✅ done | lifecycle anchor |
| `AudioRoomViewModel` (listener + speaker state) | ✅ done | `commons/.../viewmodels/` |
| Foreground service (`AudioRoomForegroundService`) | ✅ done | listening + microphone variants |
| Mute / unmute / hand-raise UI | ✅ done | drives `BroadcastHandle.setMuted` + presence |
| Kind-10312 presence heartbeat | ✅ done | 30 s + debounced state-change publish |
| **"Start space" FAB → `CreateAudioRoomSheet`** | ✅ done | publishes kind-30312 with caller as host |
| **Settings → Audio-room servers (kind 10112)** | ✅ done | replaceable list, defaults to `nostrnests.com` |

### Pending

| Item | Phase | Effort |
|---|---|---|
| Reconnect / retry on listener Failed | M8 | 1 d |
| Per-speaker level meters (RMS from decoder output) | M8 | 1 d |
| Connection-failure UX copy (typed reasons → strings) | M8 | 0.5 d |
| Desktop audio capture + playback | future | gated on Compose Desktop AudioInput API |
| iOS audio capture + playback | future | new `iosMain` actuals |
| Nests-feature parity (chat, mod, recordings, …) | see integration audit | varies |

## Pointers

- moq-lite wire spec + IETF gap: `nestsClient/plans/2026-04-26-moq-lite-gap.md`
- Nostrnests integration audit (gaps + roadmap): see most recent doc in `nestsClient/plans/`
- QUIC stack status: `quic/plans/2026-04-26-quic-stack-status.md`
- Audio-rooms NIP draft (needs refresh after the moq-lite findings):
  `docs/plans/2026-04-22-nip-audio-rooms-draft.md`
- Listener entry: `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/NestsConnect.kt`
- Speaker entry: same file (`connectNestsSpeaker`)
- Create-space sheet: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/audiorooms/create/`
- Servers settings: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/actions/nestsServers/`
