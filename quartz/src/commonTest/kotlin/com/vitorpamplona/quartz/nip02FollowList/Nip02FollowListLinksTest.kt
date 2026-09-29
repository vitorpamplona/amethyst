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
package com.vitorpamplona.quartz.nip02FollowList

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip02FollowListLinksTest {
    @Test
    fun everyValidPIsAFollow() {
        val followed = "b1".repeat(32)
        val petnamed = "b2".repeat(32)
        val event =
            ContactListEvent(
                "0".repeat(64),
                "f".repeat(64),
                1L,
                arrayOf(
                    arrayOf("p", followed),
                    arrayOf("p", petnamed.uppercase(), "wss://relay.example/", "alice"),
                    arrayOf("p", "nsec1notapubkey"),
                ),
                """{"wss://relay.example/":{"read":true,"write":true}}""",
                "0".repeat(128),
            )

        assertEquals(
            listOf(
                Link(Relation.FOLLOW, LinkTarget.User(followed), "p"),
                Link(Relation.FOLLOW, LinkTarget.User(petnamed), "p"),
            ),
            event.links(),
        )
    }
}
