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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * NIP-5A aggregate hash — a single content hash binding every file in a
 * static-website / napplet manifest, carried in the `["x", <hash>, "aggregate"]`
 * tag and verified by NIP-5D runtimes before executing a napplet.
 *
 * Algorithm (verbatim from NIP-5A):
 *  1. Collect every `path` tag.
 *  2. For each, produce a line `"<sha256hash> <absolute-path>\n"`.
 *  3. Sort all lines in ascending lexicographic order.
 *  4. Concatenate the sorted lines as UTF-8 bytes.
 *  5. SHA-256 the concatenation.
 *
 * Lines are sorted by Kotlin's natural `String` order (UTF-16 code units), which
 * matches a JavaScript `Array.prototype.sort()` reference implementation. In
 * practice each line is prefixed by its unique 64-char hex hash, so ordering is
 * decided by the hash and the path encoding only ever breaks ties between two
 * paths sharing one blob.
 */
object SiteAggregateHash {
    /** Recomputes the aggregate hash hex from a manifest's [paths]. */
    fun compute(paths: List<PathTag>): HexKey {
        val body =
            paths
                .map { "${it.hash} ${it.path}\n" }
                .sorted()
                .joinToString("")
        return sha256(body.encodeToByteArray()).toHexKey()
    }

    /**
     * Verifies a declared aggregate hash against the one recomputed from [paths].
     * Returns `true` when [declared] is null (nothing to check) — per NIP-5D,
     * the `x` tag is only enforced when present.
     */
    fun verify(
        paths: List<PathTag>,
        declared: HexKey?,
    ): Boolean = declared == null || declared.equals(compute(paths), ignoreCase = true)
}
