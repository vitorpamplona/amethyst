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
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteSubscribeException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * Moq-lite-backed [NestsListener]. Wraps a connected [MoqLiteSession]
 * and exposes the same listener API the IETF [DefaultNestsListener]
 * does, so [connectNestsListener] can swap the framing layer without
 * changing the public surface that [com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel]
 * and downstream UI consume.
 *
 * Subscription mapping per the audio-rooms NIP draft + nests JS
 * reference (`@moq/watch/broadcast-Djx16jPC.js:6237`,
 * `@moq/publish/screen-B680RFft.js:5641`):
 *   - subscribeSpeaker(pubkey) → MoqLiteSession.subscribe(
 *         broadcast = pubkey, track = "audio/data")
 *   - subscribeCatalog(pubkey) → MoqLiteSession.subscribe(
 *         broadcast = pubkey, track = "catalog.json"). The catalog
 *     publishes one JSON object per group describing the broadcast
 *     (codec, sample rate, optional speaker-side hints). Not
 *     required for audio-only listening — the API exists so a
 *     consumer can show codec / live indicators when wanted.
 *
 * Frame adaptation: each [com.vitorpamplona.nestsclient.moq.lite.MoqLiteFrame]
 * is wrapped in a [MoqObject] so existing decoder / player code keeps
 * working unchanged. moq-lite frames carry no per-frame envelope, so
 * [MoqObject.objectId] is synthesised as a per-subscription monotonic
 * counter and [MoqObject.publisherPriority] uses the IETF default.
 */
class MoqLiteNestsListener internal constructor(
    private val session: MoqLiteSession,
    private val mutableState: MutableStateFlow<NestsListenerState>,
) : NestsListener {
    override val state: StateFlow<NestsListenerState> = mutableState.asStateFlow()

    override suspend fun subscribeSpeaker(speakerPubkeyHex: String): SubscribeHandle = wrapSubscription(broadcast = speakerPubkeyHex, track = AUDIO_TRACK)

    override suspend fun subscribeCatalog(speakerPubkeyHex: String): SubscribeHandle = wrapSubscription(broadcast = speakerPubkeyHex, track = CATALOG_TRACK)

    private suspend fun wrapSubscription(
        broadcast: String,
        track: String,
    ): SubscribeHandle {
        check(state.value is NestsListenerState.Connected) {
            "NestsListener.subscribe requires Connected state, was ${state.value}"
        }
        val handle =
            try {
                session.subscribe(broadcast = broadcast, track = track)
            } catch (e: MoqLiteSubscribeException) {
                // The IETF SubscribeHandle path conventionally surfaces
                // protocol-level rejections through the same exception
                // shape MoqProtocolException uses, so wrapping here
                // avoids leaking moq-lite types into UI consumers.
                throw com.vitorpamplona.nestsclient.moq.MoqProtocolException(
                    "moq-lite subscribe rejected: ${e.message}",
                    e,
                )
            }

        val objectIdSeq = AtomicLong(0L)
        val mapped =
            handle.frames.map { frame ->
                MoqObject(
                    trackAlias = handle.id,
                    groupId = frame.groupSequence,
                    objectId = objectIdSeq.getAndIncrement(),
                    publisherPriority = MoqLiteSession.DEFAULT_PRIORITY,
                    payload = frame.payload,
                )
            }

        return SubscribeHandle(
            subscribeId = handle.id,
            trackAlias = handle.id, // moq-lite has no separate alias; reuse id
            ok = synthesizedOk(handle.ok),
            objects = mapped,
            unsubscribeAction = { handle.unsubscribe() },
        )
    }

    override suspend fun close() {
        if (state.value is NestsListenerState.Closed) return
        runCatching { session.close() }
        mutableState.value = NestsListenerState.Closed
    }

    private fun synthesizedOk(ok: com.vitorpamplona.nestsclient.moq.lite.MoqLiteSubscribeOk): SubscribeOk =
        SubscribeOk(
            subscribeId = 0L, // not used by downstream consumers
            expiresMs = ok.maxLatencyMillis,
            groupOrder = 0x01,
            // moq-lite has no "content exists" signal — assume no history.
            contentExists = false,
            largestGroupId = null,
            largestObjectId = null,
        )

    companion object {
        /**
         * Track name nests publishers use for the Opus audio stream.
         * Source: `@moq/publish/screen-B680RFft.js:5641`
         * (`static TRACK = "audio/data"`).
         */
        const val AUDIO_TRACK: String = "audio/data"

        /**
         * Catalog track name moq-lite watchers subscribe to first to
         * receive a JSON description of the broadcast. Used by
         * [subscribeCatalog].
         */
        const val CATALOG_TRACK: String = "catalog.json"
    }
}
