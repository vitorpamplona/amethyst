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

import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListItem
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.CommentsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ElementOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.NameTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SlugTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SubsetOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.TitleTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull

fun <T : DecentralizedListItem> TagArrayBuilder<T>.parentList(parent: DecentralizedListEvent) = addUniqueValueIfNew(ParentListTag.assemble(parent))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.parentList(parent: ParentList) = addUniqueValueIfNew(ParentListTag.assemble(parent))

/** Points at a list that was never formally declared, by its singular name (e.g. "dog"). */
fun <T : DecentralizedListItem> TagArrayBuilder<T>.parentListName(name: String) = addUniqueValueIfNew(ParentListTag.assemble(name))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.parentLists(parents: List<ParentList>) = addAllUniqueValueIfNew(ParentListTag.assemble(parents))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.name(name: String) = addUnique(NameTag.assemble(name))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.title(title: String) = addUnique(TitleTag.assemble(title))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.slug(slug: String) = addUnique(SlugTag.assemble(slug))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.comments(comments: String) = addUnique(CommentsTag.assemble(comments))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.itemPubKey(
    pubKey: HexKey,
    relayHint: NormalizedRelayUrl? = null,
) = add(PTag.assemble(pubKey, relayHint))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.itemEvent(
    eventId: HexKey,
    relayHint: NormalizedRelayUrl? = null,
    author: HexKey? = null,
) = add(
    // Pads the relay slot when only the author is known, so the author stays at index 3.
    arrayOfNotNull(ETag.TAG_NAME, eventId, relayHint?.url ?: author?.let { "" }, author),
)

/** A string item, written verbatim: unlike a hashtag it is not duplicated in lowercase. */
fun <T : DecentralizedListItem> TagArrayBuilder<T>.itemString(value: String) = add(HashtagTag.assemble(value))

fun <T : DecentralizedListItem> TagArrayBuilder<T>.itemAddress(
    address: Address,
    relayHint: NormalizedRelayUrl? = null,
) = add(ATag.assemble(address, relayHint))

// `n` and `s` are defined for kind 39999 only.

fun TagArrayBuilder<AddressableListItemEvent>.elementOf(parent: Address) = addUniqueValueIfNew(ElementOfTag.assemble(parent))

fun TagArrayBuilder<AddressableListItemEvent>.subsetOf(parent: Address) = addUniqueValueIfNew(SubsetOfTag.assemble(parent))
