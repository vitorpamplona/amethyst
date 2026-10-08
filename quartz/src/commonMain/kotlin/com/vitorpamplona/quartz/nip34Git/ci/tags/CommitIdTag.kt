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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * Nostr CI `c` tag: a **git object id**, not NIP-22's lowercase-`c`-anything. The first `c` is
 * the commit the workflow ran against; a second one, when the trigger was an annotated tag, is
 * that tag object's id, so one `#c` filter finds the run by either.
 *
 * Only SHA-1 (40) or SHA-256 (64) hex ids parse, as ngit validates them: anything else cannot
 * name a git object, and a commit id is never a graph edge.
 */
class CommitIdTag {
    companion object {
        const val TAG_NAME = "c"

        fun isObjectId(value: String) = (value.length == 40 || value.length == 64) && Hex.isHex(value)

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && isObjectId(tag[1])

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isObjectId(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(objectId: String) = arrayOf(TAG_NAME, objectId)
    }
}
