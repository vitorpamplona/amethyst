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
package com.vitorpamplona.quartz.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodcastChaptersTest {
    @Test
    fun `parses a podcast-namespace chapters json`() {
        val json =
            """
            {
              "version": "1.2.0",
              "chapters": [
                { "startTime": 0, "title": "Intro" },
                { "startTime": 73.5, "title": "Topic One", "img": "https://x/c1.jpg", "url": "https://x/ref" },
                { "startTime": 600, "title": "Wrap-up", "toc": false }
              ]
            }
            """.trimIndent()

        val parsed = PodcastChapters.parse(json)
        assertTrue(parsed != null)
        assertEquals("1.2.0", parsed.version)
        assertEquals(3, parsed.chapters.size)
        assertEquals("Intro", parsed.chapters[0].title)
        assertEquals(0L, parsed.chapters[0].startSeconds())
        assertEquals(73L, parsed.chapters[1].startSeconds())
        assertEquals("https://x/c1.jpg", parsed.chapters[1].img)
        assertEquals(false, parsed.chapters[2].toc)
    }

    @Test
    fun `returns null on malformed json`() {
        assertNull(PodcastChapters.parse("not-json"))
    }
}
