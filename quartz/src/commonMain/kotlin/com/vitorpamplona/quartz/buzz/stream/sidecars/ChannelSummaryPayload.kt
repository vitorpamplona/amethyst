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
package com.vitorpamplona.quartz.buzz.stream.sidecars

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Best-effort JSON content of a Buzz [ChannelSummaryEvent] (`kind:40901`) — channel
 * metadata plus computed fields, signed by the relay as a read-only sidecar.
 *
 * WARNING: `KIND_CHANNEL_SUMMARY` is declared in Buzz's `buzz-core/src/kind.rs` as a
 * relay-only sidecar ("never client-submitted"), but no emitter and no content struct
 * exist in the current Buzz source — the wire schema is UNCONFIRMED. The fields below
 * are inferred from the `ChannelSummary` reader in `buzz-cli/src/commands/channels.rs`
 * (channel metadata) plus the natural "computed fields" (counts). Every field is
 * optional and unknown keys are ignored so parsing survives the real schema once it
 * lands. See report uncertainties.
 */
@Serializable
data class ChannelSummaryPayload(
    @SerialName("channel_id") val channelId: String? = null,
    val name: String? = null,
    val about: String? = null,
    val topic: String? = null,
    val purpose: String? = null,
    val visibility: String? = null,
    val archived: Boolean? = null,
    @SerialName("member_count") val memberCount: Long? = null,
    @SerialName("message_count") val messageCount: Long? = null,
    @SerialName("last_activity_at") val lastActivityAt: Long? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): ChannelSummaryPayload = JSON.decodeFromString(json)
    }
}
