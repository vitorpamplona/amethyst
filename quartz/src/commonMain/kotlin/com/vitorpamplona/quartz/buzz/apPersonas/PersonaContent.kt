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
package com.vitorpamplona.quartz.buzz.apPersonas

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON body stored in a Buzz Persona event ([PersonaEvent], NIP-AP `kind:30175`).
 *
 * This is the on-the-wire projection published by the workspace owner — ground truth is
 * `PersonaEventContent` in Buzz's `desktop/src-tauri/src/managed_agents/persona_events.rs`
 * (NOT `PersonaConfig` in `buzz-persona/src/persona.rs`, which is the local `.persona.md`
 * YAML parser and is never serialized to a Nostr event). Field declaration order is fixed
 * upstream because serde emits in order and that order pins the content bytes (and thus the
 * NIP-01 event id + the persona content hash); it is mirrored here.
 *
 * [displayName] is required; every other field is optional. Wire field names are snake_case
 * (the Rust struct carries no `rename_all`, so the raw identifiers are the wire names) and are
 * mapped with [SerialName]. [respondTo] is a free-form string here (the persona's default
 * author-gate mode), unlike the managed-agent projection which uses a closed enum. Unknown JSON
 * fields are ignored for forward compatibility.
 */
@Serializable
data class PersonaContent(
    @SerialName("display_name") val displayName: String,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val runtime: String? = null,
    val model: String? = null,
    val provider: String? = null,
    // NEVER-encode the empty defaults to match Rust's `skip_serializing_if = Vec::is_empty`
    // (persona_events.rs): emitting `[]` where upstream omits the field would shift the
    // content bytes and spuriously trip the desktop's persona_content_hash drift check.
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("name_pool") val namePool: List<String> = emptyList(),
    @SerialName("respond_to") val respondTo: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("respond_to_allowlist") val respondToAllowlist: List<String> = emptyList(),
    val parallelism: Int? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): PersonaContent = JSON.decodeFromString(json)
    }
}
