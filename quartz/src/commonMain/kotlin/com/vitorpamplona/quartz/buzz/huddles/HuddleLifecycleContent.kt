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
package com.vitorpamplona.quartz.buzz.huddles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `content` JSON shared by every Buzz huddle-lifecycle event (`kind:48100`–`48103`):
 * the id of the ephemeral audio channel the lifecycle transition applies to. The parent
 * (timeline) channel is carried separately in the `h` tag. Field name is snake_case on
 * the wire. Ground truth: `buzz-relay/src/audio/handler.rs::emit_participant_event`.
 */
@Serializable
data class HuddleLifecycleContent(
    @SerialName("ephemeral_channel_id") val ephemeralChannelId: String,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): HuddleLifecycleContent = JSON.decodeFromString(json)

        fun decodeFromJsonOrNull(json: String): HuddleLifecycleContent? =
            try {
                decodeFromJson(json)
            } catch (_: Exception) {
                null
            }
    }
}
