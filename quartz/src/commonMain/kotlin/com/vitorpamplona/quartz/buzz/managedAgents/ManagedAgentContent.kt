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
package com.vitorpamplona.quartz.buzz.managedAgents

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Inbound author-gate mode of a managed agent — who the agent responds to. Wire values are
 * kebab-case (`#[serde(rename_all = "kebab-case")]` on `RespondTo` in Buzz's
 * `desktop/src-tauri/src/managed_agents/types.rs`).
 *
 * The upstream enum has no catch-all variant, so an unrecognized wire value fails to decode
 * (there is no `UNKNOWN`, unlike the turn-metric stop reason). The three variants below are the
 * complete set.
 */
@Serializable
enum class RespondTo {
    @SerialName("owner-only")
    OWNER_ONLY,

    @SerialName("allowlist")
    ALLOWLIST,

    @SerialName("anyone")
    ANYONE,
}

/**
 * The JSON body stored in a Buzz Managed Agent event ([ManagedAgentEvent], NIP-AP
 * `kind:30177`).
 *
 * Explicit opt-IN allowlist projection of the agent's public identity + behavioral config,
 * published by the workspace owner. Ground truth is `ManagedAgentEventContent` in Buzz's
 * `desktop/src-tauri/src/managed_agents/agent_events.rs`. Because these events are
 * world-readable, the upstream projection is a hard security boundary — it MUST NEVER carry the
 * agent's secret key, the NIP-OA auth tag, env vars, the provider backend blob, or any runtime
 * field. This model mirrors only the safe projected fields; the excluded fields have no property
 * here by design.
 *
 * [name] and [parallelism] and [respondTo] are always present on the wire; the definition quad
 * ([systemPrompt], [model], [provider], [personaSourceVersion]) is omitted upstream when the
 * agent is definition-linked (resolves through its `kind:30175` persona) and present when it is
 * standalone, so all four are optional here. Wire field names are snake_case (the Rust struct
 * carries no `rename_all`). Unknown JSON fields are ignored for forward compatibility.
 */
@Serializable
data class ManagedAgentContent(
    val name: String,
    @SerialName("persona_id") val personaId: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("persona_source_version") val personaSourceVersion: String? = null,
    val parallelism: Int,
    @SerialName("respond_to") val respondTo: RespondTo,
    @SerialName("respond_to_allowlist") val respondToAllowlist: List<String> = emptyList(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): ManagedAgentContent = JSON.decodeFromString(json)
    }
}
