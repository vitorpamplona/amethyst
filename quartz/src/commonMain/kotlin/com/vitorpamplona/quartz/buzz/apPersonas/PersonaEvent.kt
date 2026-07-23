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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Persona (NIP-AP, `kind:30175`): an addressable, world-readable persona
 * definition published by the workspace owner. Addressed by `(pubkey, 30175, d)` where the
 * `d` tag is the plaintext persona slug (grammar `^[a-z0-9][a-z0-9_-]{0,63}$`, enforced by
 * the relay in `buzz-relay/src/handlers/ingest.rs::validate_persona_envelope`).
 *
 * The `content` is a plaintext JSON [PersonaContent]. Ground truth for the content projection
 * is `desktop/src-tauri/src/managed_agents/persona_events.rs`.
 */
@Immutable
class PersonaEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The persona slug — the `d` tag. */
    fun slug() = dTag()

    /** Parses the persona configuration, or throws if the JSON is malformed. */
    fun persona(): PersonaContent = PersonaContent.decodeFromJson(content)

    fun personaOrNull(): PersonaContent? =
        try {
            persona()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30175

        fun build(
            persona: PersonaContent,
            slug: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PersonaEvent>.() -> Unit = {},
        ) = eventTemplate<PersonaEvent>(KIND, persona.encodeToJson(), createdAt) {
            dTag(slug)
            initializer()
        }
    }
}
