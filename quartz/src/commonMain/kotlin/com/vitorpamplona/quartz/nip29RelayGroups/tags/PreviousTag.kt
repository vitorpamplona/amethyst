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
package com.vitorpamplona.quartz.nip29RelayGroups.tags

import com.vitorpamplona.quartz.nip01Core.core.has

/**
 * NIP-29 timeline references: a SINGLE `previous` tag carrying every referenced
 * event-id prefix (the first 8 hex chars) as its values:
 * ```
 * ["previous", "eb96c864", "2db75638", "b5d1065f"]
 * ```
 * relay29 only reads the first `previous` tag (`Tags.GetFirst`), so the old shape — one
 * tag per prefix — made the relay check just the first reference. Parsing still accepts
 * that legacy multi-tag form: [parse] returns every value of one tag, and callers read
 * all `previous` tags.
 */
class PreviousTag {
    companion object {
        const val TAG_NAME = "previous"

        /** Every non-empty prefix carried by one `previous` tag, or null when [tag] isn't one. */
        fun parse(tag: Array<String>): List<String>? {
            if (!tag.has(1) || tag[0] != TAG_NAME) return null
            val prefixes = ArrayList<String>(tag.size - 1)
            for (i in 1 until tag.size) {
                if (tag[i].isNotEmpty()) prefixes.add(tag[i])
            }
            return prefixes.ifEmpty { null }
        }

        /** One `previous` tag holding all [eventIdPrefixes], or null when there are none to send. */
        fun assemble(eventIdPrefixes: List<String>): Array<String>? {
            val values = eventIdPrefixes.filter { it.isNotEmpty() }
            if (values.isEmpty()) return null
            return arrayOf(TAG_NAME, *values.toTypedArray())
        }
    }
}
