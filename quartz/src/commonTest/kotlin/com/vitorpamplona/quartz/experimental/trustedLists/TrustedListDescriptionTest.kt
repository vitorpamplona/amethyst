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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The `description` and `rigor` tags Brainstorm's trusted-list reader (`lib/trustedList.ts`) shows. */
class TrustedListDescriptionTest {
    private fun list(vararg extra: Array<String>): UserTrustedListEvent {
        val event =
            EventFactory.create<Event>(
                id = "c6114a5ea1d6fa763baa3ddea0fb9c648aa336c26a73f4b18078e1eae12770ac",
                pubKey = "919ba08af7786892093b8264332d817379662a0ba0ba1f5c791ed7b62a7ee2ff",
                createdAt = 1_787_253_028L,
                kind = UserTrustedListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "tl-pin-b83a28b7-b83a28b7-musician"),
                        arrayOf("title", "Musician"),
                        arrayOf("cutoff", "2"),
                        arrayOf("min-rank", "2"),
                        *extra,
                        arrayOf("p", "50a63cca15b16b60d329b92f628c432c8c12689b40fe9d0495eb836b5f2df637"),
                    ),
                content = "",
                sig = "00".repeat(64),
            )
        return assertIs<UserTrustedListEvent>(event)
    }

    @Test
    fun readsDescriptionAndRigor() {
        val event = list(arrayOf("description", "Musicians, as my network sees them"), arrayOf("rigor", "0.25"))

        assertEquals("Musicians, as my network sees them", event.description())
        assertEquals("0.25", event.rigor())
        assertEquals(2, event.cutoff())
        assertEquals(2, event.minRank())
    }

    @Test
    fun absentOrEmptyIsNull() {
        val event = list(arrayOf("description", ""), arrayOf("rigor", ""))

        assertNull(event.description())
        assertNull(event.rigor())
        assertNull(list().description())
    }
}
