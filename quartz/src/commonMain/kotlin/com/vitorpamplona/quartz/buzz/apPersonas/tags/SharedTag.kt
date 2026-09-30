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
package com.vitorpamplona.quartz.buzz.apPersonas.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * The catalog opt-in on Buzz's shared-gated kinds (the persona `kind:30175` and the team
 * catalog `kind:30178`): exactly `["shared","true"]`. Without it the relay serves the event
 * to its author only; with it, to the whole community. It is a tag rather than a content field
 * so toggling it leaves the content bytes (and the persona content hash) unchanged.
 *
 * The relay refuses any other `shared` shape at ingest (a different value, a third element, or
 * a second `shared` tag), and its read gate fails closed on them. Ground truth:
 * `event_is_shared` in Buzz's `buzz-core/src/kind.rs` and `validate_shared_tag` in
 * `buzz-relay/src/handlers/ingest.rs`.
 */
object SharedTag {
    const val TAG_NAME = "shared"
    const val VALUE = "true"

    fun assemble() = arrayOf(TAG_NAME, VALUE)

    /**
     * True only when [tags] carry exactly one `shared` tag and it is exactly `["shared","true"]`;
     * any malformed or duplicated `shared` tag reads as not shared, like the relay's read gate.
     */
    fun isShared(tags: TagArray): Boolean {
        var count = 0
        for (tag in tags) {
            if (tag.isEmpty() || tag[0] != TAG_NAME) continue
            if (tag.size != 2 || tag[1] != VALUE) return false
            count++
        }
        return count == 1
    }
}
