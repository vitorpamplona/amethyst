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
 * The JSON `content` of a Buzz NIP-CW thread-bounds overlay (`kind:39007`):
 * `{"version":1,"direction":"older","has_more":<bool>,"next_cursor":{"created_at":<s>,"id":<hex>}|null}`.
 * It is the sole authority on a thread window's exhaustion; clients echo [nextCursor] back as
 * `until` + `before_id` to request the next (older) page. [isConsistent] carries the NIP's
 * client-side checks on the body. Ground truth: `buzz-relay/src/api/bridge/thread_window.rs`.
 */
@Serializable
data class ThreadWindowBoundsContent(
    val version: Int = VERSION,
    val direction: String = DIRECTION_OLDER,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_cursor") val nextCursor: NextCursor? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /** Version 1, direction `older`, and a cursor present exactly when [hasMore]. */
    fun isConsistent() = version == VERSION && direction == DIRECTION_OLDER && (nextCursor != null) == hasMore

    companion object {
        const val VERSION = 1
        const val DIRECTION_OLDER = "older"

        // The relay writes `"next_cursor":null` on an exhausted page, so nulls stay explicit.
        val JSON =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): ThreadWindowBoundsContent = JSON.decodeFromString(json)
    }
}
