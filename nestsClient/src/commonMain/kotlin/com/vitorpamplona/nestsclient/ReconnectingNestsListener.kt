/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.moq.SubscribeOk
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * `connectNestsListener` plus a transport-loss reconnect loop with
 * exponential backoff (the JS reference's
 * `Connection.Reload`-style behaviour).
 *
 * The returned [NestsListener] surfaces the underlying listener's
 * state directly while a session is alive, but flips to
 * [NestsListenerState.Reconnecting] between attempts. The listener
 * is auto-redirected to the freshly-opened session under the hood
 * — `subscribeSpeaker(...)` calls always go to the latest live
 * session.
 *
 * **Subscribe-handle re-issuance** — caller-owned [SubscribeHandle]s
 * survive a reconnect. `subscribeSpeaker` returns a handle backed
 * by a [MutableSharedFlow]; the orchestrator opens an underlying
 * subscription against each fresh session and forwards its frames
 * into the shared flow, so the consumer's collector keeps emitting
 * even after a transport drop. The handle's [SubscribeHandle.unsubscribe]
 * cancels the pump and best-effort cancels the live underlying
 * subscription.
 *
 * Cancellation: cancelling [scope] (typically the room screen's
 * VM scope) cancels the reconnect loop and closes the active
 * session. [NestsListener.close] is idempotent.
 */
suspend fun connectReconnectingNestsListener(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    policy: NestsReconnectPolicy = NestsReconnectPolicy(),
    /**
     * Proactive JWT refresh window. moq-auth issues bearer tokens
     * with a 600 s lifetime; once the token expires the relay
     * tears down the WebTransport session and we'd otherwise
     * recover via the regular reconnect path with a brief audible
     * dropout. By recycling the session a minute before expiry we
     * stay ahead of the relay's tear-down: the new session opens,
     * the [SubscribeHandle] re-issuance pump cuts subs over to
     * it, and the listener never enters the user-visible
     * Reconnecting state. Set to 0 or negative to disable.
     */
    tokenRefreshAfterMs: Long = 540_000L,
    /**
     * Test seam — defaults to the production
     * [connectNestsListener]. Tests pass a fake that returns a
     * scripted [NestsListener] so the reconnect state machine can
     * be exercised without a real WebTransport stack.
     */
    connector: suspend () -> NestsListener = {
        connectNestsListener(httpClient, transport, scope, room, signer)
    },
): NestsListener {
    val state = MutableStateFlow<NestsListenerState>(NestsListenerState.Idle)
    val activeListener = MutableStateFlow<NestsListener?>(null)

    suspend fun openOnce(): NestsListener {
        val listener = connector()
        activeListener.value = listener
        state.value = listener.state.value
        return listener
    }

    val orchestrator =
        scope.launch {
            var attempt = 0
            while (true) {
                val listener =
                    try {
                        openOnce()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        // Orchestrator scope was cancelled mid-connect —
                        // propagate so the reconnect loop dies promptly.
                        // `runCatching` would have swallowed this and
                        // run one more loop iteration (potentially
                        // re-opening a doomed listener) before the next
                        // suspending call re-checked the cancel.
                        throw ce
                    } catch (t: Throwable) {
                        state.value = NestsListenerState.Failed("connect failed: ${t.message}", t)
                        null
                    }
                var refreshTriggered = false
                if (listener != null) {
                    // Wait for either a terminal state OR the proactive
                    // JWT-refresh deadline. withTimeoutOrNull returns
                    // null when the timer fires first; we then close
                    // the (still-healthy) listener and loop to mint a
                    // fresh JWT via openOnce(). The SubscribeHandle
                    // re-issuance pump cuts subs over to the new
                    // session without surfacing Reconnecting to the UI.
                    //
                    // `return@collect` does NOT break a StateFlow's
                    // collect (it just returns from the lambda for one
                    // emission), so we use `onEach { mirror } + first`
                    // to wait deterministically for a terminal state.
                    val terminalAwait: suspend () -> NestsListenerState = {
                        listener.state
                            .onEach { state.value = it }
                            .first { s ->
                                s is NestsListenerState.Failed || s is NestsListenerState.Closed
                            }
                    }
                    val terminal =
                        if (tokenRefreshAfterMs > 0L) {
                            withTimeoutOrNull(tokenRefreshAfterMs) { terminalAwait() }
                        } else {
                            terminalAwait()
                        }
                    if (terminal == null) {
                        // Refresh deadline hit before any terminal state —
                        // planned recycle, not a failure. Close the old
                        // listener; don't bump `attempt` (it's not a
                        // backoff event) so the next openOnce() runs
                        // immediately.
                        try {
                            listener.close()
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            // Best-effort — the listener will GC either way.
                        }
                        attempt = 0
                        refreshTriggered = true
                    } else if (terminal is NestsListenerState.Failed && !isUserCancelled(terminal)) {
                        // Transport-side failure → schedule a reconnect.
                        attempt++
                        if (!policy.isExhausted(attempt)) {
                            val delayMs = policy.delayForAttempt(attempt)
                            state.value = NestsListenerState.Reconnecting(attempt, delayMs)
                        }
                    }
                }
                if (refreshTriggered) {
                    // Skip the reconnect-schedule path entirely — a
                    // refresh is a planned cutover, not a backoff
                    // event. The next iteration's openOnce() runs
                    // immediately and the wrapper's outward state
                    // never enters Reconnecting.
                    continue
                }
                // Note: we do NOT break on terminal=Closed. The
                // user-driven stop path goes through
                // [ReconnectingHandle.close], which calls
                // `orchestrator.cancel()` BEFORE closing the inner
                // listener; cancellation propagates through the
                // next suspending call (typically `delay` below or
                // `openOnce` on the next loop iteration) and the
                // orchestrator exits cleanly. Any *other* path that
                // produces a Closed inner listener — peer-driven
                // transport close, half-broken session that was
                // closed by some internal cleanup — should be
                // treated as an unexpected drop and reconnected.
                if (policy.isExhausted(attempt + 1)) break
                val terminal = state.value
                val delayMs =
                    if (terminal is NestsListenerState.Reconnecting) {
                        terminal.delayMs
                    } else {
                        policy.delayForAttempt(++attempt)
                    }
                state.value = NestsListenerState.Reconnecting(attempt.coerceAtLeast(1), delayMs)
                delay(delayMs)
            }
        }

    return ReconnectingHandle(state, activeListener, orchestrator, scope)
}

private fun isUserCancelled(state: NestsListenerState.Failed): Boolean {
    val msg = state.reason
    // The user-driven close path comes through Closed, not Failed,
    // so a Failed state is always a transport / handshake error.
    // This hook is a forward-compat seam in case a future
    // listener emits Failed on user dispose — extending the
    // pattern is local.
    return msg.contains("user cancelled", ignoreCase = true)
}

private class ReconnectingHandle(
    private val mutableState: MutableStateFlow<NestsListenerState>,
    private val activeListener: MutableStateFlow<NestsListener?>,
    private val orchestrator: Job,
    private val scope: CoroutineScope,
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    override suspend fun subscribeSpeaker(
        speakerPubkeyHex: String,
        maxLatencyMs: Long,
    ): SubscribeHandle = reissuingSubscribe { listener -> listener.subscribeSpeaker(speakerPubkeyHex, maxLatencyMs) }

    override suspend fun subscribeCatalog(speakerPubkeyHex: String): SubscribeHandle = reissuingSubscribe { listener -> listener.subscribeCatalog(speakerPubkeyHex) }

    /**
     * The announce flow is a cold per-collect stream rather than a
     * SubscribeHandle, so it doesn't go through [reissuingSubscribe].
     * Instead, restart the inner collect on every activeListener
     * change — `collectLatest` cancels the prior collection and
     * re-runs against the fresh session. Best-effort: an inner
     * listener that throws (IETF reference path) is logged-and-
     * swallowed so a future moq-lite session keeps emitting.
     */
    override fun announces(): Flow<RoomAnnouncement> =
        flow {
            Log.d("NestRx") { "wrapper.announces() flow starting collect on activeListener" }
            var iter = 0
            activeListener.collectLatest { listener ->
                iter += 1
                Log.d("NestRx") { "wrapper.announces() iter=$iter activeListener=${if (listener == null) "null" else "set"}" }
                if (listener == null) return@collectLatest
                val terminalOrConnected =
                    listener.state.first { state ->
                        state is NestsListenerState.Connected ||
                            state is NestsListenerState.Closed ||
                            state is NestsListenerState.Failed
                    }
                Log.d("NestRx") { "wrapper.announces() iter=$iter inner state=${terminalOrConnected::class.simpleName}" }
                if (terminalOrConnected !is NestsListenerState.Connected) return@collectLatest
                var fwd = 0
                val outcome =
                    runCatching {
                        listener.announces().collect {
                            fwd += 1
                            emit(it)
                        }
                    }
                Log.w("NestRx") {
                    val why = outcome.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" } ?: "naturally"
                    "wrapper.announces() iter=$iter inner collect ended $why fwd=$fwd"
                }
            }
        }

    /**
     * Open a SubscribeHandle whose `objects` flow survives session
     * swaps. Each time the wrapper opens a fresh inner listener, the
     * pump cancels the prior subscription and calls [opener] against
     * the new session, forwarding its frames into the consumer-facing
     * [MutableSharedFlow]. Same shape for both `subscribeSpeaker`
     * and `subscribeCatalog`; the only difference is which inner
     * subscribe call to make.
     */
    private fun reissuingSubscribe(opener: suspend (NestsListener) -> SubscribeHandle): SubscribeHandle {
        // Require a live (or just-connected) session at call time —
        // matches the existing IETF / moq-lite listener contract so
        // a caller can't accidentally subscribe in Idle and stall
        // waiting for a session that may never arrive.
        activeListener.value
            ?: error("no live session — wait for state == Connected before subscribing")

        // DROP_OLDEST so a stalled consumer doesn't back-pressure the
        // pump → underlying handle → relay. For Opus audio the user
        // wants playback to stay "live" rather than catch up on
        // minutes-stale buffered frames after a UI hiccup or
        // foreground/background transition.
        val frames =
            MutableSharedFlow<MoqObject>(
                extraBufferCapacity = SUBSCRIBE_BUFFER,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val liveHandleRef = AtomicReference<SubscribeHandle?>(null)

        // Re-subscribe pump. Two re-issue triggers, layered:
        //
        //   1. Listener session swap (outer collectLatest) — fires
        //      when the orchestrator opens a fresh listener after
        //      the 540 s JWT-refresh window or a transport-loss
        //      reconnect. collectLatest cancels the prior pump
        //      iteration so the next iteration runs against the
        //      new listener.
        //
        //   2. Publisher session swap (inner while loop) — fires
        //      when the underlying SubscribeHandle.objects flow
        //      completes mid-stream because the *publisher*
        //      cycled. The moq-lite session layer detects publisher
        //      disconnect via the announce stream's Ended event
        //      and closes the underlying frames channel; that
        //      naturally ends `handle.objects.collect` here. We
        //      then loop into a fresh subscribe — moq-lite supports
        //      subscribe-before-announce, so the new subscribe
        //      attaches cleanly to whichever publisher serves the
        //      suffix next, including one that comes up AFTER us.
        //
        // Without the inner loop, a remote speaker's JWT refresh
        // (every 9 min on the speaker side via
        // [connectReconnectingNestsSpeaker]) would silently kill
        // every listener's audio — the listener's own JWT refresh
        // fires on a different cadence and can't be relied on to
        // coincide.
        //
        // Bounded by:
        //   - listener swap → outer collectLatest cancels us
        //   - unsubscribeAction → pumpJob.cancel()
        //   - opener-throws → break + wait for next swap
        val pumpJob =
            scope.launch {
                activeListener.collectLatest { listener ->
                    if (listener == null) return@collectLatest
                    val terminalOrConnected =
                        listener.state.first { state ->
                            state is NestsListenerState.Connected ||
                                state is NestsListenerState.Closed ||
                                state is NestsListenerState.Failed
                        }
                    if (terminalOrConnected !is NestsListenerState.Connected) return@collectLatest

                    // Backoff for the "opener threw" retry path — exponential
                    // 250 → 500 → 1 000 ms with reset on success. The previous
                    // shape used a flat 1 000 ms which, combined with the
                    // 64-frame (~1.3 s) wrapper buffer, just barely hid the
                    // first-retry gap; a quick retry usually succeeds because
                    // moq-rs propagates announces in < 200 ms.
                    var subscribeRetryDelayMs = SUBSCRIBE_RETRY_BACKOFF_INITIAL_MS

                    while (currentCoroutineContext().isActive) {
                        val handle =
                            try {
                                opener(listener)
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                // Don't `break` on cancel — let it propagate
                                // so the launched pumpJob actually dies on
                                // unsubscribe / scope cancellation. The old
                                // `runCatching` shape ate the cancel and ran
                                // one extra iteration before the next
                                // suspend re-checked active state.
                                throw ce
                            } catch (t: Throwable) {
                                // The opener can throw for a transient
                                // reason — most commonly: the listener
                                // joined the room before the speaker started
                                // publishing, the announce-watch hasn't yet
                                // surfaced an Active for this broadcast, and
                                // the relay FINs the SUBSCRIBE bidi without
                                // a SubscribeOk/SubscribeDrop reply. The
                                // earlier shape returned `null` and `break`'d
                                // out of the inner re-issue loop, which made
                                // a single pre-publisher subscribe permanent
                                // — the audio never recovered when the
                                // speaker did come up. Retry with the same
                                // backoff used between publisher cycles.
                                Log.w("NestRx") {
                                    "ReconnectingHandle.opener threw ${t::class.simpleName}: ${t.message} " +
                                        "— retrying after ${subscribeRetryDelayMs}ms"
                                }
                                null
                            }
                        if (handle == null) {
                            delay(subscribeRetryDelayMs)
                            subscribeRetryDelayMs =
                                (subscribeRetryDelayMs * 2)
                                    .coerceAtMost(SUBSCRIBE_RETRY_BACKOFF_MAX_MS)
                            continue
                        }
                        // Success: reset the backoff so the next publisher-
                        // cycle gap starts at the floor again (rather than
                        // inheriting the last failure window's saturation).
                        subscribeRetryDelayMs = SUBSCRIBE_RETRY_BACKOFF_INITIAL_MS
                        liveHandleRef.set(handle)
                        Log.d("NestRx") { "wrapper: handle attached id=${handle.subscribeId}, starting collect" }
                        var emitted = 0L
                        try {
                            handle.objects.collect {
                                emitted += 1
                                frames.emit(it)
                            }
                        } finally {
                            if (liveHandleRef.get() === handle) liveHandleRef.set(null)
                        }
                        Log.w("NestRx") { "wrapper: handle objects flow ENDED id=${handle.subscribeId} emitted=$emitted — re-issuing after ${RESUBSCRIBE_BACKOFF_MS}ms" }
                        // Brief backoff so a permanently-gone
                        // publisher doesn't tight-loop the relay
                        // with re-subscribes. 100 ms stays well
                        // under the SUBSCRIBE_BUFFER's 1.3 s of
                        // audio headroom.
                        delay(RESUBSCRIBE_BACKOFF_MS)
                    }
                }
            }

        return SubscribeHandle(
            subscribeId = -1L,
            trackAlias = -1L,
            ok = SYNTH_OK,
            objects = frames.asSharedFlow(),
            unsubscribeAction = {
                val live = liveHandleRef.getAndSet(null)
                pumpJob.cancel()
                live?.let { runCatching { it.unsubscribe() } }
            },
        )
    }

    /**
     * Force-close the active inner listener so the orchestrator's
     * `terminalAwait` returns Closed and the next loop iteration opens
     * a fresh session. Used by the platform layer on a network change
     * (Wi-Fi ↔ cellular) — without this, the wrapper would have to
     * wait for the QUIC PTO to fire on the now-dead old socket
     * (~30 s of silence) before noticing.
     *
     * The SubscribeHandle re-issuance pump cuts existing subs over to
     * the new session as soon as it's Connected — same path the
     * JWT-refresh recycle uses — so the consumer-facing
     * [SubscribeHandle.objects] flow keeps emitting once the new
     * session lands.
     */
    override suspend fun recycleSession() {
        val current = activeListener.value ?: return
        // Best-effort. If the close races a concurrent reconnect path
        // (extremely rare — we only call this on deliberate user /
        // platform signals), the orchestrator absorbs both via its
        // existing terminal-state handling.
        runCatching { current.close() }
    }

    override suspend fun close() {
        orchestrator.cancel()
        runCatching { activeListener.value?.close() }
        if (mutableState.value !is NestsListenerState.Closed) {
            mutableState.value = NestsListenerState.Closed
        }
    }

    companion object {
        // Buffer enough Opus frames to ride out a brief downstream
        // stall during reconnect. Frames are ~20 ms each, so 64 ≈ 1.3 s
        // of audio — long enough for a typical re-handshake without
        // dropping speech, short enough that a slow consumer doesn't
        // grow the queue unbounded.
        private const val SUBSCRIBE_BUFFER = 64

        // Inner-pump backoff between publisher-cycle re-subscribes.
        // Short enough to stay well under the SUBSCRIBE_BUFFER's
        // ~1.3 s of audio headroom; long enough that a permanently-
        // gone publisher doesn't spin the relay with re-subscribes.
        private const val RESUBSCRIBE_BACKOFF_MS = 100L

        // Exponential backoff for the opener-throws retry path.
        // Typical case: subscribe-before-announce arrives at the relay
        // before the publisher exists, and the relay FINs the bidi
        // without a SubscribeOk/Drop reply.
        //
        // - INITIAL = 100 ms: lower than the prior 250 ms because
        //   join-time logs (`claude/fix-nests-audio-receiver-HCgOY`)
        //   showed the listener was paying ~4 s of dead air on every
        //   first-join while the broadcaster's session warmed up
        //   on the relay (5 retries: 250+500+1000+1000+1000 = 3.75 s).
        //   100 ms initial halves that to ~1.9 s
        //   (100+200+400+800+1000) without thrashing the relay on a
        //   permanently-gone publisher (the MAX cap below still
        //   bounds the total per-attempt rate).
        // - Doubles each miss → 200 ms → 400 ms → 800 ms → 1 000 ms.
        // - MAX = 1 000 ms: long enough that a never-arriving
        //   publisher doesn't hammer the relay; short enough to stay
        //   under the wrapper SharedFlow's ~1.3 s buffer once playback
        //   is rolling on the next attempt.
        // - Reset on first successful subscribe so a subsequent
        //   publisher-cycle gap doesn't inherit the previous saturation.
        private const val SUBSCRIBE_RETRY_BACKOFF_INITIAL_MS = 100L
        private const val SUBSCRIBE_RETRY_BACKOFF_MAX_MS = 1_000L

        private val SYNTH_OK =
            SubscribeOk(
                subscribeId = -1L,
                expiresMs = 0L,
                groupOrder = 0x01,
                contentExists = false,
            )
    }
}
