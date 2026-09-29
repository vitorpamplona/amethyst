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
package com.vitorpamplona.quartz.nipA4PublicMessages

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class NipA4PublicMessagesLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun receiversAreRecipientsAndMentionsComeFromTheContent() {
        val first = "a".repeat(64)
        val second = "b".repeat(64)
        val cited = "c".repeat(64)
        val quoted = "2".repeat(64)
        val npub = Hex.decode(cited).toNpub()
        val event = PublicMessageEvent(id, author, 1, arrayOf(arrayOf("p", first), arrayOf("p", second), arrayOf("q", quoted)), "cc nostr:$npub", sig)
        assertEquals(
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(first), "p"),
                Link(Relation.RECIPIENT, LinkTarget.User(second), "p"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.MENTION, LinkTarget.User(cited), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }
}
