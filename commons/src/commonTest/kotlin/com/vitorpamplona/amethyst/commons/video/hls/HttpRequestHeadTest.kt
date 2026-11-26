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
package com.vitorpamplona.amethyst.commons.video.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpRequestHeadTest {
    @Test
    fun readsTheRequestLineAndHeadersWithoutCase() {
        val head =
            HttpRequestHead.parse(
                listOf(
                    "GET /s/t/abc/seg.ts HTTP/1.1",
                    "Host: 127.0.0.1:4321",
                    "range: bytes=10-20",
                    "X-Playback-Session-Id: ABC",
                    "Range: bytes=99-",
                ),
            )
        assertNotNull(head)
        assertEquals("GET", head.method)
        assertEquals("/s/t/abc/seg.ts", head.target)
        assertEquals("127.0.0.1:4321", head.header("host"))
        assertEquals("bytes=10-20", head.header("Range"), "the first of a repeated header")
        assertNull(head.header("If-Range"))
    }

    @Test
    fun refusesWhatIsNotAnHttp1Request() {
        assertNull(HttpRequestHead.parse(emptyList()))
        assertNull(HttpRequestHead.parse(listOf("GET /x")))
        assertNull(HttpRequestHead.parse(listOf("GET http://evil.example/ HTTP/1.1")), "an absolute-form target: proxy use")
        assertNull(HttpRequestHead.parse(listOf("GET /x HTTP/2")))
        assertNull(HttpRequestHead.parse(listOf("GET /x HTTP/1.1", "no colon here")))
    }
}
