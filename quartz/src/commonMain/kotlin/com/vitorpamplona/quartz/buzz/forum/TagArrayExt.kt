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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The `h` channel UUID this forum event belongs to. */
fun TagArray.forumChannel(): String? = firstTagValue(GroupIdTag.TAG_NAME)

/** The `p` mentions carried by a forum post/comment. */
fun TagArray.forumMentions(): List<HexKey> = mapNotNull(PTag::parseKey)

/** The `e` target event of a forum vote (`kind:45002`). */
fun TagArray.forumVoteTarget(): HexKey? = firstNotNullOfOrNull(ETag::parseId)

// Reads the NIP-10 marker positionally: the Buzz wire form keeps an explicit empty
// relay slot (`["e", id, "", "reply"]`), so the marker sits at index 3. A relay-less
// 3-element form (`["e", id, "reply"]`) is tolerated with the marker at index 2.
private fun Array<String>.markedEventId(marker: MarkedETag.MARKER): HexKey? {
    if (size < 3 || this[0] != MarkedETag.TAG_NAME || this[1].length != 64) return null
    val code = marker.code
    return if ((size >= 4 && this[3] == code) || this[2] == code) this[1] else null
}

/** The NIP-10 `root`-marked thread event id of a forum comment (null for a direct reply). */
fun TagArray.forumThreadRoot(): HexKey? = firstNotNullOfOrNull { it.markedEventId(MarkedETag.MARKER.ROOT) }

/** The NIP-10 `reply`-marked immediate-parent event id of a forum comment. */
fun TagArray.forumThreadReply(): HexKey? = firstNotNullOfOrNull { it.markedEventId(MarkedETag.MARKER.REPLY) }
