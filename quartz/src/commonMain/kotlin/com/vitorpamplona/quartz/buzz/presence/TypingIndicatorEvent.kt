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
package com.vitorpamplona.quartz.buzz.presence

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.presence.typing.channel
import com.vitorpamplona.quartz.buzz.presence.typing.threadReply
import com.vitorpamplona.quartz.buzz.presence.typing.threadRoot
import com.vitorpamplona.quartz.buzz.presence.typing.typingChannel
import com.vitorpamplona.quartz.buzz.presence.typing.typingThreadReply
import com.vitorpamplona.quartz.buzz.presence.typing.typingThreadRoot
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz typing indicator (`kind:20002`): an ephemeral "user is typing" signal scoped
 * to a channel by an `h` tag, with optional NIP-10 `root`/`reply` `e` tags naming the
 * thread being replied to. Content is empty. Ephemeral (20000–29999) — never stored.
 * Ground truth: `buzz-acp/src/relay.rs::build_typing_event`.
 */
@Immutable
class TypingIndicatorEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID this typing indicator targets — the `h` tag. */
    fun channelId(): String? = tags.typingChannel()

    /** The thread root event id, if present. */
    fun threadRootId(): String? = tags.typingThreadRoot()

    /** The reply (parent) event id, if present. */
    fun threadReplyId(): String? = tags.typingThreadReply()

    companion object {
        const val KIND = 20002

        fun build(
            channelId: String,
            rootEventId: HexKey? = null,
            replyEventId: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<TypingIndicatorEvent>.() -> Unit = {},
        ) = eventTemplate<TypingIndicatorEvent>(KIND, "", createdAt) {
            channel(channelId)
            // Match the relay: emit the root marker only when it differs from the reply.
            if (replyEventId != null && rootEventId != null && rootEventId != replyEventId) {
                threadRoot(rootEventId)
            }
            replyEventId?.let { threadReply(it) }
            initializer()
        }
    }
}
