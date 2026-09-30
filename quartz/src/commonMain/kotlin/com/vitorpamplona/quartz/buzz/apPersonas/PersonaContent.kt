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
    // The ACP harness command the agent runs under. On a shared (catalog) head it carries only a
    // portable alias - see [forSharedCatalog].
    @SerialName("acp_command") val acpCommand: String? = null,
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
    // Appended after the original fields upstream so a record without them serializes
    // byte-identically to the pre-revision era. [description] is short PUBLIC display text
    // (max [DESCRIPTION_MAX_CHARS]); [sessionPolicy] is omitted for the default `channel`.
    val description: String? = null,
    @SerialName("session_policy") val sessionPolicy: String? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /** True when the agent keeps a separate ACP conversation per channel thread. */
    fun isThreadSessionPolicy() = sessionPolicy == SESSION_POLICY_THREAD

    /**
     * The projection Buzz publishes on a shared (`["shared","true"]`) head: a catalog reader on
     * another machine can only run a portable harness alias, so an unset command becomes the
     * explicit stock [DEFAULT_ACP_COMMAND] (distinguishing a reset from an omitted override) and
     * a machine-local command is dropped. Mirrors `build_persona_event` in Buzz's
     * `desktop/src-tauri/src/managed_agents/persona_events.rs`.
     */
    fun forSharedCatalog(): PersonaContent =
        copy(
            acpCommand =
                when {
                    acpCommand == null -> DEFAULT_ACP_COMMAND
                    isPortableAcpCommand(acpCommand) -> acpCommand
                    else -> null
                },
        )

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): PersonaContent = JSON.decodeFromString(json)

        /** Buzz's stock ACP harness (`DEFAULT_ACP_COMMAND` in `managed_agents/types.rs`). */
        const val DEFAULT_ACP_COMMAND = "buzz-acp"

        /** The only non-default [sessionPolicy] value; anything else reads as `channel`. */
        const val SESSION_POLICY_THREAD = "thread"

        /** Upper bound Buzz puts on [description]. */
        const val DESCRIPTION_MAX_CHARS = 280

        /**
         * A harness command another machine can run: the stock `buzz-acp`, or `buzz-<name>-acp`
         * with `<name>` of ASCII letters, digits, `-` or `_`, at most 255 bytes in all. Mirrors
         * `is_portable_acp_command` in Buzz's `managed_agents/backend.rs`.
         */
        fun isPortableAcpCommand(command: String): Boolean {
            if (command == DEFAULT_ACP_COMMAND) return true
            if (command.length > 255 || !command.startsWith("buzz-") || !command.endsWith("-acp")) return false
            if (command.length <= "buzz-".length + "-acp".length) return false
            val name = command.substring("buzz-".length, command.length - "-acp".length)
            return name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
        }
    }
}
