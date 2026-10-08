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
package com.vitorpamplona.quartz.experimental.topEight

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

/**
 * The ranked people, rank 1 first: well-formed `p` tags in tag order, duplicates dropped ("first
 * occurrence wins") and cut at [TopEightEvent.MAX_ENTRIES] — clients "SHOULD ignore `p` tags past
 * the eighth when reading" so that a list of hundreds costs nothing.
 */
fun TagArray.topEight(): List<PTag> {
    val result = ArrayList<PTag>(TopEightEvent.MAX_ENTRIES)
    for (tag in this) {
        val person = PTag.parse(tag) ?: continue
        if (result.any { it.pubKey == person.pubKey }) continue
        result.add(person)
        if (result.size == TopEightEvent.MAX_ENTRIES) break
    }
    return result
}
