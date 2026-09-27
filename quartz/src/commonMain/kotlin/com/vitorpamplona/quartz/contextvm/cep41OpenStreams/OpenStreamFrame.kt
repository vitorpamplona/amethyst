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
package com.vitorpamplona.quartz.contextvm.cep41OpenStreams

import com.vitorpamplona.quartz.contextvm.transfer.ProgressEnvelope
import com.vitorpamplona.quartz.contextvm.transfer.ProgressEnvelope.Companion.asLongOrNull
import com.vitorpamplona.quartz.contextvm.transfer.ProgressEnvelope.Companion.asStringOrNull
import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.contextvm.transfer.TransferFrameException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A CEP-41 open-ended stream frame.
 *
 * The profile carries **two** independent ordering fields and conflating them is
 * the classic implementation bug:
 *
 *  - `progress` (on the envelope) orders *all* frames, control frames included,
 *    and is explicitly not a chunk counter. Each peer numbers its own outbound
 *    frames from 1, so a value may only be compared against frames from the same
 *    sender.
 *  - [Chunk.chunkIndex] starts at 0, increases contiguously, and is what
 *    validates payload completeness.
 */
sealed interface OpenStreamFrame {
    val envelope: ProgressEnvelope
    val token: ProgressToken get() = envelope.token
    val progress: Double get() = envelope.progress

    data class Start(
        override val envelope: ProgressEnvelope,
    ) : OpenStreamFrame

    data class Accept(
        override val envelope: ProgressEnvelope,
    ) : OpenStreamFrame

    data class Chunk(
        override val envelope: ProgressEnvelope,
        val data: String,
        val chunkIndex: Long,
    ) : OpenStreamFrame

    data class Ping(
        override val envelope: ProgressEnvelope,
        val nonce: String,
    ) : OpenStreamFrame

    data class Pong(
        override val envelope: ProgressEnvelope,
        val nonce: String,
    ) : OpenStreamFrame

    /**
     * Successful closure.
     *
     * [lastChunkIndex] is present only when the sender is declaring a
     * completeness bound; a live, open-ended feed omits it, and a stream that
     * carried no chunks MUST omit it.
     */
    data class Close(
        override val envelope: ProgressEnvelope,
        val lastChunkIndex: Long? = null,
    ) : OpenStreamFrame

    data class Abort(
        override val envelope: ProgressEnvelope,
        val reason: String? = null,
    ) : OpenStreamFrame

    companion object {
        const val START = "start"
        const val ACCEPT = "accept"
        const val CHUNK = "chunk"
        const val PING = "ping"
        const val PONG = "pong"
        const val CLOSE = "close"
        const val ABORT = "abort"

        const val DATA = "data"
        const val CHUNK_INDEX = "chunkIndex"
        const val NONCE = "nonce"
        const val LAST_CHUNK_INDEX = "lastChunkIndex"
        const val REASON = "reason"

        /** Receivers SHOULD enforce this ceiling and MAY reject oversized nonces. */
        const val MAX_NONCE_BYTES = 64

        /** Parses a CEP-41 frame, or null when the envelope is a different profile. */
        fun parseOrNull(envelope: ProgressEnvelope): OpenStreamFrame? {
            if (envelope.type != ProgressEnvelope.TYPE_OPEN_STREAM) return null
            val cvm = envelope.cvm

            return when (val frameType = envelope.frameType) {
                START -> Start(envelope)
                ACCEPT -> Accept(envelope)

                CHUNK ->
                    Chunk(
                        envelope = envelope,
                        data =
                            cvm[DATA]?.asStringOrNull()
                                ?: throw TransferFrameException("chunk requires string data"),
                        chunkIndex =
                            cvm[CHUNK_INDEX]?.asLongOrNull()
                                ?: throw TransferFrameException("chunk requires chunkIndex"),
                    )

                PING -> Ping(envelope, requireNonce(cvm[NONCE]?.asStringOrNull(), PING))
                PONG -> Pong(envelope, requireNonce(cvm[NONCE]?.asStringOrNull(), PONG))

                CLOSE -> Close(envelope, cvm[LAST_CHUNK_INDEX]?.asLongOrNull())

                ABORT -> Abort(envelope, cvm[REASON]?.asStringOrNull())

                else -> throw TransferFrameException("unknown open-stream frameType: $frameType")
            }
        }

        private fun requireNonce(
            nonce: String?,
            frameType: String,
        ): String {
            if (nonce == null) throw TransferFrameException("$frameType requires a nonce")
            if (nonce.encodeToByteArray().size > MAX_NONCE_BYTES) {
                throw TransferFrameException("$frameType nonce exceeds $MAX_NONCE_BYTES bytes")
            }
            return nonce
        }

        fun start(
            token: ProgressToken,
            progress: Double,
        ) = Start(envelopeOf(START, token, progress))

        fun accept(
            token: ProgressToken,
            progress: Double,
        ) = Accept(envelopeOf(ACCEPT, token, progress))

        fun chunk(
            token: ProgressToken,
            progress: Double,
            chunkIndex: Long,
            data: String,
        ) = Chunk(
            envelope =
                envelopeOf(CHUNK, token, progress) {
                    put(CHUNK_INDEX, JsonPrimitive(chunkIndex))
                    put(DATA, JsonPrimitive(data))
                },
            data = data,
            chunkIndex = chunkIndex,
        )

        fun ping(
            token: ProgressToken,
            progress: Double,
            nonce: String,
        ) = Ping(
            envelope = envelopeOf(PING, token, progress) { put(NONCE, JsonPrimitive(nonce)) },
            nonce = nonce,
        )

        fun pong(
            token: ProgressToken,
            progress: Double,
            nonce: String,
        ) = Pong(
            envelope = envelopeOf(PONG, token, progress) { put(NONCE, JsonPrimitive(nonce)) },
            nonce = nonce,
        )

        fun close(
            token: ProgressToken,
            progress: Double,
            lastChunkIndex: Long? = null,
        ) = Close(
            envelope =
                envelopeOf(CLOSE, token, progress) {
                    lastChunkIndex?.let { put(LAST_CHUNK_INDEX, JsonPrimitive(it)) }
                },
            lastChunkIndex = lastChunkIndex,
        )

        fun abort(
            token: ProgressToken,
            progress: Double,
            reason: String? = null,
        ) = Abort(
            envelope =
                envelopeOf(ABORT, token, progress) {
                    reason?.let { put(REASON, JsonPrimitive(it)) }
                },
            reason = reason,
        )

        private fun envelopeOf(
            frameType: String,
            token: ProgressToken,
            progress: Double,
            body: JsonObjectBuilder.() -> Unit = {},
        ) = ProgressEnvelope(
            token = token,
            progress = progress,
            cvm =
                buildJsonObject {
                    put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OPEN_STREAM))
                    put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive(frameType))
                    body()
                },
        )
    }
}
