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
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag

/**
 * The tags every Decentralized Lists item (9999, 39999) shares, one tag at a time; returns
 * false when [entry] is not one of them.
 *
 * - `z` names the list the item is on: a header's id, its coordinate, or the bare name of an
 *   undeclared list ([ParentListTag.classify]), so [Relation.PARENT_LIST] takes all three.
 * - `p` / `e` / `a` / `t` are the item itself. The `t` here is a list VALUE ("Switzerland"),
 *   case preserved, not a hashtag; an `a` may also be written as an `naddr1…`.
 *
 * [itemProps] qualifies every [Relation.ITEM] (a tagging's polarity).
 */
internal fun LinkBuilder.listItemTag(
    entry: Array<String>,
    itemProps: Map<String, Any>? = null,
): Boolean {
    if (entry.size < 2) return false
    when (entry[0]) {
        ParentListTag.TAG_NAME -> {
            when (val parent = ParentListTag.parse(entry)) {
                is ParentList.EventId -> event(Relation.PARENT_LIST, parent.eventId, ParentListTag.TAG_NAME)
                is ParentList.Coordinate -> address(Relation.PARENT_LIST, parent.address, ParentListTag.TAG_NAME)
                is ParentList.Name -> tag(Relation.PARENT_LIST, ParentListTag.TAG_NAME, parent.name)
                null -> Unit
            }
        }
        "p" -> user(Relation.ITEM, entry[1], "p", itemProps)
        "e" -> event(Relation.ITEM, entry[1], "e", itemProps)
        "a" -> address(Relation.ITEM, ATag.parseAddress(entry), "a", itemProps)
        "t" -> tag(Relation.ITEM, "t", entry[1], "t", itemProps)
        else -> return false
    }
    return true
}
