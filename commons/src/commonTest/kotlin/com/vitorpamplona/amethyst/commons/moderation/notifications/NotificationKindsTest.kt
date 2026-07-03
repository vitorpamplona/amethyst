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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationKindsTest {
    private val me = "me_pubkey_hex".padEnd(64, '0')
    private val alice = "alice_pubkey_hex".padEnd(64, '0')
    private val bob = "bob_pubkey_hex".padEnd(64, '0')
    private val myNoteId = "my_note_id".padEnd(64, '0')
    private val strangerNoteId = "stranger_note_id".padEnd(64, '0')
    private val someSig = "sig".padEnd(128, '0')

    private fun reaction(
        author: String,
        pTag: String? = me,
        eTag: String? = myNoteId,
        content: String = "+",
    ): ReactionEvent {
        val tags =
            listOfNotNull(
                eTag?.let { arrayOf("e", it) },
                pTag?.let { arrayOf("p", it) },
            ).toTypedArray()
        return ReactionEvent(
            id = "rid".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = tags,
            content = content,
            sig = someSig,
        )
    }

    private fun repost(
        author: String,
        pTag: String? = me,
        eTag: String? = myNoteId,
    ): RepostEvent {
        val tags =
            listOfNotNull(
                eTag?.let { arrayOf("e", it) },
                pTag?.let { arrayOf("p", it) },
            ).toTypedArray()
        return RepostEvent(
            id = "rpost".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = tags,
            content = "",
            sig = someSig,
        )
    }

    private fun textNote(
        author: String,
        pTag: String? = me,
        content: String = "hi",
    ): TextNoteEvent {
        val tags = listOfNotNull(pTag?.let { arrayOf("p", it) }).toTypedArray()
        return TextNoteEvent(
            id = "tn".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = tags,
            content = content,
            sig = someSig,
        )
    }

    private fun comment(
        author: String,
        pTag: String? = me,
    ): CommentEvent {
        val tags = listOfNotNull(pTag?.let { arrayOf("p", it) }).toTypedArray()
        return CommentEvent(
            id = "cmt".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = tags,
            content = "reply",
            sig = someSig,
        )
    }

    private fun zapReceipt(pTag: String = me): LnZapEvent =
        LnZapEvent(
            id = "zap".padEnd(64, '0'),
            pubKey = "lnurl_provider".padEnd(64, '0'),
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", pTag)),
            content = "",
            sig = someSig,
        )

    private fun nutzap(
        author: String,
        pTag: String = me,
    ): NutzapEvent =
        NutzapEvent(
            id = "nut".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", pTag)),
            content = "",
            sig = someSig,
        )

    private fun privateDm(
        author: String,
        pTag: String = me,
    ): PrivateDmEvent =
        PrivateDmEvent(
            id = "dm".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", pTag)),
            content = "ciphertext",
            sig = someSig,
        )

    private fun giftWrap(pTag: String = me): GiftWrapEvent =
        GiftWrapEvent(
            id = "gw".padEnd(64, '0'),
            pubKey = "ephemeral".padEnd(64, '0'),
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", pTag)),
            content = "wrapped",
            sig = someSig,
        )

    private fun channelMsg(
        author: String,
        pTag: String? = me,
    ): ChannelMessageEvent {
        val tags = listOfNotNull(pTag?.let { arrayOf("p", it) }).toTypedArray()
        return ChannelMessageEvent(
            id = "cm".padEnd(64, '0'),
            pubKey = author,
            createdAt = 1_000,
            tags = tags,
            content = "hi channel",
            sig = someSig,
        )
    }

    private fun acceptsFor(
        event: Event,
        isMyNote: (String) -> Boolean = { it == myNoteId },
    ): Boolean = NotificationKinds.tagsAnEventForUser(event, me, isMyNote)

    // Reactions --------------------------------------------------------------

    @Test
    fun reactionWithPMeButTargetAuthoredByStrangerRejected() {
        val e = reaction(author = alice, pTag = me, eTag = strangerNoteId)
        assertFalse(acceptsFor(e))
    }

    @Test
    fun reactionTargetingMyNoteAccepted() {
        val e = reaction(author = alice, pTag = me, eTag = myNoteId)
        assertTrue(acceptsFor(e))
    }

    @Test
    fun reactionWithNoETagRejected() {
        val e = reaction(author = alice, pTag = me, eTag = null)
        assertFalse(acceptsFor(e))
    }

    // Reposts ----------------------------------------------------------------

    @Test
    fun repostTargetingMyNoteAccepted() {
        val e = repost(author = alice, pTag = me, eTag = myNoteId)
        assertTrue(acceptsFor(e))
    }

    @Test
    fun repostTargetingStrangerNoteRejected() {
        val e = repost(author = alice, pTag = me, eTag = strangerNoteId)
        assertFalse(acceptsFor(e))
    }

    // Text notes -------------------------------------------------------------

    @Test
    fun textNoteWithPMeAccepted() {
        assertTrue(acceptsFor(textNote(author = alice, pTag = me)))
    }

    @Test
    fun textNoteWithoutPMeRejected() {
        // Text notes must actually p-tag the user. The helper cannot trust
        // an upstream filter here because it's also called from the
        // cache-seed pass which walks every note in localCache regardless
        // of subscription source.
        assertFalse(acceptsFor(textNote(author = alice, pTag = null)))
    }

    @Test
    fun textNoteWithPTagPointingAtStrangerRejected() {
        // Someone else was p-tagged; not us.
        val stranger = "stranger_pubkey".padEnd(64, '0')
        assertFalse(acceptsFor(textNote(author = alice, pTag = stranger)))
    }

    @Test
    fun channelMessageWithoutPMeRejected() {
        assertFalse(acceptsFor(channelMsg(author = alice, pTag = null)))
    }

    @Test
    fun textNoteAuthoredByMeRejected() {
        // Own events don't notify.
        assertFalse(acceptsFor(textNote(author = me, pTag = me)))
    }

    // Comments ---------------------------------------------------------------

    @Test
    fun commentWithPMeAccepted() {
        assertTrue(acceptsFor(comment(author = alice, pTag = me)))
    }

    // Zaps + Nutzaps ---------------------------------------------------------

    @Test
    fun zapReceiptAccepted() {
        assertTrue(acceptsFor(zapReceipt(pTag = me)))
    }

    @Test
    fun myOwnZapReceiptStillAccepted() {
        // LnZap receipts are signed by the LNURL provider, not by us,
        // so `pubKey == me` never applies — but even if it did the
        // helper explicitly allows LnZap/Nutzap/OnchainZap own-events.
        assertTrue(acceptsFor(zapReceipt(pTag = me)))
    }

    @Test
    fun nutzapAccepted() {
        assertTrue(acceptsFor(nutzap(author = bob, pTag = me)))
    }

    // DMs --------------------------------------------------------------------

    @Test
    fun privateDmAccepted() {
        assertTrue(acceptsFor(privateDm(author = alice)))
    }

    @Test
    fun giftWrapAccepted() {
        assertTrue(acceptsFor(giftWrap()))
    }

    // Channel messages -------------------------------------------------------

    @Test
    fun channelMessageWithPMeAccepted() {
        assertTrue(acceptsFor(channelMsg(author = alice, pTag = me)))
    }

    // SUBSCRIPTION_KINDS sanity ---------------------------------------------

    @Test
    fun subscriptionKindsCoversAllExpectedEventKinds() {
        val expected =
            setOf(
                TextNoteEvent.KIND,
                PrivateDmEvent.KIND,
                RepostEvent.KIND,
                ReactionEvent.KIND,
                GenericRepostEvent.KIND,
                ChannelMessageEvent.KIND,
                CommentEvent.KIND,
                GiftWrapEvent.KIND,
                NutzapEvent.KIND,
                LnZapEvent.KIND,
            )
        val actual = NotificationKinds.SUBSCRIPTION_KINDS.toSet()
        assertTrue(actual.containsAll(expected), "expected subset missing from SUBSCRIPTION_KINDS")
    }

    @Test
    fun subscriptionFilterUsesPTagAndSubscriptionKinds() {
        val filter = NotificationKinds.subscriptionFilter(me, limit = 50, since = 1_000)
        assertEquals(NotificationKinds.SUBSCRIPTION_KINDS, filter.kinds)
        assertEquals(listOf(me), filter.tags?.get("p"))
        assertEquals(50, filter.limit)
        assertEquals(1_000L, filter.since)
    }
}
