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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A comment reply on a Buzz forum post (`kind:45003`). The `content` is the reply body,
 * an `h` tag scopes it to the channel, NIP-10 `e` tags (`root`/`reply` markers) place it
 * in the thread, and optional `p` tags mention members. Ground truth: `build_forum_comment`
 * + `thread_tags` in Buzz's `buzz-sdk/src/builders.rs`.
 */
@Immutable
class ForumCommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID (the `h` tag) this comment belongs to. */
    fun channel() = tags.forumChannel()

    /** The thread root event id (`root`-marked `e` tag), or null for a direct reply. */
    fun threadRoot() = tags.forumThreadRoot()

    /** The immediate-parent event id (`reply`-marked `e` tag). */
    fun replyTo() = tags.forumThreadReply()

    /** The pubkeys mentioned by this comment (`p` tags). */
    fun mentions() = tags.forumMentions()

    /** The comment body - the event `content`. */
    fun body() = content

    companion object {
        const val KIND = 45003

        /**
         * Builds a forum comment. When [rootEventId] equals [parentEventId] the comment is a
         * direct reply to the thread root; otherwise it is a nested reply - see [forumThread].
         */
        fun build(
            channelId: String,
            body: String,
            rootEventId: HexKey,
            parentEventId: HexKey,
            mentions: List<HexKey> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ForumCommentEvent>.() -> Unit = {},
        ) = eventTemplate<ForumCommentEvent>(KIND, body, createdAt) {
            forumChannel(channelId)
            forumThread(rootEventId, parentEventId)
            forumMentions(mentions)
            initializer()
        }
    }
}
