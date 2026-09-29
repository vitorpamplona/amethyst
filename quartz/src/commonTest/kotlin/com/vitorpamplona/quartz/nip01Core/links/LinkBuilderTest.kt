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
package com.vitorpamplona.quartz.nip01Core.links

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkBuilderTest {
    private val pk = "1".repeat(64)
    private val id = "2".repeat(64)

    @Test
    fun malformedTargetsAreDroppedAndHexIsLowercased() {
        val built =
            links {
                event(Relation.PARENT, "not-an-id")
                event(Relation.PARENT, "g".repeat(64))
                user(Relation.MENTION, "A".repeat(64))
                address(Relation.QUOTE, "30023:short:d")
                address(Relation.QUOTE, "30023:${"B".repeat(64)}:post")
                address(Relation.QUOTE, "10006:$pk:")
                tag(Relation.HASHTAG, "t", " ")
                event(Relation.PARENT, id)
                event(Relation.PARENT, id)
            }
        assertEquals(
            listOf(
                Link(Relation.MENTION, LinkTarget.User("a".repeat(64))),
                Link(Relation.QUOTE, LinkTarget.Address("30023:${"b".repeat(64)}:post")),
                Link(Relation.QUOTE, LinkTarget.Address("10006:$pk:")),
                Link(Relation.PARENT, LinkTarget.Event(id)),
            ),
            built,
        )
    }

    @Test
    fun contentMentionsNeverLinkAnNsec() {
        val npub = Hex.decode(pk).toNpub()
        val nsec = Hex.decode("3".repeat(64)).toNsec()
        val nevent = NEvent.create(id, null, null, null)
        val naddr = NAddress.create(30023, pk, "post", null)
        val built = links { contentMentions("hi nostr:$npub and nostr:$nsec see nostr:$nevent nostr:$naddr") }
        assertEquals(
            listOf(
                Link(Relation.MENTION, LinkTarget.User(pk), Link.VIA_CONTENT),
                Link(Relation.MENTION, LinkTarget.Event(id), Link.VIA_CONTENT),
                Link(Relation.MENTION, LinkTarget.Address("30023:$pk:post"), Link.VIA_CONTENT),
            ),
            built,
        )
    }

    @Test
    fun everyEventStatesItsAuthorAndTheEveryKindTags() {
        val handler = "31990:$pk:app"
        val set = "30030:$pk:blobs"
        val event =
            NostrSignerSync().sign<Event>(
                1L,
                12345,
                arrayOf(
                    arrayOf("client", "Amethyst", handler, "wss://relay.example/"),
                    arrayOf("zap", pk, "wss://relay.example/", "3"),
                    arrayOf("emoji", "blob", "https://img.example/blob.png", set),
                    arrayOf("emoji", "plain", "https://img.example/plain.png"),
                ),
                "",
            )
        assertEquals(
            listOf(
                Link(Relation.AUTHOR, LinkTarget.User(event.pubKey)),
                Link(Relation.CLIENT, LinkTarget.Address(handler), "client"),
                Link(Relation.ZAP_SPLIT, LinkTarget.User(pk), "zap", mapOf("weight" to 3.0)),
                Link(Relation.EMOJI_SET, LinkTarget.Address(set), "emoji"),
            ),
            event.allLinks(),
        )
    }

    @Test
    fun relationNamesAreUniqueAndUpperSnake() {
        assertEquals(
            Relation.ALL.size,
            Relation.ALL
                .map { it.name }
                .toSet()
                .size,
        )
        Relation.ALL.forEach { assertEquals(it.name.uppercase(), it.name) }
    }
}
