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
package com.vitorpamplona.quartz.nip38UserStatus

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip38UserStatusLinksTest {
    @Test
    fun aStatusLinksWhatItPointsTo() {
        val person = "b1".repeat(32)
        val note = "e1".repeat(32)
        val event =
            UserStatusEvent(
                "0".repeat(64),
                "f".repeat(64),
                1L,
                arrayOf(
                    arrayOf("d", "music"),
                    arrayOf("r", "spotify:search:Intergalatic%20-%20Beastie%20Boys"),
                    arrayOf("p", person),
                    arrayOf("e", note),
                    arrayOf("a", "30023:$person:post"),
                ),
                "Intergalatic - Beastie Boys",
                "0".repeat(128),
            )

        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("r", "spotify:search:Intergalatic%20-%20Beastie%20Boys"), "r"),
                Link(Relation.LINKED, LinkTarget.User(person), "p"),
                Link(Relation.LINKED, LinkTarget.Event(note), "e"),
                Link(Relation.LINKED, LinkTarget.Address("30023:$person:post"), "a"),
            ),
            event.links(),
        )
    }
}
