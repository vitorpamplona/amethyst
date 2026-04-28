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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackgroundTagTest {
    @Test
    fun parseValidWithMode() {
        val parsed = BackgroundTag.parse(arrayOf("bg", "https://i/x.jpg", "tile"))
        assertEquals("https://i/x.jpg", parsed?.url)
        assertEquals(BackgroundTag.Mode.TILE, parsed?.mode)
    }

    @Test
    fun defaultsToCoverWhenModeMissing() {
        val parsed = BackgroundTag.parse(arrayOf("bg", "https://i/x.jpg"))
        assertEquals(BackgroundTag.Mode.COVER, parsed?.mode)
    }

    @Test
    fun unknownModeFallsBackToCover() {
        // Spec evolution — a future "blur" mode must not crash the
        // parser. Falls back to COVER until the renderer learns the
        // new mode.
        val parsed = BackgroundTag.parse(arrayOf("bg", "https://i/x.jpg", "blur"))
        assertEquals(BackgroundTag.Mode.COVER, parsed?.mode)
    }

    @Test
    fun rejectsEmptyUrl() {
        assertNull(BackgroundTag.parse(arrayOf("bg", "")))
    }

    @Test
    fun assembleRoundTrips() {
        val tag = BackgroundTag.assemble("https://i/p.png", BackgroundTag.Mode.TILE)
        val parsed = BackgroundTag.parse(tag)
        assertEquals(
            BackgroundTag(url = "https://i/p.png", mode = BackgroundTag.Mode.TILE),
            parsed,
        )
    }
}
