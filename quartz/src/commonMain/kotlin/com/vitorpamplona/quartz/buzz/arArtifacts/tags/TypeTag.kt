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
package com.vitorpamplona.quartz.buzz.arArtifacts.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-AR `type` tag — the artifact's immutable, namespaced content type (e.g.
 * `buzz.task`, `buzz.project`). `buzz.*` is reserved for published Buzz client contracts.
 * Ground truth: `validate` in Buzz's `buzz-core/src/artifact.rs`.
 */
object TypeTag {
    const val TAG_NAME = "type"
    const val MAX_BYTES = 128

    fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME

    fun parse(tag: Tag): String? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return tag[1]
    }

    fun assemble(type: String) = arrayOf(TAG_NAME, type)

    /**
     * The type grammar: at most [MAX_BYTES] bytes, at least one `.`, and every dot-separated
     * component non-empty, starting with `a-z`, and otherwise only `a-z`, `0-9`, `_` or `-`.
     */
    fun isValid(type: String): Boolean {
        if (type.length > MAX_BYTES || '.' !in type) return false
        var componentStart = true
        for (c in type) {
            if (c == '.') {
                if (componentStart) return false
                componentStart = true
            } else if (componentStart) {
                if (c !in 'a'..'z') return false
                componentStart = false
            } else if (c !in 'a'..'z' && c !in '0'..'9' && c != '_' && c != '-') {
                return false
            }
        }
        return !componentStart
    }
}
