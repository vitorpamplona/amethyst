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
package com.vitorpamplona.quartz.experimental.decentralizedLists.taggings

import com.vitorpamplona.quartz.experimental.decentralizedLists.item.parentListPointers
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.CurationMethodTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.Polarity
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags.PolarityTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * How a v1 reader counts this tagging. No `polarity` tag means apply; a tag whose value is
 * not a number is not counted, rather than guessed.
 */
fun TagArray.polarity(): Polarity {
    val tag = firstOrNull(PolarityTag::isTag) ?: return Polarity.APPLIED
    val value = PolarityTag.parseValue(tag) ?: return Polarity.UNCOUNTED
    return PolarityTag.bucket(value)
}

fun TagArray.curationMethod() = firstNotNullOfOrNull(CurationMethodTag::parse)

/** The applicability hints a tag-element's author recorded. Hints, never gates. */
fun TagArray.applicabilityHints() = parentListPointers().mapNotNull(TagApplicabilityHint::fromCode)
