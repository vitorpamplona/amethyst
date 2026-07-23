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
package com.vitorpamplona.quartz.buzz.dm

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz "DM created" confirmation (`kind:41001`): the **relay-signed** record of a
 * materialized DM conversation. Clients list their DMs by querying `kind:41001`
 * filtered by their own `#p`; each event carries the DM id in a `d` tag and one `p`
 * tag per participant.
 *
 * This is a relay-authored sidecar, not a client-published command - clients react to
 * [DmOpenEvent]/[DmAddMemberEvent] by emitting the command, and the relay confirms
 * with this event. [build] is provided for tests/tooling parity; in production the
 * relay is the signer. Ground truth: `buzz-cli/src/commands/dms.rs` (`cmd_list_dms`,
 * which reads the `d` tag as `dm_id` and the `p` tags as participants) and
 * `buzz-core/src/kind.rs` (`KIND_DM_CREATED`).
 */
@Immutable
class DmCreatedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The DM id - the `d` tag (empty string when absent). */
    fun dmId() = tags.dmId()

    /** The DM participants - one per `p` tag. */
    fun participants() = tags.dmParticipants()

    companion object {
        const val KIND = 41001

        fun build(
            dmId: String,
            participants: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DmCreatedEvent>.() -> Unit = {},
        ) = eventTemplate<DmCreatedEvent>(KIND, "", createdAt) {
            dmId(dmId)
            participants(participants)
            initializer()
        }
    }
}
