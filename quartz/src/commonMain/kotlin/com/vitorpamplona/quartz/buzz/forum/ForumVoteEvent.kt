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
package com.vitorpamplona.quartz.buzz.forum

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.forum.tags.VoteDirection
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A vote on a Buzz forum post (`kind:45002`). The `content` is `"+"` (up) or `"-"` (down),
 * an `h` tag scopes it to the channel, and an `e` tag names the target event. Ground truth:
 * `build_vote` in Buzz's `buzz-sdk/src/builders.rs`.
 */
@Immutable
class ForumVoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID (the `h` tag) this vote belongs to. */
    fun channel() = tags.forumChannel()

    /** The `e` target event this vote applies to. */
    fun target() = tags.forumVoteTarget()

    /** The vote direction parsed from `content` (`"+"`/`"-"`), or null if malformed. */
    fun direction() = VoteDirection.fromContent(content)

    companion object {
        const val KIND = 45002

        fun build(
            channelId: String,
            targetEventId: HexKey,
            direction: VoteDirection,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ForumVoteEvent>.() -> Unit = {},
        ) = eventTemplate<ForumVoteEvent>(KIND, direction.code, createdAt) {
            forumChannel(channelId)
            forumVoteTarget(targetEventId)
            initializer()
        }
    }
}
