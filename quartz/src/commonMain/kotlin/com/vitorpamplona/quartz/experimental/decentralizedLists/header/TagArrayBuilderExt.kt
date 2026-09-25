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

import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.NamesTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.SlugsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRule
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleType
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TitlesTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

// Typed on the whole family, not just the header kinds: the spec's nonstandard method
// declares a list with a 9999/39999 item, which then carries these same tags.

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.names(
    singular: String,
    plural: String,
) = addUnique(NamesTag.assemble(singular, plural))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.titles(
    singular: String,
    plural: String,
) = addUnique(TitlesTag.assemble(singular, plural))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.slugs(
    singular: String,
    plural: String,
) = addUnique(SlugsTag.assemble(singular, plural))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.tagRule(rule: TagRule) = add(TagRuleTag.assemble(rule))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.tagRules(rules: List<TagRule>) = addAll(TagRuleTag.assemble(rules))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.required(
    tagName: String,
    description: String? = null,
) = add(TagRuleTag.assemble(TagRuleType.REQUIRED, tagName, description))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.allowed(
    tagName: String,
    description: String? = null,
) = add(TagRuleTag.assemble(TagRuleType.ALLOWED, tagName, description))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.recommended(
    tagName: String,
    description: String? = null,
) = add(TagRuleTag.assemble(TagRuleType.RECOMMENDED, tagName, description))

fun <T : DecentralizedListEvent> TagArrayBuilder<T>.disallowed(
    tagName: String,
    description: String? = null,
) = add(TagRuleTag.assemble(TagRuleType.DISALLOWED, tagName, description))
