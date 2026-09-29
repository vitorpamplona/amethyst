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
package com.vitorpamplona.quartz.nip5dNapplets

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip5dNappletsLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val author = "1".repeat(64)
    private val origin = "2".repeat(64)
    private val named = "35129:$author:chess"
    private val app = "32267:$author:com.example.chess"

    @Test
    fun snapshotLinksTheNappletItSnapshots() {
        val event =
            NappletSnapshotEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("a", named),
                    arrayOf("A", "35129:$origin:chess"),
                    arrayOf("app", app),
                    arrayOf("path", "/index.html", "3".repeat(64)),
                    arrayOf("requires", "relay"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.SNAPSHOTTED, LinkTarget.Address(named), "a"),
                Link(Relation.ORIGIN, LinkTarget.Address("35129:$origin:chess"), "A"),
                Link(Relation.APP, LinkTarget.Address(app), "app"),
            ),
            event.links(),
        )
    }

    @Test
    fun namedAndRootNappletsLinkTheirCopyLineage() {
        val parent = "35129:$origin:chess"
        val tags = arrayOf(arrayOf("d", "chess"), arrayOf("a", parent), arrayOf("A", parent), arrayOf("app", "not-an-address"))
        val expected =
            listOf(
                Link(Relation.COPIED, LinkTarget.Address(parent), "a"),
                Link(Relation.ORIGIN, LinkTarget.Address(parent), "A"),
            )
        assertEquals(expected, NamedNappletEvent(id, author, 1, tags, "", sig).links())
        assertEquals(expected, RootNappletEvent(id, author, 1, tags.drop(1).toTypedArray(), "", sig).links())
    }
}
