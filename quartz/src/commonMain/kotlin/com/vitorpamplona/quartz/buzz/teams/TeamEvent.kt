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
package com.vitorpamplona.quartz.buzz.teams

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Team (NIP-AP, `kind:30176`): an addressable, world-readable team definition
 * published by the workspace owner. A team is a user-facing grouping of personas. Addressed by
 * `(pubkey, 30176, d)` where the `d` tag is the team's stable id.
 *
 * The `content` is a plaintext JSON [TeamContent]. Ground truth for the content projection is
 * `desktop/src-tauri/src/managed_agents/team_events.rs`.
 */
@Immutable
class TeamEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The team's stable id — the `d` tag. */
    fun teamId() = dTag()

    /** Parses the team configuration, or throws if the JSON is malformed. */
    fun team(): TeamContent = TeamContent.decodeFromJson(content)

    fun teamOrNull(): TeamContent? =
        try {
            team()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 30176

        fun build(
            team: TeamContent,
            teamId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TeamEvent>.() -> Unit = {},
        ) = eventTemplate<TeamEvent>(KIND, team.encodeToJson(), createdAt) {
            dTag(teamId)
            initializer()
        }
    }
}
