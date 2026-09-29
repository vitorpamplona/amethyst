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
package com.vitorpamplona.quartz.nip68Picture

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip68PictureLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun pictureTagsPeopleByTagAndByAnnotation() {
        val tagged = "a".repeat(64)
        val annotated = "b".repeat(64)
        val event =
            PictureEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("imeta", "url https://image.example/cat.jpg", "annotate-user $annotated:10:20"),
                    arrayOf("p", tagged),
                    arrayOf("t", "Cats"),
                    arrayOf("g", "u4pruy"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.TAGGED, LinkTarget.User(tagged), "p"),
                Link(Relation.TAGGED, LinkTarget.User(annotated), "imeta", mapOf("x" to 10, "y" to 20)),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "cats"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pruy"), "g"),
            ),
            event.links(),
        )
    }
}
