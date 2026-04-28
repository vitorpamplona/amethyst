# Listener-survives-publisher-recycle gap

**Status:** open. Investigation done; first-cut wrapper-layer fix
attempted and reverted.

**Discovered:** while validating
`NostrNestsReconnectingSpeakerInteropTest` against the real
`kixelated/moq` relay (`-DnestsInterop=true -DnestsInteropExternal=true`,
host stack: `cargo build moq-relay` from
`~/.cache/amethyst-nests-interop/nests/moq` HEAD, host-built moq-auth
on 8090, relay on 4443 with `--auth-key-dir <dir>` per-kid JWK).

## The gap

When a publisher (speaker) closes its session — e.g. mid-room JWT
refresh via [`connectReconnectingNestsSpeaker`] — and a fresh
publisher comes up moments later under the same broadcast suffix, an
existing listener-side `SubscribeHandle` returned from
[`NestsListener.subscribeSpeaker`] does NOT auto-reattach to the new
publisher. Frames stop. The listener stays Connected; its session
isn't dropped; but no audio reaches the consumer until the listener's
own JWT refresh fires (every 540 s by default in
`connectReconnectingNestsListener`) — and even then, only because
the listener's session swap re-issues subs via the existing
`reissuingSubscribe` pump.

In production, every speaker JWT refresh kills audio for any listener
that joined within the last 540 s, until that listener's own refresh
catches up. For a typical Nest room with people joining at staggered
times, this is a real every-9-min audio dropout.

## Why it happens

Three layers contribute:

1. **moq-lite session level.**
   `MoqLiteSubscribeHandle.frames` is a `Channel.consumeAsFlow()`. The
   channel is closed only by an explicit `unsubscribe()` call — the
   moq-lite session does NOT close the channel when the publisher's
   session disappears. The flow thus sits idle indefinitely.

2. **moq-lite Lite-03 protocol.** Per the gap plan
   (`2026-04-26-moq-lite-gap.md`):

   > Disconnect is **not** an explicit Ended (see Cleanup). [...]
   > Mid-broadcast publisher disconnect: relay either FINs/resets the
   > announce bidi or emits `Announce::Ended` if graceful.

   `MoqLiteNestsSpeaker.close()` → `MoqLiteBroadcastHandle.close()` →
   `publisher.close()` does emit `Announce(Ended)` on graceful close.
   So in the JWT-refresh path the listener SHOULD see an Ended
   announce — IF the announce flow is being collected.

3. **wrapper level.** `ReconnectingNestsListener.reissuingSubscribe`
   only re-issues on `activeListener` swaps — not on
   announce-Ended. The pump's inner `handle.objects.collect { ... }`
   blocks forever once the publisher disappears (per #1).

## Attempted fix (reverted)

Wrapper-layer announce-driven re-subscribe in
`reissuingSubscribe`:

```
coroutineScope {
    val collectJob = launch { handle.objects.collect { frames.emit(it) } }
    val triggerJob = launch {
        listener.announces()
            .filter { it.pubkey == broadcastSuffix && !it.active }
            .first()
    }
    select<Unit> {
        collectJob.onJoin {}
        triggerJob.onJoin {}
    }
    collectJob.cancel(); triggerJob.cancel()
}
// then unsubscribe + delay + loop into a fresh subscribe
```

Plus pass `broadcastSuffix` through `subscribeSpeaker` /
`subscribeCatalog`, and short-circuit `triggerJob` to
`awaitCancellation()` when `announces()` throws
`UnsupportedOperationException` (IETF reference path).

**Result against the real relay:**
`subscribe_handle_survives_publisher_recycle` test still failed.
Relay log showed the listener QUIC session terminating ~4 ms after
the publisher's "subscribe cancelled" — i.e. our client closed the
QUIC connection mid-test. Suspect cause: the `listener.announces()`
flow's `finally { handle.close() }` cleanup combined with the
sub-`unsubscribe()` in our retry path is being interpreted at the
moq-lite session layer as "session done", or the announce bidi's
finish propagates session-level close. Did not pin down the exact
chain in the time available.

Reverted to keep the branch clean. Speaker-side
`connectReconnectingNestsSpeaker` is unaffected and works
correctly (verified in
`NostrNestsReconnectingSpeakerInteropTest`).

## What a correct fix needs

Probably one of these, in order of safety:

- **Session-layer fix.** Make `MoqLiteSubscribeHandle.frames`
  complete cleanly when the publisher's session ends. Two paths
  worth checking:
  - The relay FINs the subscribe bidi when the publisher
    disconnects → our `pumpUniStreams` / response reader detects
    it → we close `frames`. Need to check the moq-lite session
    code for whether it monitors the bidi for FIN after the
    `SubscribeOk` arrives.
  - The relay forwards `Announce(Ended)` on the announce bidi →
    a session-internal hook closes any subscriptions matching the
    suffix. This is the moq-rs relay's preferred path.

- **Wrapper-level redesign that doesn't open new bidis on every
  retry.** Make the announce flow a single shared subscription
  per session, multiplexed across all
  `subscribeSpeaker`/`subscribeCatalog` calls. The current attempt
  opened a fresh announce bidi per re-subscribe attempt, which is
  what (we think) destabilized the session.

- **Coordinate listener and speaker JWT refresh windows so they're
  always synchronous.** The listener's own session-swap pump
  already re-issues subs correctly — if the listener happens to
  swap at the same moment as the publisher, the gap is invisible.
  Not a fix per se but a workaround that hides the gap when
  refresh is the only recycle source.

## Production impact today

For a v1 Nest room with most calls under 9 minutes: no impact.

For a long room (>9 minutes of any single speaker on stage):
listener audio dropout for the duration of one listener-JWT-refresh
window per speaker recycle — roughly N to 540 s, depending on when
the listener joined relative to the speaker. Heard as "speaker
suddenly went silent, then comes back".

Reproducer interop test was written and confirmed the failure
on the real relay; reverted along with the wrapper changes since
shipping a known-failing interop test isn't useful. Saved here for
the next person; the diff lived in commit
[unrecorded — discard before push] in this session.

## Files of interest for follow-up

- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/MoqLiteSession.kt`
  (lines ~180-214 for subscribe path, ~583+ for ListenerSubscription
   bookkeeping)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/MoqLiteNestsListener.kt`
  (the Flow-mapping wrapper)
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/ReconnectingNestsListener.kt`
  (`reissuingSubscribe`, the pump that needs the inner re-subscribe
   trigger)
- The host stack recipe used to reproduce, in the speaker-reconnect
  PR description: build moq-relay from `~/.cache/.../nests/moq`,
  use `--auth-key-dir <dir>` with a single `<kid>.jwk` extracted from
  moq-auth's JWKS.
