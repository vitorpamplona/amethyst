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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import com.vitorpamplona.quartz.nip01Core.core.Event

object FilterMatcher {
    fun match(
        event: Event,
        ids: List<String>? = null,
        authors: List<String>? = null,
        kinds: List<Int>? = null,
        tags: Map<String, List<String>>? = null,
        tagsAll: Map<String, List<String>>? = null,
        since: Long? = null,
        until: Long? = null,
    ): Boolean {
        if (ids?.contains(event.id) == false) return false
        if (kinds?.contains(event.kind) == false) return false
        if (authors?.contains(event.pubKey) == false) return false
        tags?.forEach { tag ->
            val valueSet = tag.value.toSet()
            // AND between keys, OR between values
            if (!event.tags.any { it.size > 1 && it[0] == tag.key && it[1] in valueSet }) return false
        }
        tagsAll?.forEach { tag ->
            val eventTagValueSet =
                event.tags.mapNotNullTo(mutableSetOf()) {
                    if (it.size > 1 && it[0] == tag.key) {
                        it[1]
                    } else {
                        null
                    }
                }
            // AND between keys, AND between values
            for (tagValue in tag.value) {
                if (tagValue !in eventTagValueSet) return false
            }
        }
        if (event.createdAt !in (since ?: Long.MIN_VALUE)..(until ?: Long.MAX_VALUE)) {
            return false
        }
        return true
    }
}
