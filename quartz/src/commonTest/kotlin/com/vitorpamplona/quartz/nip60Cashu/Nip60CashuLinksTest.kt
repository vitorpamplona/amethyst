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
package com.vitorpamplona.quartz.nip60Cashu

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip60CashuLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun historyNamesWhatEachPublicReferenceDid() {
        val nutzap = "2".repeat(64)
        val created = "3".repeat(64)
        val destroyed = "4".repeat(64)
        val sender = "a".repeat(64)
        val event =
            CashuSpendingHistoryEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("e", nutzap, "", "redeemed"),
                    arrayOf("e", created, "", "created"),
                    arrayOf("e", destroyed, "", "destroyed"),
                    arrayOf("e", "5".repeat(64)),
                    arrayOf("p", sender),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REDEEMED, LinkTarget.Event(nutzap), "e"),
                Link(Relation.CREATED, LinkTarget.Event(created), "e"),
                Link(Relation.DESTROYED, LinkTarget.Event(destroyed), "e"),
                Link(Relation.REDEEMED_AUTHOR, LinkTarget.User(sender), "p"),
            ),
            event.links(),
        )
    }
}
