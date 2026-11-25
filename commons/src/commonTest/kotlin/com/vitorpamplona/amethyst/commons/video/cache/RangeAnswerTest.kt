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
package com.vitorpamplona.amethyst.commons.video.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class RangeAnswerTest {
    @Test
    fun partialContentSaysWhereAndHowLong() {
        assertEquals(RangeAnswer.Bytes(100, 1000), RangeAnswer.of(RangeResponseHead(206, "bytes 100-199/1000"), 100))
        assertEquals(RangeAnswer.Bytes(100, null), RangeAnswer.of(RangeResponseHead(206, "bytes 100-199/*"), 100))
    }

    @Test
    fun partialContentWithoutContentRangeHasNoLength() {
        // Its Content-Length is the slice, not the file.
        assertEquals(RangeAnswer.Bytes(100, null), RangeAnswer.of(RangeResponseHead(206, contentLength = 100), 100))
    }

    @Test
    fun okIsTheWholeFileFromZero() {
        assertEquals(RangeAnswer.Bytes(0, 5000), RangeAnswer.of(RangeResponseHead(200, contentLength = 5000), 300))
        assertEquals(RangeAnswer.Bytes(0, null), RangeAnswer.of(RangeResponseHead(200), 300))
        // A compressed body's length is not the file's.
        assertEquals(RangeAnswer.Bytes(0, null), RangeAnswer.of(RangeResponseHead(200, contentLength = 5000, contentEncoding = "gzip"), 0))
        assertEquals(RangeAnswer.Bytes(0, 5000), RangeAnswer.of(RangeResponseHead(200, contentLength = 5000, contentEncoding = "identity"), 0))
    }

    @Test
    fun pastTheEnd() {
        assertEquals(RangeAnswer.PastEnd(1000), RangeAnswer.of(RangeResponseHead(416, "bytes */1000"), 1000))
        assertEquals(RangeAnswer.PastEnd(null), RangeAnswer.of(RangeResponseHead(416), 1000))
    }

    @Test
    fun errors() {
        assertEquals(RangeAnswer.Failed(404), RangeAnswer.of(RangeResponseHead(404), 0))
        assertEquals(RangeAnswer.Failed(500), RangeAnswer.of(RangeResponseHead(500), 0))
    }

    @Test
    fun contentRangeParsing() {
        assertEquals(0L to 10L, RangeAnswer.parseContentRange("bytes 0-0/10"))
        assertEquals(5L to null, RangeAnswer.parseContentRange("Bytes  5-9 / *"))
        assertEquals(null, RangeAnswer.parseContentRange("bytes */10"))
        assertEquals(null, RangeAnswer.parseContentRange("items 0-1/2"))
        assertEquals(null, RangeAnswer.parseContentRange("bytes 0-1"))
    }
}
