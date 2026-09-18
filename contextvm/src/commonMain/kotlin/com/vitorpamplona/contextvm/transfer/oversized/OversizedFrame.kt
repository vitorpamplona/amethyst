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
package com.vitorpamplona.contextvm.transfer.oversized

import com.vitorpamplona.contextvm.transfer.ProgressEnvelope
import com.vitorpamplona.contextvm.transfer.ProgressEnvelope.Companion.asLongOrNull
import com.vitorpamplona.contextvm.transfer.ProgressEnvelope.Companion.asStringOrNull
import com.vitorpamplona.contextvm.transfer.ProgressToken
import com.vitorpamplona.contextvm.transfer.TransferFrameException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A CEP-22 bounded oversized-transfer frame.
 *
 * The payload is reassembled from the `data` fragments of [Chunk] frames, which
 * are **raw substrings of the serialized JSON-RPC message**, not base64. That is
 * what makes the digest rule work: concatenate the fragments, encode the result
 * as UTF-8, and hash those bytes.
 */
sealed interface OversizedFrame {
    val envelope: ProgressEnvelope
    val token: ProgressToken get() = envelope.token
    val progress: Double get() = envelope.progress

    data class Start(
        override val envelope: ProgressEnvelope,
        val completionMode: String,
        val digest: String,
        val totalBytes: Long,
        val totalChunks: Long,
    ) : OversizedFrame

    data class Accept(
        override val envelope: ProgressEnvelope,
    ) : OversizedFrame

    data class Chunk(
        override val envelope: ProgressEnvelope,
        val data: String,
    ) : OversizedFrame

    data class End(
        override val envelope: ProgressEnvelope,
    ) : OversizedFrame

    data class Abort(
        override val envelope: ProgressEnvelope,
        val reason: String? = null,
    ) : OversizedFrame

    companion object {
        const val START = "start"
        const val ACCEPT = "accept"
        const val CHUNK = "chunk"
        const val END = "end"
        const val ABORT = "abort"

        /** The only completion mode this CEP version defines. */
        const val COMPLETION_MODE_RENDER = "render"

        /** Digest values are algorithm-prefixed on the wire, e.g. `sha256:ab12…`. */
        const val DIGEST_PREFIX_SHA256 = "sha256:"

        const val COMPLETION_MODE = "completionMode"
        const val DIGEST = "digest"
        const val TOTAL_BYTES = "totalBytes"
        const val TOTAL_CHUNKS = "totalChunks"
        const val DATA = "data"
        const val REASON = "reason"

        /**
         * Parses a CEP-22 frame, or returns null when the envelope belongs to a
         * different transfer profile (CEP-41 shares this envelope).
         */
        fun parseOrNull(envelope: ProgressEnvelope): OversizedFrame? {
            if (envelope.type != ProgressEnvelope.TYPE_OVERSIZED) return null
            val cvm = envelope.cvm

            return when (val frameType = envelope.frameType) {
                START -> {
                    val completionMode =
                        cvm[COMPLETION_MODE]?.asStringOrNull()
                            ?: throw TransferFrameException("start requires completionMode")
                    // Receivers MUST reject unknown or unsupported completion
                    // modes; the field exists as an extension point for future
                    // CEPs, so silently treating anything else as render would
                    // be the wrong kind of tolerance.
                    if (completionMode != COMPLETION_MODE_RENDER) {
                        throw TransferFrameException("unsupported completionMode: $completionMode")
                    }
                    OversizedFrame.Start(
                        envelope = envelope,
                        completionMode = completionMode,
                        digest =
                            cvm[DIGEST]?.asStringOrNull()
                                ?: throw TransferFrameException("start requires digest"),
                        totalBytes =
                            cvm[TOTAL_BYTES]?.asLongOrNull()
                                ?: throw TransferFrameException("start requires totalBytes"),
                        totalChunks =
                            cvm[TOTAL_CHUNKS]?.asLongOrNull()
                                ?: throw TransferFrameException("start requires totalChunks"),
                    )
                }

                ACCEPT -> OversizedFrame.Accept(envelope)

                CHUNK ->
                    OversizedFrame.Chunk(
                        envelope = envelope,
                        data =
                            cvm[DATA]?.asStringOrNull()
                                ?: throw TransferFrameException("chunk requires string data"),
                    )

                END -> OversizedFrame.End(envelope)

                ABORT -> OversizedFrame.Abort(envelope, cvm[REASON]?.asStringOrNull())

                else -> throw TransferFrameException("unknown oversized frameType: $frameType")
            }
        }

        fun start(
            token: ProgressToken,
            progress: Double,
            digest: String,
            totalBytes: Long,
            totalChunks: Long,
            message: String? = null,
        ) = OversizedFrame.Start(
            envelope =
                envelopeOf(START, token, progress, message) {
                    put(COMPLETION_MODE, JsonPrimitive(COMPLETION_MODE_RENDER))
                    put(DIGEST, JsonPrimitive(digest))
                    put(TOTAL_BYTES, JsonPrimitive(totalBytes))
                    put(TOTAL_CHUNKS, JsonPrimitive(totalChunks))
                },
            completionMode = COMPLETION_MODE_RENDER,
            digest = digest,
            totalBytes = totalBytes,
            totalChunks = totalChunks,
        )

        fun accept(
            token: ProgressToken,
            progress: Double,
        ) = OversizedFrame.Accept(envelopeOf(ACCEPT, token, progress))

        fun chunk(
            token: ProgressToken,
            progress: Double,
            data: String,
        ) = OversizedFrame.Chunk(
            envelope = envelopeOf(CHUNK, token, progress) { put(DATA, JsonPrimitive(data)) },
            data = data,
        )

        fun end(
            token: ProgressToken,
            progress: Double,
            message: String? = null,
        ) = OversizedFrame.End(envelopeOf(END, token, progress, message))

        fun abort(
            token: ProgressToken,
            progress: Double,
            reason: String? = null,
        ) = OversizedFrame.Abort(
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
            message: String? = null,
            body: JsonObjectBuilder.() -> Unit = {},
        ) = ProgressEnvelope(
            token = token,
            progress = progress,
            message = message,
            cvm =
                buildJsonObject {
                    put(ProgressEnvelope.TYPE, JsonPrimitive(ProgressEnvelope.TYPE_OVERSIZED))
                    put(ProgressEnvelope.FRAME_TYPE, JsonPrimitive(frameType))
                    body()
                },
        )
    }
}
