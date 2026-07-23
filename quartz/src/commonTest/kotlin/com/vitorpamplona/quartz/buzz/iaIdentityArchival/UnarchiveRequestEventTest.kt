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
package com.vitorpamplona.quartz.buzz.iaIdentityArchival

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnarchiveRequestEventTest {
    private val target = "a".repeat(64)

    @Test
    fun buildRoundTrips() {
        val tpl = UnarchiveRequestEvent.build(target, "back on the team", "rehired")
        val ev = UnarchiveRequestEvent("00".repeat(32), "cc".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(9036, UnarchiveRequestEvent.KIND)
        assertEquals(target, ev.target())
        assertEquals("rehired", ev.reason())
        assertEquals("back on the team", ev.content)
        assertTrue(ev.isProtected(), "unarchive request must carry the NIP-70 '-' marker")
        assertNull(ev.auth())
    }
}
