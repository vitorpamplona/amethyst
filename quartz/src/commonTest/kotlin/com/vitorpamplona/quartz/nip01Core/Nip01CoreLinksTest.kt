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
package com.vitorpamplona.quartz.nip01Core

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip01CoreLinksTest {
    @Test
    fun aProfileLinksOnlyItsIdentityClaims() {
        val event =
            MetadataEvent(
                "0".repeat(64),
                "f".repeat(64),
                1L,
                arrayOf(
                    arrayOf("name", "Vitor"),
                    arrayOf("i", "github:vitorpamplona", "cf19e2d1d7f8dac6585c4d7e9a4e0f41"),
                    arrayOf("i", "twitter:vitorpamplona", "1619358434134196225"),
                ),
                """{"name":"Vitor","about":"nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"}""",
                "0".repeat(128),
            )

        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("i", "github:vitorpamplona"), "i"),
                Link(Relation.TAG, LinkTarget.Tag("i", "twitter:vitorpamplona"), "i"),
            ),
            event.links(),
        )
    }
}
