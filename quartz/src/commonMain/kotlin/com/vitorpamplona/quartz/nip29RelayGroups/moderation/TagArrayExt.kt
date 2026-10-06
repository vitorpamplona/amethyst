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
package com.vitorpamplona.quartz.nip29RelayGroups.moderation

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ChildTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.CodeTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ParentTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.PreviousTag
import com.vitorpamplona.quartz.utils.Hex

fun TagArray.groupId() = firstTagValue(GroupIdTag.TAG_NAME)

/**
 * Every `previous` reference prefix, across ALL `previous` tags: the spec's single
 * multi-value tag as well as the legacy one-tag-per-prefix form.
 */
fun TagArray.previousEvents(): List<String> {
    val result = ArrayList<String>()
    fastForEach { tag -> PreviousTag.parse(tag)?.let { result.addAll(it) } }
    return result
}

/** The `parent` group id (subgroups), or null when this is a root group. At most one is expected. */
fun TagArray.parentGroupId() = firstNotNullOfOrNull(ParentTag::parse)

/** The ordered list of direct `child` subgroup ids advertised on a parent's metadata. */
fun TagArray.childGroupIds(): List<String> = mapNotNull(ChildTag::parse)

// PTag only checks the key's length; the users a moderation event names must be real keys.
fun TagArray.userPubKeys(): List<HexKey> = mapNotNull { tag -> PTag.parseKey(tag)?.takeIf(Hex::isHex64) }

fun TagArray.deletedEventIds(): List<HexKey> = mapValueTagged("e") { it.takeIf { value -> value.isValid() } }

/** The ordered pin list: `e` (event id) and `a` (address) references, interleaved as sent. */
fun TagArray.groupPins(): List<GroupPin> = mapNotNull(GroupPin::parse)

/** Just the `e`-tagged pinned event ids, in order. Use [groupPins] to also see `a` pins. */
fun TagArray.pinnedEventIds(): List<HexKey> = mapNotNull { EventPin.parse(it)?.eventId }

/** Just the `a`-tagged pinned addresses, in order. */
fun TagArray.pinnedAddresses(): List<Address> = mapNotNull { AddressPin.parse(it)?.address }

/** The `e` pins that carry a relay hint in their extras. */
fun TagArray.pinnedEventHints(): List<EventIdHint> = mapNotNull(ETag::parseAsHint)

/** The `a` pins as `kind:pubkey:d` address ids, in order. */
fun TagArray.pinnedAddressIds(): List<String> = mapNotNull { AddressPin.parse(it)?.ref }

/** The `a` pins that carry a relay hint in their extras. */
fun TagArray.pinnedAddressHints(): List<AddressHint> = mapNotNull(ATag::parseAsHint)

fun TagArray.inviteCode() = firstNotNullOfOrNull(CodeTag::parse)
