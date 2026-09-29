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
package com.vitorpamplona.quartz.nip28PublicChat

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.LinkProps
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip28PublicChatLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val channel = "c1".repeat(32)
    private val otherChannel = "c2".repeat(32)
    private val message = "e1".repeat(32)
    private val otherMessage = "e2".repeat(32)
    private val quoted = "e3".repeat(32)
    private val messageAuthor = "b1".repeat(32)
    private val bystander = "b2".repeat(32)
    private val relay = "wss://relay.example/"

    private fun <P : LinkProps> ev(
        relation: Relation<P>,
        id: String,
        via: String = "e",
    ) = Link(relation, LinkTarget.Event(id), via)

    private fun <P : LinkProps> us(
        relation: Relation<P>,
        pubkey: String,
        via: String = "p",
    ) = Link(relation, LinkTarget.User(pubkey), via)

    @Test
    fun aMessageIsInItsChannelAndRepliesToItsParent() {
        val npub = Hex.decode(bystander).toNpub()
        val event =
            ChannelMessageEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", channel, relay, "root"),
                    arrayOf("e", message, relay, "reply", messageAuthor),
                    arrayOf("e", otherMessage, relay, "mention"),
                    arrayOf("p", messageAuthor, relay),
                    arrayOf("p", bystander),
                    arrayOf("q", quoted),
                    arrayOf("a", "30023:$bystander:post"),
                ),
                "hi nostr:$npub",
                sig,
            )

        assertEquals(
            listOf(
                ev(Relation.ROOT, channel),
                ev(Relation.PARENT, message),
                ev(Relation.MENTION, otherMessage),
                us(Relation.PARENT_AUTHOR, messageAuthor),
                us(Relation.MENTION, bystander),
                ev(Relation.QUOTE, quoted, "q"),
                Link(Relation.MENTION, LinkTarget.Address("30023:$bystander:post"), "a"),
                us(Relation.MENTION, bystander, Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun anUnmarkedChannelIsNeverTheParent() {
        val event = ChannelMessageEvent(id, me, 1L, arrayOf(arrayOf("e", channel, relay)), "", sig)
        assertEquals(listOf(ev(Relation.ROOT, channel)), event.links())
    }

    @Test
    fun theChannelIsNotAHiddenMessage() {
        val event =
            ChannelHideMessageEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", channel, relay, "root"),
                    arrayOf("e", message),
                    arrayOf("e", otherMessage),
                ),
                "spam",
                sig,
            )
        assertEquals(
            listOf(
                ev(Relation.ROOT, channel),
                ev(Relation.HIDDEN, message),
                ev(Relation.HIDDEN, otherMessage),
            ),
            event.links(),
        )
    }

    @Test
    fun aSpecConformingHideNamesNoChannel() {
        // NIP-28's own 43 carries only the hidden message: it must not read as the channel.
        val event = ChannelHideMessageEvent(id, me, 1L, arrayOf(arrayOf("e", message)), "", sig)
        assertEquals(listOf(ev(Relation.HIDDEN, message)), event.links())
    }

    @Test
    fun aChannelMuteIsChannelModeration() {
        val event =
            ChannelMuteUserEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", channel, relay, "root"),
                    arrayOf("p", bystander),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                ev(Relation.ROOT, channel),
                us(Relation.CHANNEL_MUTED, bystander),
            ),
            event.links(),
        )
    }

    @Test
    fun aMetadataUpdateIsForItsChannel() {
        val event = ChannelMetadataEvent(id, me, 1L, arrayOf(arrayOf("e", channel, relay, "root")), "{}", sig)
        assertEquals(listOf(ev(Relation.ROOT, channel)), event.links())
    }

    @Test
    fun aChannelCreationOnlyMentionsItsStrayAddresses() {
        val event = ChannelCreateEvent(id, me, 1L, arrayOf(arrayOf("a", "30023:$bystander:post")), "{}", sig)
        assertEquals(listOf(Link(Relation.MENTION, LinkTarget.Address("30023:$bystander:post"), "a")), event.links())
    }

    @Test
    fun thePublicChatsListIsSubscribedChannels() {
        val event =
            PublicChatListEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("e", channel, relay),
                    arrayOf("e", otherChannel),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                ev(Relation.SUBSCRIBED, channel),
                ev(Relation.SUBSCRIBED, otherChannel),
            ),
            event.links(),
        )
    }
}
