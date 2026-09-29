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
package com.vitorpamplona.quartz.nip88Polls

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip88PollsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun responseNamesThePollAndTheChosenOptions() {
        val poll = "2".repeat(64)
        val pollAuthor = "a".repeat(64)
        val event =
            PollResponseEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("e", poll), arrayOf("p", pollAuthor), arrayOf("response", "yes"), arrayOf("response", "maybe")),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.POLL, LinkTarget.Event(poll), "e", mapOf("responses" to "yes,maybe")),
                Link(Relation.POLL_AUTHOR, LinkTarget.User(pollAuthor), "p"),
            ),
            event.links(),
        )
    }
}
