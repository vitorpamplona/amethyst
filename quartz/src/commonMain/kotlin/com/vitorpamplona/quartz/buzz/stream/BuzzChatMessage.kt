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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.buzz.threading.buzzThread
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz channel message: a plain NIP-29 kind-9 [ChatEvent] in Buzz's tag shape. This is what every
 * Buzz client writes today (desktop, mobile, CLI); kind 40002 ([StreamMessageV2Event]) is only a
 * read-compat tail from the 10002 -> 40001 -> 40002 migration.
 *
 * Mirrors `build_message` in `buzz-sdk/src/builders.rs`, in its tag order:
 * - `["h", channel]`
 * - the NIP-10 thread markers when it is a reply ([buzzThread]: one `reply` marker for a direct
 *   reply, `root` + `reply` for a nested one) - derive [threadRoot] with
 *   `buzzThreadRootForReplyTo` on the parent or the relay rejects the ancestry
 * - one `p` per mention (the author may mention themselves)
 * - `["broadcast", "1"]` for a reply that should also show in the channel timeline instead of
 *   only in its thread
 *
 * Media (`imeta`) and NIP-30 `emoji` tags follow via [initializer].
 */
object BuzzChatMessage {
    fun build(
        channelId: String,
        content: String,
        threadRoot: HexKey? = null,
        replyTo: HexKey? = null,
        mentions: List<HexKey> = emptyList(),
        broadcast: Boolean = false,
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<ChatEvent>.() -> Unit = {},
    ) = ChatEvent.build(content, createdAt) {
        hTag(channelId)
        if (replyTo != null) buzzThread(threadRoot ?: replyTo, replyTo)
        mentions(mentions)
        if (broadcast) broadcast()
        initializer()
    }
}
