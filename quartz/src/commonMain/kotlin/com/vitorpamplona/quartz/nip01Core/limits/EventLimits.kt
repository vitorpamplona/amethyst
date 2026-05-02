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
package com.vitorpamplona.quartz.nip01Core.limits

import com.vitorpamplona.quartz.nip01Core.core.TagArray

// Defense-in-depth caps on relay-supplied event payloads.
// A hostile relay can otherwise OOM the client with one giant event or a flood of large tags
// (security review 2026-04-24 §2.3). Numbers are conservative: well above any legitimate
// real-world event seen in production, well below sizes that would meaningfully impact memory.
object EventLimits {
    const val MAX_TAG_COUNT = 2_000

    // Sized to accommodate observed real-world events (max ~104 inner elements in Vitor's
    // startup-data fixture) with ~2.5x headroom; still small enough that an attacker can't
    // amplify a single tag into a meaningful allocation.
    const val MAX_TAG_ELEMENTS_PER_TAG = 256
    const val MAX_TAG_VALUE_LENGTH = 16 * 1024
    const val MAX_CONTENT_LENGTH = 64 * 1024
    const val MAX_RELAY_MESSAGE_LENGTH = 256 * 1024

    fun validateContent(content: String) {
        require(content.length <= MAX_CONTENT_LENGTH) {
            "Event content length ${content.length} exceeds max $MAX_CONTENT_LENGTH"
        }
    }

    // Used by callers that have a TagArray in hand (e.g. constructed without going through
    // a tag deserializer). The streaming Jackson and kotlinx tag deserializers already
    // enforce these caps inline during parse, so the deserializer hot path does not call
    // this helper.
    fun validateTags(tags: TagArray) {
        require(tags.size <= MAX_TAG_COUNT) {
            "Event has ${tags.size} tags; max is $MAX_TAG_COUNT"
        }
        for (tag in tags) {
            require(tag.size <= MAX_TAG_ELEMENTS_PER_TAG) {
                "Tag has ${tag.size} elements; max is $MAX_TAG_ELEMENTS_PER_TAG"
            }
            for (value in tag) {
                require(value.length <= MAX_TAG_VALUE_LENGTH) {
                    "Tag value length ${value.length} exceeds max $MAX_TAG_VALUE_LENGTH"
                }
            }
        }
    }
}
