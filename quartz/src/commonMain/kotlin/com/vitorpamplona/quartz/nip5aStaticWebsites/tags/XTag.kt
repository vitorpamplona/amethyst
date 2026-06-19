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
package com.vitorpamplona.quartz.nip5aStaticWebsites.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The NIP-5A aggregate-hash tag: `["x", "<sha256-hex>", "aggregate"]`.
 *
 * It pins the whole site/napplet manifest to a single content hash computed over
 * all `path` tags (see [com.vitorpamplona.quartz.nip5aStaticWebsites.SiteAggregateHash]).
 * The `"aggregate"` marker in position 2 distinguishes it from other `x` tags
 * (e.g. NIP-94's bare `["x", "<hash>"]`).
 */
class XTag {
    companion object {
        const val TAG_NAME = "x"
        const val AGGREGATE_MARKER = "aggregate"

        /** Returns the aggregate hash hex when [tag] is a well-formed aggregate `x` tag, else null. */
        fun parse(tag: Array<String>): HexKey? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2] == AGGREGATE_MARKER) { return null }
            return tag[1]
        }

        fun assemble(aggregateHash: HexKey) = arrayOf(TAG_NAME, aggregateHash, AGGREGATE_MARKER)
    }
}
