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

import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.CommentsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ElementOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.NameTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SlugTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SubsetOfTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.TitleTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

/** Every list this item belongs to. One `z` tag per list; an item may sit on several. */
fun TagArray.parentLists() = mapNotNull(ParentListTag::parse)

/** The raw `z` values, as they would go into a `#z` filter. */
fun TagArray.parentListPointers() = mapNotNull(ParentListTag::parseValue)

fun TagArray.name() = firstNotNullOfOrNull(NameTag::parse)

fun TagArray.title() = firstNotNullOfOrNull(TitleTag::parse)

fun TagArray.slug() = firstNotNullOfOrNull(SlugTag::parse)

fun TagArray.comments() = firstNotNullOfOrNull(CommentsTag::parse)

/** Pubkeys declared as items (`p`). */
fun TagArray.itemPubKeys() = mapNotNull(PTag::parse)

/** Events declared as items (`e`). */
fun TagArray.itemEvents() = mapNotNull(ETag::parse)

/**
 * Strings declared as items (`t`), with their case preserved. These are list values like
 * "Switzerland" or "Fido", not hashtags, so they are not lowercased the way hashtag readers do.
 */
fun TagArray.itemStrings() = mapNotNull(HashtagTag::parse)

/** Addressable events declared as items (`a`). Accepts both `kind:pubkey:d` and `naddr1…`. */
fun TagArray.itemAddresses() = mapNotNull(ATag::parse)

/** The sets/supersets this item claims to be an element of (`n`). */
fun TagArray.elementOf() = mapNotNull(ElementOfTag::parse)

/** The supersets this set claims to be a subset of (`s`). */
fun TagArray.subsetOf() = mapNotNull(SubsetOfTag::parse)
