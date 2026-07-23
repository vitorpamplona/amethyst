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
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
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
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The managed agent's pubkey — the `d` tag. */
    fun agentPubKey() = dTag()

    /** Parses the managed-agent projection, or throws if the JSON is malformed. */
    fun agent(): ManagedAgentContent = ManagedAgentContent.decodeFromJson(content)

    fun agentOrNull(): ManagedAgentContent? =
        try {
            agent()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

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
