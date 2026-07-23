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
 * The JSON `content` of a Buzz NIP-CW thread-summary overlay (`kind:39005`).
 *
 * Field names are snake_case on the wire, mirroring the object the relay synthesizes in
 * `buzz-relay/src/api/bridge.rs`: [replyCount] direct replies, [descendantCount] total
 * events in the thread, [lastReplyAt] the newest reply's unix-seconds timestamp (null when
 * there are no replies), and [participants] the hex pubkeys that posted in the thread.
 */
@Serializable
data class ThreadSummaryContent(
    @SerialName("reply_count") val replyCount: Long,
    @SerialName("descendant_count") val descendantCount: Long,
    @SerialName("last_reply_at") val lastReplyAt: Long? = null,
    val participants: List<String> = emptyList(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): ThreadSummaryContent = JSON.decodeFromString(json)
    }
}
