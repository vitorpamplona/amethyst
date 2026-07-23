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
 * A Buzz "add member to DM" command (`kind:41011`): adds a new participant to an
 * existing group DM. Scopes the target conversation with the `h` (NIP-29 group id =
 * DM channel UUID) tag and names the invited member with a single `p` tag; the
 * `content` is empty.
 *
 * Ground truth: `buzz-sdk/src/builders.rs` (`build_dm_add_member`) and
 * `buzz-cli/src/commands/dms.rs` (`cmd_add_dm_member`).
 */
@Immutable
class DmAddMemberEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The target DM channel id - the `h` tag. */
    fun channelId() = tags.dmChannelId()

    /** The member being added - the single `p` tag. */
    fun member() = tags.dmParticipants().firstOrNull()

    companion object {
        const val KIND = 41011

        fun build(
            channelId: String,
            member: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DmAddMemberEvent>.() -> Unit = {},
        ) = eventTemplate<DmAddMemberEvent>(KIND, "", createdAt) {
            dmChannel(channelId)
            dmMember(member)
            initializer()
        }
    }
}
