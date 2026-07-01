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
package com.vitorpamplona.quartz.nip89AppHandlers.definition

import com.vitorpamplona.quartz.nip89AppHandlers.definition.tags.SupportedNipTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupportedNipTagTest {
    @Test
    fun parsesNumericNip() {
        val tag = arrayOf("i", "https://github.com/nostr-protocol/nips/blob/master/01.md")
        val result = SupportedNipTag.parse(tag)

        assertEquals("01", result?.nip)
        assertEquals("https://github.com/nostr-protocol/nips/blob/master/01.md", result?.url)
    }

    @Test
    fun parsesHexNipAndUppercases() {
        val tag = arrayOf("i", "https://github.com/nostr-protocol/nips/blob/master/5A.md")
        assertEquals("5A", SupportedNipTag.parse(tag)?.nip)

        val lower = arrayOf("i", "https://github.com/nostr-protocol/nips/blob/master/7d.md")
        assertEquals("7D", SupportedNipTag.parse(lower)?.nip)
    }

    @Test
    fun ignoresNonNipIdentityClaim() {
        // A regular NIP-39 identity claim is also an `i` tag, but it is not a NIP spec url.
        val tag = arrayOf("i", "github:vitorpamplona", "https://gist.github.com/abc")
        assertNull(SupportedNipTag.parse(tag))
    }

    @Test
    fun ignoresUrlWithoutMdFile() {
        val tag = arrayOf("i", "https://github.com/nostr-protocol/nips")
        assertNull(SupportedNipTag.parse(tag))
    }

    @Test
    fun ignoresWrongTagName() {
        assertNull(SupportedNipTag.parse(arrayOf("t", "https://example.com/01.md")))
    }

    @Test
    fun roundTrips() {
        val original = SupportedNipTag("01", "https://github.com/nostr-protocol/nips/blob/master/01.md")
        val parsed = SupportedNipTag.parse(original.toTagArray())

        assertEquals(original.nip, parsed?.nip)
        assertEquals(original.url, parsed?.url)
    }
}
