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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON `content` of a Buzz NIP-CW window-bounds overlay (`kind:39006`) — the sole
 * authority on window exhaustion. Mirrors `buzz-relay/src/api/bridge.rs`: [hasMore] is
 * whether more rows exist beyond this page, and [nextCursor] is the cursor to request them
 * (null when exhausted). Clients MUST NOT infer `has_more` from row counts.
 */
@Serializable
data class WindowBoundsContent(
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_cursor") val nextCursor: NextCursor? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): WindowBoundsContent = JSON.decodeFromString(json)
    }
}

/** A window pagination cursor: the [createdAt] unix-seconds timestamp and hex event [id] of the boundary row. */
@Serializable
data class NextCursor(
    @SerialName("created_at") val createdAt: Long,
    val id: String,
)
