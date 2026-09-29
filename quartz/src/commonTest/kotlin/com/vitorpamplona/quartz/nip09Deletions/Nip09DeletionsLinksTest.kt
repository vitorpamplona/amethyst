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
package com.vitorpamplona.quartz.nip09Deletions

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip09DeletionsLinksTest {
    @Test
    fun aDeletionRequestNamesWhatItDeletes() {
        val me = "f".repeat(64)
        val note = "e1".repeat(32)
        val version = "e2".repeat(32)
        val article = "30023:$me:article"
        val event =
            DeletionRequestEvent(
                "0".repeat(64),
                me,
                1L,
                arrayOf(
                    arrayOf("e", note),
                    arrayOf("e", version),
                    arrayOf("a", article),
                    arrayOf("p", me),
                    arrayOf("k", "1"),
                    arrayOf("k", "30023"),
                ),
                "posted by mistake",
                "0".repeat(128),
            )

        assertEquals(
            listOf(
                Link(Relation.DELETED, LinkTarget.Event(note), "e"),
                Link(Relation.DELETED, LinkTarget.Event(version), "e"),
                Link(Relation.DELETED, LinkTarget.Address(article), "a"),
                Link(Relation.DELETED_AUTHOR, LinkTarget.User(me), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
                Link(Relation.TAG, LinkTarget.Tag("k", "30023"), "k"),
            ),
            event.links(),
        )
    }
}
