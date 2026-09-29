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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.each
import com.vitorpamplona.quartz.nip01Core.links.props.OrderProps
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ChildTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ParentTag

/** The `h` group a user-signed NIP-29 event is scoped to ([GroupIdTag]) → [Relation.GROUP]. */
fun LinkBuilder.groups(tags: TagArray) = each(tags, GroupIdTag::parse) { tag(Relation.GROUP, GroupIdTag.TAG_NAME, it) }

/**
 * NIP-29 subgroups: [ParentTag] → [Relation.PARENT], each [ChildTag] → [Relation.CHILD]. Both
 * hold a group id, so the target is the same `h` node the group's own events scope to; `via`
 * keeps which tag said it.
 */
fun LinkBuilder.subgroups(tags: TagArray) {
    each(tags, ParentTag::parse) { tag(Relation.PARENT, GroupIdTag.TAG_NAME, it, ParentTag.TAG_NAME) }
    each(tags, ChildTag::parse) { tag(Relation.CHILD, GroupIdTag.TAG_NAME, it, ChildTag.TAG_NAME) }
}

/**
 * A NIP-29 pin list (the 9010 request and the relay's 39005): `e` and `a` pins interleaved in
 * display order. A graph keeps no edge order, so each [Relation.PIN] carries its position in
 * [pins] as `order`.
 */
fun LinkBuilder.groupPinLinks(pins: List<GroupPin>) =
    pins.forEachIndexed { index, pin ->
        val order = OrderProps(index)
        when (pin) {
            is EventPin -> event(Relation.PIN, pin.eventId, EventPin.TAG_NAME, order)
            is AddressPin -> address(Relation.PIN, pin.address, AddressPin.TAG_NAME, order)
        }
    }
