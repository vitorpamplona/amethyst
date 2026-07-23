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
package com.vitorpamplona.quartz.buzz.aoObserver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The decrypted plaintext of an agent observer frame (NIP-AO, `kind:24200`).
 *
 * Two shapes travel over the same NIP-44 v2 ciphertext; the cleartext `frame`
 * tag ([com.vitorpamplona.quartz.buzz.aoObserver.tags.FrameTag]) says which:
 * - [ObserverTelemetryPayload] for `frame:telemetry` (agent-to-owner).
 * - [ObserverControlPayload] for `frame:control` (owner-to-agent).
 */
object ObserverPayload {
    val JSON =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
}

/**
 * Agent-to-owner telemetry frame body. Mirrors `ObserverEvent` in
 * `buzz-acp/src/observer.rs` (serde `rename_all = "camelCase"`), one entry from
 * the harness's in-process observer bus.
 *
 * [seq] is a process-local monotonic counter, [timestamp] is RFC3339 UTC, and
 * [kind] is a semantic label such as `acp_read`, `turn_started`, or
 * `control_result`. Every id/context field is optional. [payload] is the raw
 * or semantic event body, kept as an opaque [JsonElement] because its schema
 * varies per [kind].
 */
@Serializable
data class ObserverTelemetryPayload(
    val seq: Long,
    val timestamp: String,
    val kind: String,
    val agentIndex: Int? = null,
    val channelId: String? = null,
    val sessionId: String? = null,
    val turnId: String? = null,
    val startedAt: String? = null,
    val payload: JsonElement? = null,
) {
    fun encodeToJson(): String = ObserverPayload.JSON.encodeToString(this)

    companion object {
        fun decodeFromJson(json: String): ObserverTelemetryPayload = ObserverPayload.JSON.decodeFromString(json)
    }
}

/**
 * Owner-to-agent control frame body. The relay never reads this — it routes on
 * the cleartext `frame:control` tag — so the schema is defined only by the
 * agent-side reader in `buzz-acp/src/lib.rs` (`handle_relay_observer_control_event`),
 * which dispatches on [type] and pulls [channelId] / [modelId] ad hoc.
 *
 * Known [type] values are [CANCEL_TURN] and [SWITCH_MODEL]; an unrecognized
 * value is retained verbatim (the agent silently ignores it) rather than
 * dropping the payload.
 */
@Serializable
data class ObserverControlPayload(
    @SerialName("type") val type: String,
    val channelId: String? = null,
    val modelId: String? = null,
) {
    fun encodeToJson(): String = ObserverPayload.JSON.encodeToString(this)

    companion object {
        const val CANCEL_TURN = "cancel_turn"
        const val SWITCH_MODEL = "switch_model"

        fun decodeFromJson(json: String): ObserverControlPayload = ObserverPayload.JSON.decodeFromString(json)
    }
}
