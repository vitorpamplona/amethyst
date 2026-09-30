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
import com.vitorpamplona.quartz.buzz.apPersonas.tags.SharedTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
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
 *
 * Read access is **author-only unless shared**: the relay serves a persona to anyone but its
 * author only when it carries exactly `["shared","true"]` ([SharedTag]); otherwise it is
 * silently withheld from foreign REQs, COUNTs and id lookups. Device sync reads
 * `authors:[self]`, so the owner always sees their own.
 */
@Immutable
class PersonaEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = personaOrNull()?.let { listOfNotNull(it.displayName, it.systemPrompt).joinToString("\n") } ?: ""

    // The read path. The parse happens once and its fields are handed over one by
    // one; a scan that stops on the first hit never pays for the rest of the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        val data = personaOrNull() ?: return
        if (!visitor.visit(data.displayName)) return
        if (!visitor.visit(data.systemPrompt)) return
    }

    /** The persona slug — the `d` tag. */
    fun slug() = dTag()

    /** True when the persona is published to the community catalog (`["shared","true"]`). */
    fun isShared() = SharedTag.isShared(tags)

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

        /**
         * Builds a persona the way Buzz's `build_persona_event` does: a `d` slug tag, plus
         * `["shared","true"]` when [shared]. A shared head publishes the portable catalog
         * projection of its content ([PersonaContent.forSharedCatalog]).
         *
         * An edit should pass [priorHeadCreatedAt] (the replaced head's `created_at`) so the new
         * head sorts after it even when this clock lags (`monotonic_created_at` upstream).
         */
        fun build(
            persona: PersonaContent,
            slug: String,
            shared: Boolean = false,
            priorHeadCreatedAt: Long? = null,
            createdAt: Long = monotonicCreatedAt(priorHeadCreatedAt),
            initializer: TagArrayBuilder<PersonaEvent>.() -> Unit = {},
        ) = eventTemplate<PersonaEvent>(KIND, (if (shared) persona.forSharedCatalog() else persona).encodeToJson(), createdAt) {
            dTag(slug)
            if (shared) addUnique(SharedTag.assemble())
            initializer()
        }

        /** `max(now, prior + 1)`: a replacement head never sorts behind the one it replaces. */
        fun monotonicCreatedAt(priorHeadCreatedAt: Long?): Long {
            val now = TimeUtils.now()
            return if (priorHeadCreatedAt == null) now else maxOf(now, priorHeadCreatedAt + 1)
        }
    }
}
