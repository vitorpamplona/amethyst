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
package com.vitorpamplona.quartz.buzz.presence.typing

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag

/** The channel UUID the typing indicator is scoped to — the `h` tag. */
fun TagArray.typingChannel() = firstNotNullOfOrNull(GroupIdTag::parse)

/** The root event id of the thread being typed in, if a `["e", id, "", "root"]` tag is present. */
fun TagArray.typingThreadRoot(): String? = typingThreadMarker(MarkedETag.MARKER.ROOT)

/** The parent (reply) event id being typed in, if a `["e", id, "", "reply"]` tag is present. */
fun TagArray.typingThreadReply(): String? = typingThreadMarker(MarkedETag.MARKER.REPLY)

private fun TagArray.typingThreadMarker(marker: MarkedETag.MARKER): String? =
    firstNotNullOfOrNull { tag ->
        MarkedETag.parseAllThreadTags(tag)?.takeIf { it.marker == marker }?.eventId
    }
