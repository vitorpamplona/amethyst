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
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz "open DM" command (`kind:41010`): opens (or re-opens) a direct-message
 * conversation between the author and 1-8 named participants. The participants are
 * carried as one `p` tag each and the `content` is empty - the relay executes the
 * command, materializes the DM channel, and confirms it back with a relay-signed
 * [DmCreatedEvent] (`kind:41001`).
 *
 * Ground truth: `buzz-sdk/src/builders.rs` (`build_dm_open`) and
 * `buzz-relay/src/handlers/command_executor.rs`.
 */
@Immutable
class DmOpenEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The DM participants - one per `p` tag. */
    fun participants() = tags.dmParticipants()

    companion object {
        const val KIND = 41010

        /** Buzz enforces 1-8 participants for a DM open command. */
        const val MIN_PARTICIPANTS = 1
        const val MAX_PARTICIPANTS = 8

        fun build(
            participants: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DmOpenEvent>.() -> Unit = {},
        ): EventTemplate<DmOpenEvent> {
            require(participants.size in MIN_PARTICIPANTS..MAX_PARTICIPANTS) {
                "A DM open requires $MIN_PARTICIPANTS-$MAX_PARTICIPANTS participants (got ${participants.size})"
            }
            return eventTemplate(KIND, "", createdAt) {
                participants(participants)
                initializer()
            }
        }
    }
}
