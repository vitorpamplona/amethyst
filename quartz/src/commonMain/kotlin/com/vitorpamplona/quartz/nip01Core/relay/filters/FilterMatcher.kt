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

/**
 * Whether one event satisfies one filter's NIP-01 fields.
 *
 * This runs once per event per candidate filter, which on a full-cache scan means tens of
 * thousands of times per keystroke — so it allocates nothing. That is the whole design
 * constraint here and the reason for the hand-rolled loops: `tags.forEach { it.toSet() }` built
 * a `Set` per event per tag key, `tagsAll` built one more plus a full tag walk, and the stdlib
 * `any` allocated an iterator over the tag array each time. All three are gone; the loops below
 * read the same data in place.
 *
 * The tag lists a filter carries are short — a handful of values per key — so a linear `contains`
 * over them beats hashing, and needs no allocation to do it.
 */
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
        if (ids != null && !ids.contains(event.id)) return false
        if (kinds != null && !kinds.contains(event.kind)) return false
        if (authors != null && !authors.contains(event.pubKey)) return false

        // AND between keys, OR between values: the event must carry at least one of each key's values.
        if (tags != null) {
            for ((name, values) in tags) {
                if (!hasAnyTagValue(event, name, values)) return false
            }
        }

        // AND between keys, AND between values: the event must carry every one of each key's values.
        if (tagsAll != null) {
            for ((name, values) in tagsAll) {
                for (i in values.indices) {
                    if (!hasTagValue(event, name, values[i])) return false
                }
            }
        }

        if (since != null && event.createdAt < since) return false
        if (until != null && event.createdAt > until) return false

        return true
    }

    /** Does the event carry `name` with any of `values`? Walks the tag array in place. */
    private fun hasAnyTagValue(
        event: Event,
        name: String,
        values: List<String>,
    ): Boolean {
        val tags = event.tags
        for (i in tags.indices) {
            val tag = tags[i]
            if (tag.size > 1 && tag[0] == name && values.contains(tag[1])) return true
        }
        return false
    }

    /** Does the event carry `name` with exactly `value`? */
    private fun hasTagValue(
        event: Event,
        name: String,
        value: String,
    ): Boolean {
        val tags = event.tags
        for (i in tags.indices) {
            val tag = tags[i]
            if (tag.size > 1 && tag[0] == name && tag[1] == value) return true
        }
        return false
    }
}
