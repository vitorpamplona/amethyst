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
import com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Moq-lite-backed [NestsSpeaker]. Mirrors [MoqLiteNestsListener] on the
 * publish side: takes a connected [MoqLiteSession] and exposes the
 * existing [NestsSpeaker] API so [connectNestsSpeaker] can swap the
 * framing layer without changing any downstream consumers.
 *
 * Wire-flow per [MoqLiteSession.publish]:
 *   - the session opens a publisher state when [startBroadcasting] is
 *     called, then services every relay-opened Announce / Subscribe
 *     bidi automatically.
 *   - frames pushed via [MoqLitePublisherHandle.send] go on a fresh
 *     uni stream per group, framed as `varint(size) + payload`.
 */
class MoqLiteNestsSpeaker internal constructor(
    private val session: MoqLiteSession,
    private val speakerPubkeyHex: String,
    private val captureFactory: () -> AudioCapture,
    private val encoderFactory: () -> OpusEncoder,
    private val scope: CoroutineScope,
    private val mutableState: MutableStateFlow<NestsSpeakerState>,
    /**
     * How many Opus frames to pack into one moq-lite group / QUIC uni
     * stream. Forwarded to [NestMoqLiteBroadcaster.framesPerGroup] —
     * see that field's kdoc for the production stream-cliff rationale.
     * Defaults to [NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP].
     */
    private val framesPerGroup: Int = NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP,
) : NestsSpeaker {
    override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

    private val gate = Mutex()
    private var activeHandle: MoqLiteBroadcastHandle? = null

    override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle {
        gate.withLock {
            val current = state.value
            check(current is NestsSpeakerState.Connected) {
                "startBroadcasting requires Connected state, was $current"
            }
            check(activeHandle == null) { "speaker is already broadcasting" }

            // Per the audio-rooms NIP draft + JS reference
            // (`@moq/publish/screen-B680RFft.js:5641`), publishers
            // claim a broadcast suffix equal to their pubkey hex and
            // the audio data sits on track "audio/data". The publisher
            // must be track-scoped because listeners typically open
            // BOTH a catalog subscribe AND an audio subscribe per
            // speaker; without the track filter the publisher would
            // route Opus frames onto whichever subscribe arrived first
            // (in practice the catalog one) and the audio subscription
            // would silently starve.
            val publisher =
                session.publish(
                    broadcastSuffix = speakerPubkeyHex,
                    track = MoqLiteNestsListener.AUDIO_TRACK,
                )
            // From here on, the publisher is registered in the session
            // and has a live announce/subscribe path. Anything that
            // throws before we hand a working handle back to the caller
            // must close the publisher to avoid leaking it inside the
            // session's `activePublisher` slot — otherwise a subsequent
            // startBroadcasting fails with "already publishing" and the
            // publisher's announce state never tears down.
            val broadcaster =
                try {
                    NestMoqLiteBroadcaster(
                        capture = captureFactory(),
                        encoder = encoderFactory(),
                        publisher = publisher,
                        scope = scope,
                        framesPerGroup = framesPerGroup,
                    ).also {
                        it.start(
                            onTerminalFailure = {
                                // Broadcaster bailed after sustained
                                // publisher.send failures. Flip to
                                // Failed so the reconnect orchestrator
                                // sees a terminal state and recycles
                                // the session — without this signal the
                                // outward state stays on Broadcasting
                                // and the room is silently mute.
                                onBroadcastTerminalFailure()
                            },
                            onLevel = onLevel,
                        )
                    }
                } catch (t: Throwable) {
                    runCatching { publisher.close() }
                    throw t
                }
            mutableState.value =
                NestsSpeakerState.Broadcasting(
                    room = current.room,
                    negotiatedMoqVersion = current.negotiatedMoqVersion,
                    isMuted = false,
                )
            val handle =
                MoqLiteBroadcastHandle(
                    broadcaster = broadcaster,
                    publisher = publisher,
                    parent = this,
                )
            activeHandle = handle
            return handle
        }
    }

    /**
     * Compare-and-clear that runs from inside [close] (already holds
     * [gate]) and from [MoqLiteBroadcastHandle.close] (doesn't).
     * Mirrors [DefaultNestsSpeaker.broadcastClosed].
     */
    internal fun broadcastClosed(handle: MoqLiteBroadcastHandle) {
        if (activeHandle !== handle) return
        activeHandle = null
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value =
                NestsSpeakerState.Connected(current.room, current.negotiatedMoqVersion)
        }
    }

    /**
     * Called from the broadcaster's `onTerminalFailure` callback (off
     * the speaker's coroutine). Transitions the speaker to `Failed` so
     * the reconnect orchestrator (`ReconnectingNestsSpeaker`) observes
     * a terminal state and recycles the session. No-op if the speaker
     * is already in a terminal state.
     */
    private fun onBroadcastTerminalFailure() {
        val current = mutableState.value
        if (current is NestsSpeakerState.Failed || current is NestsSpeakerState.Closed) return
        mutableState.value =
            NestsSpeakerState.Failed(
                reason = "broadcast pipeline gave up — likely transport loss",
            )
    }

    internal fun reportMuteState(muted: Boolean) {
        val current = mutableState.value
        if (current is NestsSpeakerState.Broadcasting) {
            mutableState.value = current.copy(isMuted = muted)
        }
    }

    override suspend fun close() {
        // Take + clear under [gate] so a concurrent `startBroadcasting`
        // can't observe a half-closed state, then run the long-running
        // suspends (handle.close + session.close) outside the lock.
        val handle: MoqLiteBroadcastHandle?
        gate.withLock {
            if (state.value is NestsSpeakerState.Closed) return
            handle = activeHandle
            activeHandle = null
            mutableState.value = NestsSpeakerState.Closed
        }
        // Don't `runCatching { handle.close() }` — that swallows
        // CancellationException too, breaking structured cancellation
        // when the parent scope is cancelling teardown.
        if (handle != null) {
            try {
                handle.close()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Best-effort — already cleared activeHandle.
            }
        }
        try {
            session.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
    }
}

internal class MoqLiteBroadcastHandle(
    private val broadcaster: NestMoqLiteBroadcaster,
    private val publisher: MoqLitePublisherHandle,
    private val parent: MoqLiteNestsSpeaker,
) : BroadcastHandle {
    @Volatile private var muted: Boolean = false

    @Volatile private var closed: Boolean = false

    override val isMuted: Boolean get() = muted

    override suspend fun setMuted(muted: Boolean) {
        if (closed) return
        this.muted = muted
        broadcaster.setMuted(muted)
        parent.reportMuteState(muted)
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        try {
            broadcaster.stop()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Even on cancel, run the rest of cleanup before rethrowing
            // — broadcaster.stop already cancels its own job, so the
            // mic + encoder + publisher are owed their close paths.
            runCatching { publisher.close() }
            parent.broadcastClosed(this)
            throw ce
        } catch (_: Throwable) {
            // Best-effort; fall through to the defensive publisher.close.
        }
        // broadcaster.stop() already calls publisher.close(); call again
        // defensively to make this method idempotent against partial
        // failures on the broadcaster.stop path.
        try {
            publisher.close()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            parent.broadcastClosed(this)
            throw ce
        } catch (_: Throwable) {
            // Best-effort.
        }
        parent.broadcastClosed(this)
    }
}
