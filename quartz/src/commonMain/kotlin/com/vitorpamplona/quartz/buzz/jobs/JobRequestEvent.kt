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
package com.vitorpamplona.quartz.buzz.jobs

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz agent job request (`kind:43001`): the root event of the agent job protocol -
 * one party asks an agent to perform a task. The task description is carried in
 * `content` (plain text). An optional `h` tag scopes the request to a channel and an
 * optional `p` tag names the target agent.
 *
 * SCHEMA CAVEAT: kinds 43001-43006 are *reserved* in `buzz-core/src/kind.rs` and read
 * as feed activity in `buzz-db/src/feed.rs`, but the referenced Buzz clone ships no
 * builder or ingest handler pinning their tags/content. This model (plain-text content,
 * `h` channel, `p` counterparty, `e` request reference on replies, optional `status`)
 * is a best-effort shape and must be reconciled once Buzz implements the protocol.
 */
@Immutable
class JobRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The task description - the event `content`. */
    fun request() = content

    /** The channel this job is scoped to - the `h` tag. */
    fun channel() = tags.jobChannel()

    /** The target agent - the `p` tag. */
    fun target() = tags.jobParticipant()

    companion object {
        const val KIND = 43001

        fun build(
            request: String,
            channelId: String? = null,
            targetAgent: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JobRequestEvent>.() -> Unit = {},
        ) = eventTemplate<JobRequestEvent>(KIND, request, createdAt) {
            channelId?.let { jobChannel(it) }
            targetAgent?.let { jobParticipant(it) }
            initializer()
        }
    }
}
