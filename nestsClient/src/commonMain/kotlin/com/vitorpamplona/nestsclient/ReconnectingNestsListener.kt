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

import com.vitorpamplona.nestsclient.moq.SubscribeHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * are NOT preserved across a reconnect. The listener's
 * `subscribeSpeaker` returns a handle bound to the SESSION; once
 * the session is replaced, that handle's flow stops. Callers can
 * either re-subscribe in their own
 * `state.collectLatest { if (Connected) sub() }` loop, or wait for
 * the future `MutableSharedFlow`-buffered upgrade flagged in the
 * Tier-4 plan.
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
                if (listener != null) {
                    // Forward state until the listener terminates.
                    listener.state.collect { s ->
                        state.value = s
                        if (s is NestsListenerState.Failed && !isUserCancelled(s)) {
                            // Transport-side failure → reconnect.
                            attempt++
                            if (policy.isExhausted(attempt)) return@collect
                            val delayMs = policy.delayForAttempt(attempt)
                            state.value = NestsListenerState.Reconnecting(attempt, delayMs)
                            return@collect
                        }
                        if (s is NestsListenerState.Closed) {
                            // User-driven close — exit the loop entirely.
                            return@collect
                        }
                        // Connecting / Connected / Reconnecting (transient) — continue.
                    }
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

    return ReconnectingHandle(state, activeListener, orchestrator)
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
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle {
        val live =
            activeListener.value
                ?: error("no live session — wait for state == Connected before subscribing")
        return live.subscribeSpeaker(speakerPubkeyHex)
    }

    override suspend fun close() {
        orchestrator.cancel()
        runCatching { activeListener.value?.close() }
        if (mutableState.value !is NestsListenerState.Closed) {
            mutableState.value = NestsListenerState.Closed
        }
    }
}
