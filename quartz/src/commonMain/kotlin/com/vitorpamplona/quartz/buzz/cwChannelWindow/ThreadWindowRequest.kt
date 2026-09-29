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
package com.vitorpamplona.quartz.buzz.cwChannelWindow

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The response-affecting arguments of a NIP-CW thread-mode request (`thread_window: true`,
 * served on Buzz's NIP-98 HTTP `POST /query`), normalized the way the relay normalizes them,
 * so a client can recompute the request binding a [ThreadWindowBoundsEvent] must carry.
 *
 * [kinds] must be drawn from `9`, `40002`, `45001`, `45003` (sorted and deduplicated here);
 * [limit] is 1–200 (default 50), [depth] 1–100 (default 100); [cursor] is null for the head
 * page, else the previous bounds' `next_cursor`. Ground truth: `Request::parse` and
 * `Request::binding` in Buzz's `buzz-core/src/thread_window.rs`.
 */
data class ThreadWindowRequest(
    val channelId: String,
    val rootId: HexKey,
    val kinds: List<Int>,
    val limit: Int = DEFAULT_LIMIT,
    val depth: Int = MAX_DEPTH,
    val cursor: NextCursor? = null,
    val includeAux: Boolean = false,
) {
    init {
        require(kinds.isNotEmpty() && kinds.all { it in ROW_KINDS }) { "thread_window supports row kinds 9, 40002, 45001, 45003 only" }
        require(limit in 1..MAX_LIMIT) { "thread_window: limit must be an integer in 1..=$MAX_LIMIT" }
        require(depth in 1..MAX_DEPTH) { "thread_window: depth_limit must be an integer in 1..=$MAX_DEPTH" }
        require(rootId.length == 64 && rootId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "thread_window: expected a full 64-hex event id" }
        require(cursor == null || cursor.createdAt >= 0) { "thread_window: until must be nonnegative integer seconds" }
    }

    /**
     * The `d` value of the bounds event answering this request for [readerPubKey] on [host]:
     * `"tw:1:" + hex(SHA-256(compact JSON of
     * ["tw",1,"older",host,reader,channel,root,limit,depth,kinds,cursor,include_aux]))`, where
     * `cursor` is `null` or `[created_at,"<id>"]`. [host] is the relay's normalized authority
     * (e.g. `relay.example` or `localhost:3000`); ids and the reader are lowercased first.
     */
    fun binding(
        host: String,
        readerPubKey: HexKey,
    ): String {
        val canonical =
            JsonArray(
                listOf(
                    JsonPrimitive("tw"),
                    JsonPrimitive(1),
                    JsonPrimitive(ThreadWindowBoundsContent.DIRECTION_OLDER),
                    JsonPrimitive(host),
                    JsonPrimitive(readerPubKey.lowercase()),
                    JsonPrimitive(channelId.lowercase()),
                    JsonPrimitive(rootId.lowercase()),
                    JsonPrimitive(limit),
                    JsonPrimitive(depth),
                    JsonArray(kinds.distinct().sorted().map { JsonPrimitive(it) }),
                    cursor?.let { JsonArray(listOf(JsonPrimitive(it.createdAt), JsonPrimitive(it.id.lowercase()))) } ?: JsonNull,
                    JsonPrimitive(includeAux),
                ),
            )
        return BINDING_PREFIX + sha256(canonical.toString().encodeToByteArray()).toHexKey()
    }

    companion object {
        const val BINDING_PREFIX = "tw:1:"
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
        const val MAX_DEPTH = 100

        /** Conversation row kinds a thread window may request. */
        val ROW_KINDS = setOf(9, 40002, 45001, 45003)
    }
}
