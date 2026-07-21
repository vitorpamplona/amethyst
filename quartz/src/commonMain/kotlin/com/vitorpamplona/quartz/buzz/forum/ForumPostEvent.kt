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
 * A Buzz forum post - the root of a forum thread (`kind:45001`). The `content` is the
 * post body (plaintext, max 64 KiB). An `h` tag scopes it to a channel; optional `p`
 * tags mention channel members. Ground truth: `build_forum_post` in Buzz's
 * `buzz-sdk/src/builders.rs`.
 */
@Immutable
class ForumPostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID (the `h` tag) this post belongs to. */
    fun channel() = tags.forumChannel()

    /** The pubkeys mentioned by this post (`p` tags). */
    fun mentions() = tags.forumMentions()

    /** The post body - the event `content`. */
    fun body() = content

    companion object {
        const val KIND = 45001

        fun build(
            channelId: String,
            body: String,
            mentions: List<HexKey> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ForumPostEvent>.() -> Unit = {},
        ) = eventTemplate<ForumPostEvent>(KIND, body, createdAt) {
            forumChannel(channelId)
            forumMentions(mentions)
            initializer()
        }
    }
}
