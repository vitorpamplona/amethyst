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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz stream (channel) chat message, `kind:40002`.
 *
 * The V2 successor of the plain `kind:9`/`kind:40001` group message (V1 used the
 * replaceable range `10002`, which was wrong). Channel-scoped via the `h` tag; the
 * text lives in [content], optionally carrying `p` mentions and a `broadcast` flag.
 * See `KIND_STREAM_MESSAGE_V2` in Buzz's `buzz-core/src/kind.rs`.
 */
@Immutable
class StreamMessageV2Event(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = content

    fun channel() = tags.channel()

    fun mentions() = tags.mentions()

    fun isBroadcast() = tags.isBroadcast()

    companion object {
        const val KIND = 40002

        fun build(
            channelId: String,
            content: String,
            mentions: List<HexKey> = emptyList(),
            broadcast: Boolean = false,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StreamMessageV2Event>.() -> Unit = {},
        ) = eventTemplate<StreamMessageV2Event>(KIND, content, createdAt) {
            channel(channelId)
            mentions(mentions)
            if (broadcast) broadcast()
            initializer()
        }
    }
}
