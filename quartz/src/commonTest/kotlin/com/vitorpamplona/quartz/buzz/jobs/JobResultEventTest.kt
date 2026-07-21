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
package com.vitorpamplona.quartz.buzz.jobs

import kotlin.test.Test
import kotlin.test.assertEquals

class JobResultEventTest {
    private val requestId = "e".repeat(64)
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val requester = "a".repeat(64)

    @Test
    fun buildTagsAndContent() {
        val t = JobResultEvent.build(requestId, "the answer is 42", channel, requester, "completed")
        val ev = JobResultEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(JobResultEvent.KIND, ev.kind)
        assertEquals("the answer is 42", ev.result())
        assertEquals(requestId, ev.jobRequest())
        assertEquals(channel, ev.channel())
        assertEquals(requester, ev.requester())
        assertEquals("completed", ev.status())
    }
}
