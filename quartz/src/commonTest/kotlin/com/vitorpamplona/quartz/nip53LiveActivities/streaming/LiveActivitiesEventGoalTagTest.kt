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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveActivitiesEventGoalTagTest {
    private val host = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val goalId = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    private val dummySig = "0".repeat(128)

    @Test
    fun readsZapStreamGoalTag() {
        val event =
            LiveActivitiesEvent(
                id = "3".repeat(64),
                pubKey = host,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("d", "stream-d"),
                        arrayOf("title", "My stream"),
                        arrayOf("goal", goalId),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(goalId, event.goalEventId())
    }

    @Test
    fun returnsNullWhenNoGoalTag() {
        val event =
            LiveActivitiesEvent(
                id = "3".repeat(64),
                pubKey = host,
                createdAt = 1_700_000_000L,
                tags = arrayOf(arrayOf("d", "stream-d")),
                content = "",
                sig = dummySig,
            )

        assertNull(event.goalEventId())
    }

    @Test
    fun returnsNullWhenGoalTagEmpty() {
        val event =
            LiveActivitiesEvent(
                id = "3".repeat(64),
                pubKey = host,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("d", "stream-d"),
                        arrayOf("goal", ""),
                    ),
                content = "",
                sig = dummySig,
            )

        assertNull(event.goalEventId())
    }
}
