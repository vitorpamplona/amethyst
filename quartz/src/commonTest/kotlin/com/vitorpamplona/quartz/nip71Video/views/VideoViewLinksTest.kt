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
package com.vitorpamplona.quartz.nip71Video.views

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.ViewProps
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoViewLinksTest {
    private val author = "1".repeat(64)
    private val version = "2".repeat(64)

    @Test
    fun aViewLinksTheVideoAndTheVersionWatchedWithItsPhase() {
        val video = "34236:$author:loop"
        val event =
            VideoViewEvent(
                "0".repeat(64),
                "3".repeat(64),
                1,
                arrayOf(arrayOf("a", video), arrayOf("e", version), arrayOf("phase", "start")),
                "",
                "0".repeat(128),
            )
        val phase = ViewProps(phase = "start")
        assertEquals(
            listOf(
                Link(Relation.VIEWED, LinkTarget.Address(video), "a", phase),
                Link(Relation.VIEWED, LinkTarget.Event(version), "e", phase),
            ),
            event.links(),
        )
    }
}
