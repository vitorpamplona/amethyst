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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Profile (`kind:10100`): a replaceable, world-readable, agent-authored event
 * carrying agent metadata + the author's channel-add policy. Replaceable (10000-19999), so it
 * is keyed by `(pubkey, 10100)` with an empty `d` tag and there is at most one per author.
 *
 * The `content` is a loose JSON [AgentProfileContent] — see that class for the FLAG: Buzz has no
 * single authoritative schema, so the model is conservative and tolerant of unknown keys.
 * Ground truth consumers: `buzz-relay/src/handlers/side_effects.rs::handle_agent_profile`
 * (reads `channel_add_policy`) and `desktop/src-tauri/src/nostr_convert.rs::agents_from_events`
 * (reads loose agent-discovery metadata).
 */
@Immutable
class AgentProfileEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Parses the profile metadata, or throws if the JSON is malformed. */
    fun profile(): AgentProfileContent = AgentProfileContent.decodeFromJson(content)

    fun profileOrNull(): AgentProfileContent? =
        try {
            profile()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 10100

        fun build(
            profile: AgentProfileContent,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AgentProfileEvent>.() -> Unit = {},
        ) = eventTemplate<AgentProfileEvent>(KIND, profile.encodeToJson(), createdAt) {
            initializer()
        }
    }
}
