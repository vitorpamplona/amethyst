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

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * `connectNestsSpeaker` plus a transport-loss reconnect loop with
 * exponential backoff and proactive JWT refresh. Mirror of
 * [connectReconnectingNestsListener] for the publish side.
 *
 * The returned [NestsSpeaker]'s state surfaces the underlying speaker's
 * state directly while a session is alive, but flips to
 * [NestsSpeakerState.Reconnecting] between attempts. The speaker is
 * auto-redirected to the freshly-opened session under the hood —
 * `startBroadcasting()` returns a stable [BroadcastHandle] whose
 * `setMuted` and `close` survive every refresh.
 *
 * **Why this exists** — moq-auth issues 600 s bearer tokens
 * (`moq-auth/src/index.ts`). Without proactive refresh, any room a
 * user keeps the stage in for >10 min hits an authorisation failure
 * the moment the relay tears down the session, the publish stream
 * goes silent, and the user has to manually re-tap "Talk". The
 * proactive recycle keeps the WebTransport session young so the
 * relay never sees an expired token.
 *
 * **Broadcast-handle re-issuance** — the caller-owned
 * [BroadcastHandle] survives a refresh / reconnect. Internally the
 * wrapper opens a fresh underlying `BroadcastHandle` against each
 * new session, replays the user's mute intent on it, and forwards
 * `setMuted` calls to whichever live handle exists at the time.
 * `close` cancels the re-issue pump and best-effort closes the
 * latest live handle.
 *
 * **Audio gap during refresh** — the wrapper closes the current
 * underlying speaker (which stops the mic capture + Opus encoder +
 * publisher) before opening the next, so the listener side will
 * hear ~50–150 ms of silence at each recycle boundary. That's the
 * trade-off we pay for a clean session swap; the alternative
 * (carrying the mic capture across sessions) would require deeper
 * plumbing into the audio pipeline. Acceptable for v1 —
 * 9-min-spaced 150 ms gaps are well below the noise floor of a
 * voice call.
 *
 * Cancellation: cancelling [scope] (typically the room screen's VM
 * scope) cancels the reconnect loop and closes both the active
 * session and the active broadcast. [NestsSpeaker.close] is
 * idempotent.
 */
suspend fun connectReconnectingNestsSpeaker(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    speakerPubkeyHex: String,
    captureFactory: () -> AudioCapture,
    encoderFactory: () -> OpusEncoder,
    policy: NestsReconnectPolicy = NestsReconnectPolicy(),
    /**
     * Proactive JWT refresh window. moq-auth issues bearer tokens
     * with a 600 s lifetime; once the token expires the relay
     * tears down the WebTransport session and we'd otherwise
     * recover via the regular reconnect path with a brief audible
     * dropout AND a permanent broadcast loss until the user taps
     * Talk again. By recycling the session a minute before expiry
     * we stay ahead of the relay's tear-down: the new session
     * opens, the broadcast pump reopens publishing on it (carrying
     * the user's mute intent), and the wrapper's outward state
     * never enters the user-visible Reconnecting state.
     *
     * Set to 0 or negative to disable.
     */
    tokenRefreshAfterMs: Long = 540_000L,
    /**
     * Test seam — defaults to the production [connectNestsSpeaker].
     * Tests pass a fake that returns a scripted [NestsSpeaker] so
     * the reconnect state machine can be exercised without a real
     * WebTransport stack.
     */
    connector: suspend () -> NestsSpeaker = {
        connectNestsSpeaker(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
            speakerPubkeyHex = speakerPubkeyHex,
            captureFactory = captureFactory,
            encoderFactory = encoderFactory,
        )
    },
): NestsSpeaker {
    val state = MutableStateFlow<NestsSpeakerState>(NestsSpeakerState.Idle)
    val activeSpeaker = MutableStateFlow<NestsSpeaker?>(null)

    suspend fun openOnce(): NestsSpeaker {
        val speaker = connector()
        activeSpeaker.value = speaker
        state.value = speaker.state.value
        return speaker
    }

    val orchestrator =
        scope.launch {
            var attempt = 0
            while (true) {
                val speaker =
                    try {
                        openOnce()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        // Propagate so the orchestrator dies promptly on
                        // scope cancellation; `runCatching` would have
                        // eaten the cancel and run one more iteration.
                        throw ce
                    } catch (t: Throwable) {
                        state.value = NestsSpeakerState.Failed("connect failed: ${t.message}", t)
                        null
                    }
                var refreshTriggered = false
                if (speaker != null) {
                    // Wait for either a terminal state OR the proactive
                    // JWT-refresh deadline. withTimeoutOrNull returns
                    // null when the timer fires first; we then close
                    // the (still-healthy) speaker and loop to mint a
                    // fresh JWT via openOnce(). The broadcast-pump
                    // re-issues publishing onto the new session
                    // without the wrapper's outward state ever
                    // showing Reconnecting.
                    //
                    // The `onEach { mirror } + first` pattern (rather
                    // than `state.collect { mirror; if terminal break }`)
                    // is what lets `withTimeoutOrNull` cancel cleanly
                    // mid-mirror — once cancelled, the underlying
                    // speaker's subsequent state changes (e.g. the
                    // Closed we trigger on the next line) don't leak
                    // out to the wrapper's state.
                    val terminalAwait: suspend () -> NestsSpeakerState = {
                        speaker.state
                            .onEach { state.value = it }
                            .first { s ->
                                s is NestsSpeakerState.Failed || s is NestsSpeakerState.Closed
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
                        // speaker; don't bump `attempt` (it's not a
                        // backoff event) so the next openOnce() runs
                        // immediately.
                        try {
                            speaker.close()
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            // Best-effort.
                        }
                        attempt = 0
                        refreshTriggered = true
                    } else if (terminal is NestsSpeakerState.Failed && !isUserCancelledSpeaker(terminal)) {
                        // Transport-side failure → schedule a reconnect.
                        attempt++
                        if (!policy.isExhausted(attempt)) {
                            val delayMs = policy.delayForAttempt(attempt)
                            state.value = NestsSpeakerState.Reconnecting(attempt, delayMs)
                        }
                    }
                }
                if (refreshTriggered) {
                    // Skip the reconnect-schedule path entirely — a
                    // refresh is a planned cutover, not a backoff event.
                    continue
                }
                val terminal = state.value
                if (terminal is NestsSpeakerState.Closed) break
                if (policy.isExhausted(attempt + 1)) break
                val delayMs =
                    if (terminal is NestsSpeakerState.Reconnecting) {
                        terminal.delayMs
                    } else {
                        policy.delayForAttempt(++attempt)
                    }
                state.value = NestsSpeakerState.Reconnecting(attempt.coerceAtLeast(1), delayMs)
                delay(delayMs)
            }
        }

    // Match the existing [connectNestsSpeaker] semantics: suspend
    // until the first session is up (or hard-fails) so the VM's
    // call site `val s = speakerConnector.connect(...); s.startBroadcasting()`
    // keeps working without changes. This is a deliberate departure
    // from the listener wrapper, which returns immediately and
    // expects the VM to gate `subscribeSpeaker` on the Connected
    // state — the speaker side has a tighter `startBroadcasting`
    // contract that requires a live session at call time.
    val firstReady =
        state.first { s ->
            s is NestsSpeakerState.Connected ||
                s is NestsSpeakerState.Broadcasting ||
                s is NestsSpeakerState.Failed
        }
    if (firstReady is NestsSpeakerState.Failed) {
        // Unwind: cancel the orchestrator + close any speaker that
        // managed to open before the failure surfaced.
        orchestrator.cancel()
        runCatching { activeSpeaker.value?.close() }
        throw NestsException(firstReady.reason, firstReady.cause)
    }

    return ReconnectingSpeakerHandle(state, activeSpeaker, orchestrator, scope)
}

private fun isUserCancelledSpeaker(state: NestsSpeakerState.Failed): Boolean {
    val msg = state.reason
    // Forward-compat seam — same shape as [isUserCancelled] on the
    // listener side. User-driven close goes through Closed today;
    // anything else surfaced as Failed is a transport / handshake
    // error worth retrying.
    return msg.contains("user cancelled", ignoreCase = true)
}

private class ReconnectingSpeakerHandle(
    private val mutableState: MutableStateFlow<NestsSpeakerState>,
    private val activeSpeaker: MutableStateFlow<NestsSpeaker?>,
    private val orchestrator: Job,
    private val scope: CoroutineScope,
) : NestsSpeaker {
    override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

    private val gate = Mutex()

    @Volatile private var activeBroadcast: ReissuingBroadcastHandle? = null

    override suspend fun startBroadcasting(): BroadcastHandle =
        gate.withLock {
            check(state.value !is NestsSpeakerState.Closed) {
                "startBroadcasting on a closed speaker"
            }
            check(activeBroadcast == null) {
                "speaker is already broadcasting"
            }
            // Require a live (or just-connected) session — matches
            // the listener wrapper's `subscribeSpeaker` contract.
            // The wrapper's own `connect()` already suspended until
            // the first session was up, so this check almost never
            // fails in practice; it guards the second-call-after-
            // close case.
            activeSpeaker.value
                ?: error("no live session — wait for state == Connected before startBroadcasting")

            val handle =
                ReissuingBroadcastHandle(activeSpeaker, scope) { closed ->
                    if (activeBroadcast === closed) activeBroadcast = null
                }
            handle.start()
            activeBroadcast = handle
            handle
        }

    override suspend fun close() {
        orchestrator.cancel()
        runCatching { activeBroadcast?.close() }
        runCatching { activeSpeaker.value?.close() }
        if (mutableState.value !is NestsSpeakerState.Closed) {
            mutableState.value = NestsSpeakerState.Closed
        }
    }
}

/**
 * Stable [BroadcastHandle] backed by a re-issuing pump. Each time
 * the wrapper opens a fresh session the pump cancels its prior
 * iteration, calls [NestsSpeaker.startBroadcasting] on the new
 * session, replays the cached mute intent on the resulting
 * underlying handle, and parks until the next session swap.
 *
 * `setMuted` updates the cached intent unconditionally and forwards
 * to whichever live underlying handle exists at the time. If no
 * underlying handle is up (e.g. a brief gap during recycle), the
 * intent is replayed on the next handle the pump opens, so the
 * user-observed mute state is monotonic across recycles.
 */
private class ReissuingBroadcastHandle(
    private val activeSpeaker: StateFlow<NestsSpeaker?>,
    private val scope: CoroutineScope,
    private val onClose: (ReissuingBroadcastHandle) -> Unit,
) : BroadcastHandle {
    @Volatile private var desiredMuted: Boolean = false

    @Volatile private var closed: Boolean = false
    private val liveHandle = AtomicReference<BroadcastHandle?>(null)
    private var pumpJob: Job? = null

    override val isMuted: Boolean get() = desiredMuted

    fun start() {
        // Re-broadcast pump: every time activeSpeaker changes, drop
        // the prior broadcast (collectLatest cancels the inner
        // body via awaitCancellation) and open a new one against
        // the fresh session. The pattern mirrors the listener's
        // SubscribeHandle re-issuance pump.
        pumpJob =
            scope.launch {
                activeSpeaker.collectLatest { sp ->
                    if (sp == null || closed) return@collectLatest
                    // Wait until the underlying speaker is ready to
                    // broadcast (or has gone terminal). For a fresh
                    // session this resolves immediately because the
                    // wrapper's openOnce already saw Connected.
                    val ready =
                        sp.state.first { st ->
                            st is NestsSpeakerState.Connected ||
                                st is NestsSpeakerState.Broadcasting ||
                                st is NestsSpeakerState.Closed ||
                                st is NestsSpeakerState.Failed
                        }
                    if (ready !is NestsSpeakerState.Connected && ready !is NestsSpeakerState.Broadcasting) {
                        return@collectLatest
                    }
                    if (closed) return@collectLatest
                    val handle =
                        try {
                            sp.startBroadcasting()
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            // Don't `return@collectLatest` on cancel —
                            // propagate so the launched pumpJob actually
                            // dies on close/scope cancellation. The old
                            // `runCatching` shape ate the cancel.
                            throw ce
                        } catch (_: Throwable) {
                            null
                        } ?: return@collectLatest
                    if (closed) {
                        runCatching { handle.close() }
                        return@collectLatest
                    }
                    // Apply current mute intent BEFORE storing the
                    // handle so a setMuted that races us applies
                    // exactly once: either (a) we set intent →
                    // apply intent → store, and the racing setMuted
                    // sees the live handle and applies again (no-op
                    // on the broadcaster); or (b) the racing
                    // setMuted updates intent → we read intent →
                    // apply. Order doesn't matter; idempotent.
                    if (desiredMuted) {
                        runCatching { handle.setMuted(true) }
                    }
                    liveHandle.set(handle)
                    try {
                        // Park until activeSpeaker emits a new value
                        // (collectLatest cancels us) or close() runs
                        // (pumpJob.cancel).
                        awaitCancellation()
                    } finally {
                        // Clear our slot only if we still own it —
                        // close() may have already swapped in null.
                        if (liveHandle.get() === handle) liveHandle.set(null)
                        // Best-effort close on the way out: the user
                        // may have called wrapper.close (closed=true,
                        // pump cancelling), or activeSpeaker swapped
                        // (the prior speaker is about to be closed
                        // by the orchestrator anyway, but defensively
                        // closing here releases the broadcaster +
                        // publisher promptly rather than waiting for
                        // the speaker.close()).
                        runCatching { handle.close() }
                    }
                }
            }
    }

    override suspend fun setMuted(muted: Boolean) {
        if (closed) return
        desiredMuted = muted
        liveHandle.get()?.let { runCatching { it.setMuted(muted) } }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        pumpJob?.cancel()
        liveHandle.getAndSet(null)?.let { runCatching { it.close() } }
        onClose(this)
    }
}
