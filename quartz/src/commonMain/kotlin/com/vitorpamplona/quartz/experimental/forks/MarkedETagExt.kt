/**
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
package com.vitorpamplona.quartz.experimental.forks

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag.MARKER

fun MarkedETag.Companion.parseFork(tag: Array<String>): MarkedETag? {
    if (tag.size < 4 || tag[0] != "e") return null
    if (tag[ORDER_MARKER] != MARKER.FORK.code) return null
    // ["e", id hex, relay hint, marker, pubkey]
    return MarkedETag(
        tag[ORDER_EVT_ID],
        tag[ORDER_RELAY].ifBlank { null }?.let { RelayUrlNormalizer.normalizeOrNull(it) },
        MARKER.FORK,
        tag.getOrNull(
            ORDER_PUBKEY,
        ),
    )
}

fun MarkedETag.Companion.parseForkedEventId(tag: Array<String>): HexKey? {
    if (tag.size < 4 || tag[0] != "e") return null
    if (tag[ORDER_MARKER] != MARKER.FORK.code) return null
    // ["e", id hex, relay hint, marker, pubkey]
    return tag[ORDER_EVT_ID]
}
