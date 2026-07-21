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
package com.vitorpamplona.quartz.buzz.stream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON content of a Buzz [SystemMessageEvent] (`kind:40099`) — a relay-authored
 * record of a channel state change (join, leave, rename, archive, delete, ...).
 *
 * Field names mirror the `serde_json::json!` payloads emitted by `emit_system_message`
 * and its callers in Buzz's `buzz-relay/src/handlers/side_effects.rs`. Only [type] is
 * always present; [actor] is present on almost every variant, and the remaining fields
 * are variant-specific (`member_joined`/`member_removed` carry [target], `topic_changed`
 * carries [topic], `ttl_changed` carries [ttlSeconds], etc). Unknown keys are ignored
 * for forward compatibility.
 */
@Serializable
data class SystemMessagePayload(
    val type: String,
    val actor: String? = null,
    val target: String? = null,
    val topic: String? = null,
    val purpose: String? = null,
    val visibility: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Long? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): SystemMessagePayload = JSON.decodeFromString(json)
    }
}
