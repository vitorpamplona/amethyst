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
package com.vitorpamplona.quartz.experimental.decentralizedLists.item

import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.props.ItemProps
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

/**
 * The tags every Decentralized Lists item (9999, 39999) shares.
 *
 * - `z` names the list the item is on: a header's id, its coordinate, or the bare name of an
 *   undeclared list ([ParentListTag.classify]), so [Relation.PARENT_LIST] takes all three.
 * - `p` / `e` / `a` / `t` are the item itself. The `t` here is a list VALUE ("Switzerland"),
 *   case preserved, not a hashtag; an `a` may also be written as an `naddr1…`.
 *
 * [itemProps] qualifies every [Relation.ITEM] (a tagging's polarity).
 */
internal fun LinkBuilder.listItemLinks(
    tags: TagArray,
    itemProps: ItemProps? = null,
) {
    each(tags, ParentListTag::parse) { parent ->
        when (parent) {
            is ParentList.EventId -> event(Relation.PARENT_LIST, parent.eventId, ParentListTag.TAG_NAME)
            is ParentList.Coordinate -> address(Relation.PARENT_LIST, parent.address, ParentListTag.TAG_NAME)
            is ParentList.Name -> tag(Relation.PARENT_LIST, ParentListTag.TAG_NAME, parent.name)
        }
    }
    each(tags, PTag::parse) { user(Relation.ITEM, it, PTag.TAG_NAME, itemProps) }
    each(tags, ETag::parse) { event(Relation.ITEM, it, ETag.TAG_NAME, itemProps) }
    each(tags, ATag::parse) { address(Relation.ITEM, it, ATag.TAG_NAME, itemProps) }
    // HashtagTag::parse keeps the case, which a list value needs; hashtags() would lowercase it.
    each(tags, HashtagTag::parse) { tag(Relation.ITEM, HashtagTag.TAG_NAME, it, HashtagTag.TAG_NAME, itemProps) }
}
