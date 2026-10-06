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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Managed Agent (NIP-AP, `kind:30177`): an addressable, world-readable managed-agent
 * definition published by the workspace owner. Addressed by `(pubkey, 30177, d)` where the `d`
 * tag is the agent's pubkey.
 *
 * The `content` is a plaintext JSON [ManagedAgentContent] — an explicit opt-IN allowlist that
 * carries only the agent's public identity + behavioral config, never secrets or runtime state.
 * Ground truth for the content projection is
 * `desktop/src-tauri/src/managed_agents/agent_events.rs`.
 */
@Immutable
class ManagedAgentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    PubKeyHintProvider,
    AddressHintProvider {
    // The agent pubkey is the `d` value and the allowlist sits in the JSON body: neither has a relay slot.
    override fun pubKeyHints(): List<PubKeyHint> = emptyList()

    override fun linkedPubKeys(): List<HexKey> {
        val agent = agentPubKey().takeIf { it.isValid() }
        val allowlist = agentOrNull()?.respondToAllowlist?.filter { it.isValid() } ?: emptyList()
        return listOfNotNull(agent) + allowlist
    }

    override fun addressHints(): List<AddressHint> = emptyList()

    /** The `persona_id` names one of the author's own `30175` personas by its slug. */
    override fun linkedAddressIds(): List<String> = listOfNotNull(agentOrNull()?.personaId?.takeIf { it.isNotEmpty() }?.let { Address.assemble(PersonaEvent.KIND, pubKey, it) })

    override fun indexableContent() = agentOrNull()?.let { listOfNotNull(it.name, it.systemPrompt).joinToString("\n") } ?: ""

    // The read path. The parse happens once and its fields are handed over one by
    // one; a scan that stops on the first hit never pays for the rest of the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        val data = agentOrNull() ?: return
        if (!visitor.visit(data.name)) return
        if (!visitor.visit(data.systemPrompt)) return
    }

    /** The managed agent's pubkey — the `d` tag. */
    fun agentPubKey() = dTag()

    // linked*() runs for every relay copy of every event and forEachIndexableField() on every
    // keystroke, so the body is decoded once per instance — a failure included. Events are
    // immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var agentCache: Result<ManagedAgentContent>? = null

    private fun parsedAgent(): Result<ManagedAgentContent> =
        agentCache ?: try {
            Result.success(ManagedAgentContent.decodeFromJson(content))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }.also { agentCache = it }

    /** Parses the managed-agent projection (once), or throws if the JSON is malformed. */
    fun agent(): ManagedAgentContent = parsedAgent().getOrThrow()

    fun agentOrNull(): ManagedAgentContent? = parsedAgent().getOrNull()

    companion object {
        const val KIND = 30177

        fun build(
            agent: ManagedAgentContent,
            agentPubKey: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ManagedAgentEvent>.() -> Unit = {},
        ) = eventTemplate<ManagedAgentEvent>(KIND, agent.encodeToJson(), createdAt) {
            dTag(agentPubKey)
            initializer()
        }
    }
}
