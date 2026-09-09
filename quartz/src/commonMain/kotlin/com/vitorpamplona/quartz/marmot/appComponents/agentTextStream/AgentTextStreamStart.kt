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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The kind-1200 event that anchors one agent text stream, read off an inner
 * app payload's tags.
 *
 * A stream never carries its own key material: the anchoring event's id is
 * part of [AgentTextStreamKeyContextV1], so a receiver can only derive record
 * keys for a stream it has already seen announced inside the group. That is
 * also why the anchor is an ordinary in-group app payload rather than
 * something the broker hands out — the broker relays ciphertext and learns
 * nothing.
 *
 * ```
 * ["stream", <32-byte stream id, hex>]
 * ["route", "quic"]                       (optional; "quic" when absent)
 * ["broker", <candidate URL>]             (repeatable)
 * ```
 *
 * The matching END of a stream is an ordinary kind-9 chat carrying
 * [STREAM_TAG], [STREAM_HASH_TAG] and [STREAM_CHUNKS_TAG]; see
 * [AgentTextStreamFinal].
 */
class AgentTextStreamStart(
    val streamId: HexKey,
    val route: String,
    val brokerCandidates: List<String>,
) {
    val isQuicRoute: Boolean get() = route == ROUTE_QUIC

    companion object {
        const val KIND = 1200

        const val STREAM_TAG = "stream"
        const val ROUTE_TAG = "route"
        const val BROKER_TAG = "broker"
        const val ROUTE_QUIC = "quic"

        fun tags(
            streamId: HexKey,
            brokerCandidates: List<String>,
            route: String = ROUTE_QUIC,
        ): Array<Array<String>> =
            buildList {
                add(arrayOf(STREAM_TAG, streamId))
                add(arrayOf(ROUTE_TAG, route))
                brokerCandidates.forEach { add(arrayOf(BROKER_TAG, it)) }
            }.toTypedArray()

        /**
         * Read the start view from a kind-1200 payload's tags, or null when
         * this is not a stream start. A missing `route` means `quic`; a
         * missing `stream` tag means the payload is not usable as an anchor at
         * all, so it is rejected rather than defaulted.
         */
        fun fromTags(
            kind: Int,
            tags: Array<Array<String>>,
        ): AgentTextStreamStart? {
            if (kind != KIND) return null
            val streamId = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_TAG }?.get(1) ?: return null
            val route = tags.firstOrNull { it.size >= 2 && it[0] == ROUTE_TAG }?.get(1) ?: ROUTE_QUIC
            val brokers = tags.filter { it.size >= 2 && it[0] == BROKER_TAG }.map { it[1] }
            return AgentTextStreamStart(streamId, route, brokers)
        }
    }
}

/**
 * The `stream` / `stream-hash` / `stream-chunks` tags a kind-9 chat carries to
 * close a stream out.
 *
 * The final chat is the durable message; the stream was a live preview of the
 * text it now contains. A receiver that folded every record compares its own
 * [AgentTextStreamTranscriptV1] against these two values: agreement means it
 * saw exactly the stream the publisher sent, and disagreement means records
 * were dropped, reordered, or injected — even though each one opened.
 */
class AgentTextStreamFinal(
    val streamId: HexKey,
    val transcriptHash: HexKey,
    val chunkCount: Long,
) {
    companion object {
        const val STREAM_HASH_TAG = "stream-hash"
        const val STREAM_CHUNKS_TAG = "stream-chunks"

        fun tags(
            streamId: HexKey,
            transcriptHash: HexKey,
            chunkCount: Long,
        ): Array<Array<String>> =
            arrayOf(
                arrayOf(AgentTextStreamStart.STREAM_TAG, streamId),
                arrayOf(STREAM_HASH_TAG, transcriptHash),
                arrayOf(STREAM_CHUNKS_TAG, chunkCount.toString()),
            )

        fun fromTags(tags: Array<Array<String>>): AgentTextStreamFinal? {
            val streamId = tags.firstOrNull { it.size >= 2 && it[0] == AgentTextStreamStart.STREAM_TAG }?.get(1) ?: return null
            val hash = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_HASH_TAG }?.get(1) ?: return null
            val chunks = tags.firstOrNull { it.size >= 2 && it[0] == STREAM_CHUNKS_TAG }?.get(1)?.toLongOrNull() ?: return null
            return AgentTextStreamFinal(streamId, hash, chunks)
        }
    }
}
