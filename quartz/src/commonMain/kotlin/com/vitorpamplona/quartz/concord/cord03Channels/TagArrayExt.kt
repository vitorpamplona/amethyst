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

import com.vitorpamplona.quartz.concord.cord03Channels.tags.ChannelTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * The value of the one tag named [name], or null when it is absent **or appears more than once**
 * (a binding must be unambiguous — the reference client's `uniqueTag`). Every tag whose name
 * matches counts toward the duplicate check, even one too short to carry a value.
 */
private fun TagArray.uniqueTagValue(name: String): String? {
    var found: String? = null
    var count = 0
    for (tag in this) {
        if (tag.isEmpty() || tag[0] != name) continue
        count++
        if (count > 1) return null
        found = tag.getOrNull(1)
    }
    return found
}

/** The channel id this Chat Plane rumor is bound to, or null if unbound or ambiguous (duplicated). */
fun TagArray.concordChannel(): HexKey? = uniqueTagValue(ChannelTag.TAG_NAME)?.takeIf { it.isNotEmpty() }

/**
 * The epoch this Chat Plane rumor is bound to, or null if unbound, ambiguous (duplicated), or not
 * in canonical decimal form ([EpochTag.parse]).
 */
fun TagArray.concordEpoch(): Long? = uniqueTagValue(EpochTag.TAG_NAME)?.let { EpochTag.parse(arrayOf(EpochTag.TAG_NAME, it)) }

/**
 * True when these tags bind to exactly [channelId] and [epoch] (CORD-03 §3): exactly one
 * `channel` tag strict-equal to [channelId], and exactly one `epoch` tag strict-equal to the
 * canonical decimal of [epoch] (`"04"` or `"+4"` never match 4). Recipients must reject any Chat
 * Plane event whose binding does not match the plane it arrived on.
 */
fun TagArray.isConcordBoundTo(
    channelId: HexKey,
    epoch: Long,
): Boolean = uniqueTagValue(ChannelTag.TAG_NAME) == channelId && uniqueTagValue(EpochTag.TAG_NAME) == epoch.toString()
