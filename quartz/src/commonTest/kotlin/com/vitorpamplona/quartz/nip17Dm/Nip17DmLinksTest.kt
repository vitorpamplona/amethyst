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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip17DmLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val carol = "3".repeat(64)
    private val parent = "4".repeat(64)
    private val quoted = "5".repeat(64)

    @Test
    fun chatMessageLinksRecipientsParentQuotesAndMentions() {
        val event =
            ChatMessageEvent(
                id,
                alice,
                1,
                arrayOf(
                    arrayOf("p", bob, "wss://inbox.example/"),
                    arrayOf("e", parent, "wss://inbox.example/"),
                    arrayOf("q", quoted),
                    arrayOf("q", "30023:$carol:post"),
                    arrayOf("subject", "lunch"),
                ),
                "ask nostr:${Hex.decode(carol).toNpub()}",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(bob), "p"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.QUOTE, LinkTarget.Address("30023:$carol:post"), "q"),
                Link(Relation.MENTION, LinkTarget.User(carol), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun fileMessageLinksRecipientsAndParentButNotItsBlob() {
        val event =
            ChatMessageEncryptedFileHeaderEvent(
                id,
                alice,
                1,
                arrayOf(
                    arrayOf("p", bob),
                    arrayOf("e", parent, "", "reply"),
                    arrayOf("x", "6".repeat(64)),
                    arrayOf("file-type", "image/jpeg"),
                ),
                "https://blossom.example/blob",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(bob), "p"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
            ),
            event.links(),
        )
    }
}
