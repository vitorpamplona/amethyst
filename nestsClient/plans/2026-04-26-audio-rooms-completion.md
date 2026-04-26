# Audio rooms — completion plan (2026-04-26)

What's left between today's code and shippable audio rooms in Amethyst.

## Where we are

The transport stack is **done** and audited
([quic/plans/2026-04-26-quic-stack-status.md](../../quic/plans/2026-04-26-quic-stack-status.md)).
On top of it, `:nestsClient` already has:

- HTTP control plane (`NestsClient.resolveRoom` — NIP-98 auth → room info)
- WebTransport adapter (`QuicWebTransportFactory` wires `:quic` into the
  `WebTransportSession` interface)
- MoQ session — listener side: `MoqSession.client(...)` + `setup()` +
  `subscribe(namespace, trackName, filter)` + control + datagram pumps
- Opus decode + audio playback chain: `MediaCodecOpusDecoder`,
  `AudioTrackPlayer`, `AudioRoomPlayer`
- `NestsListener` API + `connectNestsListener` orchestration
- Audio capture primitives (`AudioRecordCapture`, `MediaCodecOpusEncoder`)
  exist but are not wired into a publisher path

Amethyst's `audiorooms/` UI parses NIP-53 events and renders rooms +
participant chips. It does NOT call `NestsListener` — there's no Connect
button, no audio output, no mute control wired.

So the punch list is: app-side wiring → manual interop validation → speaker
path → backgrounding & polish.

## Phase M1 — Listener-only MVP (1 week)

**Goal:** open a real audio room from the Amethyst UI, hear one speaker.

- Wire `connectNestsListener` into a `RememberRoomConnection` composable in
  `amethyst/.../audiorooms/room/`. Lifecycle tied to `DisposableEffect`;
  cancels on screen exit.
- Surface `NestsListenerState` in the UI:
  - `Idle` / `Connecting` → show a spinner or chip "Connecting…"
  - `Connected` → show "Audio connected" chip + auto-subscribe to the host's
    speaker track (NIP-53 room's `p` tag with role `host`)
  - `Failed(reason, cause)` → toast / inline message
- `AudioRoomPlayer` per subscription. Per the audio-rooms NIP draft
  (`docs/plans/2026-04-22-nip-audio-rooms-draft.md`) one speaker = one track
  name = `<speaker-pubkey-hex>`; one `AudioRoomPlayer` per speaker.
- Mute toggle drives `AudioPlayer.setVolume(0f / 1f)` on the active player.
  Mute at the player keeps the network running so unmute is instant.
- Backed by an `AudioRoomViewModel` in `commons/.../viewmodels/` so desktop
  can reuse the orchestration once it gets WT.

Tests:
- Manual: connect to `nostrnests.com`, open a known room, hear audio.
- Unit: `AudioRoomViewModel` state-flow transitions on
  `NestsListenerState` updates.

## Phase M2 — Multi-speaker + audience UX (3 days)

- Subscribe to every `host` + `speaker` `p` tag, not just the first one.
  Mix at the audio side (Android `AudioTrack` accepts multiple writers if
  we use one shared track + downmix; cleaner: one `AudioTrack` per
  subscription and let the OS mix).
- Show per-speaker level meters (if the encoder exposes RMS) or just a
  speaking indicator driven by "objects received in last 200 ms".
- React to NIP-53 room event updates: a new speaker added to `p` →
  open a subscription; a speaker removed → close one.

## Phase M3 — Foreground service (2 days)

- Android `MediaSessionService` with a media-style notification so
  playback continues when the app backgrounds.
- Stop the service on:
  - screen exit AND no other audio-room-screen is alive
  - user dismisses the notification
  - underlying `NestsListener` enters `Failed` or `Closed`
- Permission shim: `RECORD_AUDIO` is NOT needed for listener-only.

## Phase M4 — Manual interop pass against `nostrnests.com` (3 days)

This is the proof-of-life step before any speaker work.

- Build a debug build with the listener flow above.
- Open one of the long-running test rooms hosted by nests.
- Confirm: connect succeeds; SUBSCRIBE_OK arrives; OBJECT_DATAGRAMs
  decode through MediaCodec into audible audio.
- Anything that surfaces here goes into a follow-up audit / fix pass on
  `:quic` or `:nestsClient`. We expect one or two issues — protocol drafts
  drift, and we've only verified against aioquic, not a real MoQ relay.
- Capture a packet trace if anything fails so we can compare on-the-wire
  bytes against a known-working JS client.

## Phase M5 — Speaker path: MoQ publisher (1 week)

The big one. `MoqSession` only does subscribe today; it needs ANNOUNCE +
OBJECT emission.

Required MoQ messages to encode + decode:

| Message | Direction | Status |
|---|---|---|
| ANNOUNCE | client → server | not implemented |
| ANNOUNCE_OK / ANNOUNCE_ERROR | server → client | decode + match-by-namespace |
| ANNOUNCE_CANCEL | server → client | decode + signal publisher to stop |
| UNANNOUNCE | client → server | encode |
| SUBSCRIBE | server → client (we're publisher) | accept + map to our track sink |
| SUBSCRIBE_OK / SUBSCRIBE_ERROR | client → server | encode |
| SUBSCRIBE_DONE | client → server | encode on track end |
| OBJECT_DATAGRAM (publish-side) | client → server | encode + emit |

API we need on `MoqSession`:

```kotlin
suspend fun announce(
    namespace: TrackNamespace,
    parameters: List<TrackParameter> = emptyList(),
): AnnounceHandle

interface AnnounceHandle {
    /** New publisher per track name we serve under this namespace. */
    suspend fun openTrack(name: ByteArray): TrackPublisher
    /** Stop announcing; sends UNANNOUNCE + closes any open track publishers. */
    suspend fun unannounce()
}

interface TrackPublisher {
    /** Push one OBJECT_DATAGRAM. group/objectId are managed internally
     *  per the audio-rooms NIP. */
    suspend fun send(payload: ByteArray)
    suspend fun close()
}
```

Internal additions:
- `pendingAnnounces` keyed by namespace, like the existing
  `pendingSubscribes`
- inbound-SUBSCRIBE routing: when the server SUBSCRIBEs, we look up the
  publisher by namespace+name and start delivering its objects with the
  server-assigned subscribeId/trackAlias
- group/object id management: monotonic group per
  `TrackPublisher`, object id zero-reset per group; reflect this in the
  emitted `OBJECT_DATAGRAM` header

Tests:
- `MoqSession` unit tests for ANNOUNCE round-trip via `FakeWebTransport`
- Integration: a publisher sends 100 Opus-shaped payloads through to a
  matching subscriber, all received with intact group/object ids

## Phase M6 — Capture → encode → publish (3 days)

The inverse of `AudioRoomPlayer`:

- `AudioCaptureSource` (commonMain interface) with platform actuals on
  `AudioRecordCapture` (Android) and a desktop one later
- `AudioRoomBroadcaster` orchestrates: pull PCM frames from the capture →
  feed `MediaCodecOpusEncoder` → push the resulting Opus packet into
  `TrackPublisher.send`
- `RECORD_AUDIO` permission gate — surface on first-tap of the talk button
- Push-to-talk vs always-on toggle: at the API level, just `start()` /
  `stop()` on the broadcaster; the UI decides

## Phase M7 — `NestsSpeaker` API (2 days)

Mirror of `NestsListener` for hosts/speakers:

```kotlin
interface NestsSpeaker {
    val state: StateFlow<NestsSpeakerState>
    suspend fun startBroadcasting(): BroadcastHandle
    suspend fun close()
}

interface BroadcastHandle {
    suspend fun setMuted(muted: Boolean)
    suspend fun close()
}
```

Same `connectNestsSpeaker` orchestration as `connectNestsListener` but the
post-`setup` step is `announce(...)` instead of `subscribe(...)`.

UI:
- Talk button only enabled when our pubkey is in the room's `p` tags with
  role `host` or `speaker`
- "Live" indicator while broadcasting, level meter from the encoder
- Mute / unmute drives `BroadcastHandle.setMuted`

## Phase M8 — App polish (3-5 days)

- Connection-recovery: `NestsListener` exposes `reconnect()`; the screen
  retries on `Failed` after a short backoff
- Room-leave cleanup: on screen exit, send UNSUBSCRIBE + UNANNOUNCE before
  closing the WT session (audit-4 / 5 already wired the
  `WtCloseSession` capsule emit on `close()`)
- Surface server `peerGoawayProtocolError` and the various
  `NestsListenerState.Failed` reasons as user-readable messages
- iOS: stub everything in `iosMain` with `expect`s that error cleanly until
  iOS audio capture/playback land

## Phase M9 — Backgrounding for speakers (2 days)

Different from M3 because capture has stricter Android rules:
- Foreground service type `microphone` (Android 14+ requires this)
- Notification with prominent "Speaking" indicator + mute action

## Out of scope for this plan

- **Recording / saving** room audio.
- **Server-mixed audio.** Each speaker is a separate track per the NIP
  draft; mixing is client-side.
- **Video.** We support audio only.
- **Accessibility transcription.**
- **Desktop audio capture** (until Compose Desktop has a stable
  `AudioInput` API; today's options are JNA-heavy).

## Timeline

| Phase | Days | Cumulative |
|---|---|---|
| M1 Listener wire-up | 5 | 5 |
| M2 Multi-speaker | 3 | 8 |
| M3 Foreground listener | 2 | 10 |
| M4 Real-server interop | 3 | 13 |
| M5 MoQ publisher | 5 | 18 |
| M6 Capture + encode | 3 | 21 |
| M7 NestsSpeaker | 2 | 23 |
| M8 Polish | 4 | 27 |
| M9 Foreground speaker | 2 | 29 |

≈ **6 weeks** to ship full audio rooms (listener + speaker + Android polish).
≈ **2 weeks** to ship listener-only (M1+M3+M4) which is the 95% case for
audience members.

## Stop conditions

- **M4 reveals the QUIC stack can't reach `nostrnests.com`** — drop into a
  protocol-comparison pass (likely a draft-version mismatch or a small
  framing bug). Up to 1 wk of `:quic` adjustment, otherwise we ship behind
  a feature flag and chase interop async.
- **MediaCodec Opus is missing on a target device.** Android 10+ ships the
  decoder; for older devices we'd need a software Opus, which is out of
  scope.

## Pointers

- QUIC stack status: `quic/plans/2026-04-26-quic-stack-status.md`
- Audio-rooms NIP draft: `docs/plans/2026-04-22-nip-audio-rooms-draft.md`
- Original (frozen) QUIC plan: `docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md`
- Existing listener entry point: `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/NestsListener.kt`
- App-side audio-room screen: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/audiorooms/`
