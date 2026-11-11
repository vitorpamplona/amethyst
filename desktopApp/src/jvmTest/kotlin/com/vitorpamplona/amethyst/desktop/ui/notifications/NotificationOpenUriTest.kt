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
package com.vitorpamplona.amethyst.desktop.ui.notifications

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A click on a desktop notification opens what Android's would, in the account it is for: every
 * link names that account, which the app switches to before it opens the link.
 */
class NotificationOpenUriTest {
    private val me = "a".repeat(64)
    private val them = "b".repeat(64)
    private val myNpub = me.hexToByteArray().toNpub()

    private fun noteOf(event: Event) = Note(event.id).also { it.event = event }

    @Test
    fun aReplyOpensTheReplyItself() {
        val reply = TextNoteEvent("1".repeat(64), them, 1L, arrayOf(arrayOf("e", "2".repeat(64), "", "reply"), arrayOf("p", me)), "hi", "")
        val uri = openUriFor(noteOf(reply), reply, NotifKind.REPLY, me)

        assertTrue(uri.startsWith("nevent1"), uri)
        assertEquals(myNpub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun aReactionOpensTheNotificationsScrolledToIt() {
        val reaction = ReactionEvent("3".repeat(64), them, 1L, arrayOf(arrayOf("e", "4".repeat(64)), arrayOf("p", me)), "+", "")
        val uri = openUriFor(noteOf(reaction), reaction, NotifKind.REACTION, me)

        assertTrue(uri.startsWith("notifications"), uri)
        assertEquals(myNpub, uri.findQueryParameterValue("account"))
        assertEquals(reaction.id, uri.findQueryParameterValue("scrollTo"))
    }

    @Test
    fun aDirectMessageOpensItsRoom() {
        val dm = EncryptedDmEvent("5".repeat(64), them, 1L, arrayOf(arrayOf("p", me)), "cipher?iv=x", "")
        val uri = openUriFor(noteOf(dm), dm, NotifKind.DM, me)

        assertTrue(uri.startsWith("chatroom?id=$them"), uri)
        assertEquals(myNpub, uri.findQueryParameterValue("account"))
    }

    @Test
    fun aSealedMessageOpensTheNotifications() {
        val wrap = GiftWrapEvent("6".repeat(64), "c".repeat(64), 1L, arrayOf(arrayOf("p", me)), "sealed", "")
        val uri = openUriFor(noteOf(wrap), wrap, NotifKind.DM, me)

        assertTrue(uri.startsWith("notifications"), uri)
        assertEquals(myNpub, uri.findQueryParameterValue("account"))
    }
}
