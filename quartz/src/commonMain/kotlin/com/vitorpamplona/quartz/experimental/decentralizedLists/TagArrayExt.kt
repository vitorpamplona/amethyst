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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.concepts.WordWrapper
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.DescriptionTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritFromTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.InheritType
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.JsonTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray

fun TagArray.description() = firstNotNullOfOrNull(DescriptionTag::parse)

/** Every `b` link, in tag order. Excludes the `b-tag-deferred` marker. */
fun TagArray.inheritFrom() = mapNotNull(InheritFromTag::parse)

/**
 * The targets of `b` tags of exactly [type], in tag order. For [InheritType.INHERIT] the order
 * is load-bearing: the first-listed parent wins a field both ancestors state.
 */
fun TagArray.inheritFromTargets(type: InheritType) = mapNotNull { InheritFromTag.parseTarget(it, type) }

/** True when the event carries `["b", "b-tag-deferred"]`: deliberately affiliated with nothing. */
fun TagArray.isDeliberatelyUnaffiliated() = any(InheritFromTag::isUnaffiliatedMarker)

/** The raw `json` tag. */
fun TagArray.json() = firstNotNullOfOrNull(JsonTag::parse)

/** The `json` tag parsed as a JSON object, or null when absent or malformed. */
fun TagArray.wordWrapper() = json()?.let(WordWrapper::parse)
