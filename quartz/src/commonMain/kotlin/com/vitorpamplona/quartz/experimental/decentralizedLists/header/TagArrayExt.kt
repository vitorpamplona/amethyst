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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ConceptGraphTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.ItemKindTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.NamesTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.SlugsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleType
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TitlesTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull

fun TagArray.names() = fastFirstNotNullOfOrNull(NamesTag::parse)

fun TagArray.titles() = fastFirstNotNullOfOrNull(TitlesTag::parse)

fun TagArray.slugs() = fastFirstNotNullOfOrNull(SlugsTag::parse)

/** Every `required` / `allowed` / `recommended` / `disallowed` rule, in tag order. */
fun TagArray.tagRules() = mapNotNull(TagRuleTag::parse)

fun TagArray.tagRuleNames(type: TagRuleType) = mapNotNull { TagRuleTag.parseTagName(it, type) }

fun TagArray.requiredTags() = tagRuleNames(TagRuleType.REQUIRED)

fun TagArray.allowedTags() = tagRuleNames(TagRuleType.ALLOWED)

fun TagArray.recommendedTags() = tagRuleNames(TagRuleType.RECOMMENDED)

fun TagArray.disallowedTags() = tagRuleNames(TagRuleType.DISALLOWED)

/**
 * True when the tags carry a list declaration. Standard headers (9998/39998) must; an item
 * (9999/39999) that does is using the spec's "nonstandard" method to declare a list.
 */
fun TagArray.declaresList() = fastAny(NamesTag::isTag)

/** The `concept-graph` pointer when present. See [ConceptGraphTag.compute] for the fallback. */
fun TagArray.conceptGraph() = fastFirstNotNullOfOrNull(ConceptGraphTag::parse)

/** The `item-kind` declarations, in tag order. */
fun TagArray.itemKinds() = mapNotNull(ItemKindTag::parse)

/** The kinds to query for this list's items: the declared `item-kind`s, else 9999 and 39999. */
fun TagArray.acceptedItemKinds(): List<Int> = itemKinds().map { it.kind }.distinct().ifEmpty { STANDARD_ITEM_KINDS }

val STANDARD_ITEM_KINDS = listOf(9999, 39999)
