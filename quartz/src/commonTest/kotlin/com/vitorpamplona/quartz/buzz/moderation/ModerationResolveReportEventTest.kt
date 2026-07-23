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
package com.vitorpamplona.quartz.buzz.moderation

import com.vitorpamplona.quartz.buzz.moderation.tags.ActionTag
import com.vitorpamplona.quartz.buzz.moderation.tags.StatusTag
import kotlin.test.Test
import kotlin.test.assertEquals

class ModerationResolveReportEventTest {
    private val reportId = "a".repeat(64)

    @Test
    fun buildRoundTrips() {
        val tpl = ModerationResolveReportEvent.build(reportId, StatusTag.RESOLVED, ActionTag.BAN, "abusive content")
        val ev = ModerationResolveReportEvent("00".repeat(32), "cc".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(9044, ModerationResolveReportEvent.KIND)
        assertEquals(reportId, ev.report())
        assertEquals(StatusTag.RESOLVED, ev.status())
        assertEquals(ActionTag.BAN, ev.action())
        assertEquals("abusive content", ev.reason())
    }
}
