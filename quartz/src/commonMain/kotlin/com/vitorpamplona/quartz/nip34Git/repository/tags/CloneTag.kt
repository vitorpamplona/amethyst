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
package com.vitorpamplona.quartz.nip34Git.repository.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-34 repository `clone` tag. The spec encodes clone URLs as a single
 * multi-value tag — `["clone", "<url1>", "<url2>", ...]` — so [assemble] emits
 * that form and [parseAll] reads every value. [parse] (first value only) and
 * the legacy repeated `["clone", "<url>"]` form are still read for backward
 * compatibility (see `GitRepositoryEvent.clones`).
 */
class CloneTag {
    companion object {
        const val TAG_NAME = "clone"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        /** Every non-empty URL carried by a single `clone` tag. */
        fun parseAll(tag: Array<String>): List<String> {
            ensure(tag.has(1)) { return emptyList() }
            ensure(tag[0] == TAG_NAME) { return emptyList() }
            return tag.drop(1).filter { it.isNotEmpty() }
        }

        fun assemble(name: String) = arrayOf(TAG_NAME, name)

        /** The spec form: one tag carrying all clone URLs. */
        fun assemble(urls: List<String>): Array<String> = (listOf(TAG_NAME) + urls.filter { it.isNotEmpty() }).toTypedArray()
    }
}
