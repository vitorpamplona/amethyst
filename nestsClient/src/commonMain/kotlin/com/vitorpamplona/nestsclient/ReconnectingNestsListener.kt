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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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
                    runCatching { openOnce() }.getOrElse {
                        state.value = NestsListenerState.Failed("connect failed: ${it.message}", it)
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
                        runCatching { listener.close() }
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
                val terminal = state.value
                if (terminal is NestsListenerState.Closed) break
                if (policy.isExhausted(attempt + 1)) break
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

    override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle = reissuingSubscribe { listener -> listener.subscribeSpeaker(speakerPubkeyHex) }

    override suspend fun subscribeCatalog(speakerPubkeyHex: String): SubscribeHandle = reissuingSubscribe { listener -> listener.subscribeCatalog(speakerPubkeyHex) }

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

        val frames = MutableSharedFlow<MoqObject>(extraBufferCapacity = SUBSCRIBE_BUFFER)
        val liveHandleRef = AtomicReference<SubscribeHandle?>(null)

        // Re-subscribe pump: every time activeListener changes, drop
        // the prior subscription (collectLatest cancels the inner
        // body) and open a new one against the fresh session.
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
                    val handle =
                        runCatching { opener(listener) }
                            .getOrNull() ?: return@collectLatest
                    liveHandleRef.set(handle)
                    try {
                        handle.objects.collect { frames.emit(it) }
                    } finally {
                        if (liveHandleRef.get() === handle) liveHandleRef.set(null)
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

        private val SYNTH_OK =
            SubscribeOk(
                subscribeId = -1L,
                expiresMs = 0L,
                groupOrder = 0x01,
                contentExists = false,
            )
    }
}
