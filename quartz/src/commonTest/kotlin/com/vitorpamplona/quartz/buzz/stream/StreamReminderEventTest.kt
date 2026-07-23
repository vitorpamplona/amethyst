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
package com.vitorpamplona.quartz.buzz.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamReminderEventTest {
    private val channelId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val user = "aaaa000000000000000000000000000000000000000000000000000000000001"
    private val target = "eeee000000000000000000000000000000000000000000000000000000000abc"

    @Test
    fun buildEmitsChannelAndRecipient() {
        val template =
            StreamReminderEvent.build(
                channelId = channelId,
                recipientPubKey = user,
                content = "review the PR",
                createdAt = 1_700_000_000L,
            )

        assertEquals(StreamReminderEvent.KIND, template.kind)
        assertEquals(40007, template.kind)
        assertEquals("review the PR", template.content)

        val byName = template.tags.groupBy { it[0] }
        assertEquals(channelId, byName["h"]!!.single()[1])
        assertEquals(user, byName["p"]!!.single()[1])
    }

    @Test
    fun accessorsReadTags() {
        val event =
            StreamReminderEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("h", channelId),
                        arrayOf("p", user),
                        arrayOf("e", target),
                    ),
                content = "remind",
                sig = "00",
            )

        assertEquals(channelId, event.channel())
        assertEquals(listOf(user), event.recipients())
        assertEquals(target, event.targetMessage())
    }
}
