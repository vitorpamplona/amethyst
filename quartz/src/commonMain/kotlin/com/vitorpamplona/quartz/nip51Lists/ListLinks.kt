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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps

// The shapes NIP-51 lists share. Only public tags are read: private (NIP-44 encrypted) entries
// are invisible to anyone but the owner, so they never become links.

/** The `e` and `a` items of a bookmark-like list, in tag order, as [relation]. */
internal fun <P : LinkProps> LinkBuilder.eventsAndAddresses(
    relation: Relation<P>,
    tags: TagArray,
) = tags.fastForEach { tag ->
    if (tag.size < 2) return@fastForEach
    when (tag[0]) {
        "e" -> event(relation, tag[1], "e")
        "a" -> address(relation, tag[1], "a")
    }
}

/**
 * A mute list's entries, each a `MUTE`: people (`p`), threads (`e`), hashtags (`t`, the same
 * lowercased node `HASHTAG` uses) and words (`word`, lowercase as NIP-51 writes them).
 */
internal fun LinkBuilder.mutes(tags: TagArray) =
    tags.fastForEach { tag ->
        if (tag.size < 2) return@fastForEach
        when (tag[0]) {
            "p" -> user(Relation.MUTE, tag[1], "p")
            "e" -> event(Relation.MUTE, tag[1], "e")
            "t" -> tag(Relation.MUTE, "t", tag[1].lowercase())
            "word" -> tag(Relation.MUTE, "word", tag[1].lowercase())
        }
    }
