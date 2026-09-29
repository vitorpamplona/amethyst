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
package com.vitorpamplona.quartz.concord.cord03Channels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.links.LinkFree

/**
 * A Concord **timer notice** (CORD-08 §4, `kind:1740`): the inline "Alice set disappearing messages
 * to 30 days" line an actor posts into each channel after changing the community's timer. Empty
 * content, the channel binding, and one `["timer", "<seconds>"]` tag (`0` = turned off).
 *
 * Informational only — the folded metadata is the authority — and believed only when its author
 * holds MANAGE_METADATA in the fold; readers drop it otherwise. A notice never expires (§2).
 */
@Immutable
class ConcordTimerNoticeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    // Its tags are the channel binding, which no Concord channel message links, and the timer
    // value: nothing to link.
    LinkFree {
    /** The announced timer in seconds (`0` = off), or null when the tag is missing or malformed. */
    fun timerSecs(): Long? = ConcordDisappearing.noticeTimerSecs(this)

    companion object {
        const val KIND = ChannelChat.KIND_TIMER_NOTICE
    }
}
