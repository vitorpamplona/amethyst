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
package com.vitorpamplona.quartz.buzz.threading

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag

/**
 * Positional reader for Buzz's thread e-tags (`["e", id, "", "root"|"reply"]`).
 * [MarkedETag.parse] rejects the empty relay slot Buzz emits, so the marker is read
 * positionally: index 3 in the canonical 4-element form, tolerating the relay-less
 * 3-element form (`["e", id, "reply"]`) with the marker at index 2.
 */
private fun Array<String>.buzzMarkedEventId(marker: MarkedETag.MARKER): HexKey? {
    if (size < 3 || this[0] != MarkedETag.TAG_NAME || this[1].length != 64) return null
    val code = marker.code
    return if ((size >= 4 && this[3] == code) || this[2] == code) this[1] else null
}

/** The thread root this event replies under, from its marked `root` e-tag. */
fun TagArray.buzzThreadRoot(): HexKey? = firstNotNullOfOrNull { it.buzzMarkedEventId(MarkedETag.MARKER.ROOT) }

/** The direct parent this event replies to, from its marked `reply` e-tag. */
fun TagArray.buzzThreadReply(): HexKey? = firstNotNullOfOrNull { it.buzzMarkedEventId(MarkedETag.MARKER.REPLY) }
