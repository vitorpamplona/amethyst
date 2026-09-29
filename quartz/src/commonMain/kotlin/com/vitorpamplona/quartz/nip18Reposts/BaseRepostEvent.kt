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
package com.vitorpamplona.quartz.nip18Reposts

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

interface BaseRepostEvent {
    fun boostedEventId(): HexKey?

    fun boostedAddress(): Address?

    /** The kind of the reposted (boosted) event, as declared in the `k` tag. */
    fun boostedKind(): Int?
}

/**
 * NIP-18 links of kinds 6 and 16. The reposted event is the LAST `e` (and, for an addressable
 * one, the last `a`), as [BaseRepostEvent.boostedEventId] reads it, and its author the last `p`.
 * Any earlier `e`/`a`/`p` is not part of the repost and is a `MENTION`; `k` is the reposted kind.
 * The reposted event's JSON in the content is the same event as the `e`, not another link.
 */
internal fun repostLinks(tags: TagArray): List<Link<*>> =
    links {
        val eventId = tags.lastNotNullOfOrNull(ETag::parseId)
        val address = tags.lastNotNullOfOrNull(ATag::parseAddressId)
        val author = tags.lastNotNullOfOrNull(PTag::parseKey)

        event(Relation.REPOSTED, eventId, "e")
        address(Relation.REPOSTED, address, "a")
        user(Relation.REPOSTED_AUTHOR, author, "p")

        tags.fastForEach { tag ->
            if (tag.size < 2) return@fastForEach
            when (tag[0]) {
                "e" -> if (tag[1] != eventId) event(Relation.MENTION, tag[1], "e")
                "a" -> if (tag[1] != address) address(Relation.MENTION, tag[1], "a")
                "p" -> if (tag[1] != author) user(Relation.MENTION, tag[1], "p")
                "k" -> tag(Relation.TAG, "k", tag[1])
            }
        }
    }
