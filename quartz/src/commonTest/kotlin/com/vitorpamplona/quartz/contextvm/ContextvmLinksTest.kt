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
package com.vitorpamplona.quartz.contextvm

import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmToolsListEvent
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextvmLinksTest {
    private val me = "0".repeat(64)

    @Test
    fun toolsListLinksItsCommonSchemaIds() {
        val tags =
            arrayOf(
                arrayOf("i", "schemahash1", "create_group"),
                arrayOf("k", "io.contextvm/common-schema"),
                arrayOf("p", "1".repeat(64)),
            )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("i", "schemahash1"), "i"),
                Link(Relation.TAG, LinkTarget.Tag("k", "io.contextvm/common-schema"), "k"),
            ),
            CvmToolsListEvent(me, me, 0, tags, "{\"tools\":[]}", me).links(),
        )
    }
}
