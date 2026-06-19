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

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteAggregateHashTest {
    private val h1 = "11".repeat(32)
    private val h2 = "22".repeat(32)

    @Test
    fun matchesIndependentlyComputedVector() {
        // Pinned with `printf '<h1> /index.html\n<h2> /app.js\n' | sha256sum` (h1 < h2, so sorted order).
        val paths = listOf(PathTag("/index.html", h1), PathTag("/app.js", h2))
        assertEquals(
            "2c1250d51fba528f4d8c3c98522ecd844f6dcd94bcf3ecc90f3219bbc4a23224",
            SiteAggregateHash.compute(paths),
        )
    }

    @Test
    fun isIndependentOfInputOrder() {
        val forward = SiteAggregateHash.compute(listOf(PathTag("/index.html", h1), PathTag("/app.js", h2)))
        val reversed = SiteAggregateHash.compute(listOf(PathTag("/app.js", h2), PathTag("/index.html", h1)))
        assertEquals(forward, reversed)
    }

    @Test
    fun verifyAcceptsNullAndMatchAndRejectsTamper() {
        val paths = listOf(PathTag("/index.html", h1), PathTag("/app.js", h2))
        val aggregate = SiteAggregateHash.compute(paths)

        // No x tag declared -> nothing to enforce.
        assertTrue(SiteAggregateHash.verify(paths, null))
        // Declared matches, case-insensitively.
        assertTrue(SiteAggregateHash.verify(paths, aggregate))
        assertTrue(SiteAggregateHash.verify(paths, aggregate.uppercase()))
        // Declared aggregate that doesn't match the paths is rejected.
        assertFalse(SiteAggregateHash.verify(paths, h1))
        // Tampering a single path hash changes the recomputed aggregate -> mismatch.
        val tampered = listOf(PathTag("/index.html", h2), PathTag("/app.js", h2))
        assertFalse(SiteAggregateHash.verify(tampered, aggregate))
    }
}
