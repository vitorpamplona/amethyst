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
package com.vitorpamplona.quartz.nip84Highlights.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextFragmentParserTest {
    @Test
    fun returnsNullWhenNoFragment() {
        assertNull(TextFragmentParser.parse("https://example.com/post"))
    }

    @Test
    fun returnsNullForPlainElementFragment() {
        assertNull(TextFragmentParser.parse("https://example.com/post#section-2"))
    }

    @Test
    fun parsesStartOnly() {
        val fragment = TextFragmentParser.parse("https://example.com/post#:~:text=hello%20world")
        assertEquals("hello world", fragment?.start)
        assertNull(fragment?.prefix)
        assertNull(fragment?.suffix)
        assertNull(fragment?.end)
    }

    @Test
    fun parsesPrefixStartSuffix() {
        val fragment =
            TextFragmentParser.parse(
                "https://example.com/post#:~:text=the%20-,highlighted%20passage,-follows%20on",
            )
        assertEquals("the ", fragment?.prefix)
        assertEquals("highlighted passage", fragment?.start)
        assertEquals("follows on", fragment?.suffix)
    }

    @Test
    fun parsesStartAndEndRange() {
        val fragment =
            TextFragmentParser.parse("https://example.com/post#:~:text=start%20of,end%20of")
        assertEquals("start of", fragment?.start)
        assertEquals("end of", fragment?.end)
    }

    @Test
    fun decodesEncodedCommaWithinText() {
        // A comma that belongs to the passage arrives percent-encoded (%2C) so it is not
        // mistaken for the directive's own start/end separator.
        val fragment = TextFragmentParser.parse("https://example.com/post#:~:text=one%2C%20two%2C%20three")
        assertEquals("one, two, three", fragment?.start)
        assertNull(fragment?.end)
    }

    @Test
    fun leavesLiteralPlusVerbatim() {
        // Form decoding would turn `+` into a space; a text fragment must not.
        val fragment = TextFragmentParser.parse("https://example.com/post#:~:text=c%2B%2B%20rocks")
        assertEquals("c++ rocks", fragment?.start)
    }

    @Test
    fun readsFirstTextDirectiveWhenSeveral() {
        val fragment =
            TextFragmentParser.parse("https://example.com/post#:~:text=first&text=second")
        assertEquals("first", fragment?.start)
    }

    @Test
    fun parsesDirectiveAfterElementId() {
        val fragment = TextFragmentParser.parse("https://example.com/post#heading:~:text=quoted")
        assertEquals("quoted", fragment?.start)
    }

    @Test
    fun stripsDirectiveAndBareHash() {
        assertEquals(
            "https://example.com/post",
            TextFragmentParser.stripTextFragment("https://example.com/post#:~:text=hello"),
        )
    }

    @Test
    fun stripsDirectiveButKeepsElementId() {
        assertEquals(
            "https://example.com/post#heading",
            TextFragmentParser.stripTextFragment("https://example.com/post#heading:~:text=hello"),
        )
    }

    @Test
    fun stripLeavesPlainFragmentUntouched() {
        assertEquals(
            "https://example.com/post#section",
            TextFragmentParser.stripTextFragment("https://example.com/post#section"),
        )
    }
}
