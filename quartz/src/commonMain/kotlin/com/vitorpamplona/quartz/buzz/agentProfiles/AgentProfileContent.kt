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
package com.vitorpamplona.quartz.buzz.agentProfiles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON body stored in a Buzz Agent Profile event ([AgentProfileEvent], `kind:10100`).
 *
 * MODELLED CONSERVATIVELY — FLAGGED. Buzz has no single authoritative struct for `kind:10100`
 * content; the fields below are the union of the two consumers found in the Rust/TS ground truth:
 *
 *  1. The relay side effect `handle_agent_profile` in
 *     `buzz-relay/src/handlers/side_effects.rs` reads exactly one field, [channelAddPolicy]
 *     (`channel_add_policy`), to set the author's channel-add policy. The CLI writer
 *     (`buzz-cli/.../channels.rs`) publishes `{"channel_add_policy": "<policy>"}` and nothing
 *     else. Observed policy values: `anyone`, `owner_only`, `nobody`.
 *  2. The desktop agent-discovery reader `agents_from_events` in
 *     `desktop/src-tauri/src/nostr_convert.rs` treats the content as loose kind-0-like agent
 *     metadata, reading [name], [displayName], [agentType], [status], [capabilities],
 *     [channelIds] and defaulting each when absent.
 *
 * No writer emits all of these together, so every field is optional and unknown keys are
 * tolerated. The event author is the authoritative pubkey; any `pubkey` claimed inside the
 * content is deliberately NOT modelled here (upstream overwrites it with the signer). If a
 * definitive schema is published later, tighten this struct.
 */
@Serializable
data class AgentProfileContent(
    @SerialName("channel_add_policy") val channelAddPolicy: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("agent_type") val agentType: String? = null,
    val status: String? = null,
    val capabilities: List<String> = emptyList(),
    @SerialName("channel_ids") val channelIds: List<String> = emptyList(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): AgentProfileContent = JSON.decodeFromString(json)
    }
}
