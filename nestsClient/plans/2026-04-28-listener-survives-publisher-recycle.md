# Listener-survives-publisher-recycle: resolution + open follow-ups

**Status:** ✅ Resolved for the production-relevant case
(publisher recycles, listener doesn't). One pre-existing test
exposes a separate, narrower issue that is **out of scope here**
— see "Open: session-swap test" below.

## What was broken (recap)

When a remote speaker JWT-refreshed (every 9 min via
`connectReconnectingNestsSpeaker`), any listener with a vanilla
`SubscribeHandle` open against that speaker's broadcast went silent.
The listener's session stayed Connected; the listener's
`MoqLiteSubscribeHandle.frames` `Channel.consumeAsFlow()` just sat
open waiting for frames that never arrived. moq-lite Lite-03 has no
explicit "publisher gone" message on the subscribe bidi (the relay
keeps that bidi open across publisher cycles in case a fresh
publisher takes over the suffix), so the announce stream's
`Announce(Ended)` event is the only reliable signal.

## What landed (commit `851045c6`)

**Session layer** (`MoqLiteSession.kt`):

- Lazy single shared announce-watch pump per session, opened on
  first subscribe.
- On `Announce(Ended)` for a broadcast suffix, close the matching
  `ListenerSubscription`'s frames `Channel` and remove it from the
  map.

**Wrapper layer** (`ReconnectingNestsListener.kt`):

- Inner `while (currentCoroutineContext().isActive)` loop in
  `reissuingSubscribe`. When the underlying frames flow completes
  (signalled by the session layer), re-issue subscribe against the
  same listener with a 100 ms backoff. moq-lite supports
  subscribe-before-announce so the new subscribe attaches cleanly
  when the next publisher comes up under the same suffix.

**Verified against the real moq-rs relay** (host build, external
mode): the new
`NostrNestsReconnectingListenerInteropTest.subscribe_handle_survives_publisher_recycle`
test passes — single SubscribeHandle keeps emitting frames across
multiple speaker JWT-refresh cycles. Speaker reconnect tests still
pass too.

## Open: session-swap test

`NostrNestsReconnectingListenerInteropTest.reconnecting_wrapper_keeps_handle_alive_across_session_swap`
still fails in interop runs (with both pre-existing and current
code). Investigation revealed **two compounding issues**, only one
of which my fix addresses:

1. **Orchestrator break-on-Closed** (one cause): the orchestrator's
   `if (terminal is NestsListenerState.Closed) break` exits whenever
   the inner listener goes Closed for ANY reason. This includes
   the test's `firstListener.close()` call. Pre-fix the orchestrator
   stopped → no reconnect → `opens=1`. Removing the break (orchestrator
   loops on Closed too) gets `opens=2`, but exposes the second
   issue below. Decision: **keep the break-on-Closed for now** —
   user-driven `reconnecting.close()` already cancels the
   orchestrator coroutine separately, and removing the break would
   fight a deeper publisher issue without a corresponding fix.

2. **Publisher single-group architecture** (the deeper cause):
   `NestMoqLiteBroadcaster` only ever calls `publisher.send(opus)`
   — never `startGroup()` / `endGroup()`. So the entire broadcast
   is one giant moq-lite group. A subscriber that joins
   mid-broadcast (the test's listener-2 case) gets nothing because
   moq-lite's "from-latest" subscribe semantics give the next
   group's frames; if the publisher is in the middle of a
   never-ending group, the new subscriber waits indefinitely.

   The listener-survives-publisher-recycle path doesn't hit this
   because each speaker JWT cycle creates a fresh publisher session
   with a fresh group — the listener-side resubscribe naturally
   lands on a new group.

   Fixing this properly would require periodic group rotation in
   `NestMoqLiteBroadcaster` (e.g. one group per second, or per N
   frames). That's a substantive audio-pipeline change with its
   own concerns (jitter buffer interaction, listener seek
   semantics) — out of scope for the listener-survival work.

The interop test's expectation — that closing the inner listener
mid-stream forces a clean session swap with continuous frame flow
— is unrealistic against the current publisher architecture. The
test was passing pre-my-changes because the orchestrator broke on
Closed (issue 1) BEFORE issue 2 could be observed; with the break
in place, the test never gets to verify frame continuity, and
fails earlier with `opens=1`. Either way, the test fails — it just
fails for different reasons before vs. after issue 1 is fixed.

For follow-up:

- Consider rotating moq-lite groups in `NestMoqLiteBroadcaster`
  on a fixed cadence so mid-stream listener subscribes work.
- Once rotation lands, consider removing the
  orchestrator's `break-on-Closed` so that listener-side recycles
  via `firstListener.close()` (or analogous transport-peer-close
  paths) trigger a wrapper-level reconnect. Today, the only
  documented path for the wrapper to spin up a fresh inner listener
  is via the JWT refresh window or a Failed terminal state.

## Files relevant to follow-up

- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/audio/NestMoqLiteBroadcaster.kt`
  — where to add periodic `endGroup()` + new group on send.
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/ReconnectingNestsListener.kt:174`
  — where the break-on-Closed lives.
- `nestsClient/src/jvmTest/kotlin/com/vitorpamplona/nestsclient/interop/NostrNestsReconnectingListenerInteropTest.kt`
  — `reconnecting_wrapper_keeps_handle_alive_across_session_swap`
  is the failing-but-pre-existing test that captures both issues.
