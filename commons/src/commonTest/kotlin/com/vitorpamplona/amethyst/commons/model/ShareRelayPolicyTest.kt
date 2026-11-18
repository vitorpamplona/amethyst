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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a Share link may copy to Brainstorm's relay, and what it never may. */
class ShareRelayPolicyTest {
    private val id = "a".repeat(64)
    private val author = "b".repeat(64)
    private val sig = "c".repeat(128)

    private fun textNote(
        tags: TagArray = emptyArray(),
        signature: String = sig,
    ) = TextNoteEvent(id, author, 1_700_000_000L, tags, "hello", signature)

    private fun noteOf(event: TextNoteEvent) = Note(event.id).also { it.event = event }

    @Test
    fun aSignedPublicNoteIsPushed() {
        assertTrue(ShareRelayPolicy.canPush(textNote()))
        assertTrue(ShareRelayPolicy.canPush(noteOf(textNote(arrayOf(arrayOf("t", "nostr"))))))
    }

    @Test
    fun anUnsignedRumorIsNeverPushed() {
        // NIP-17 DMs, Concord and Marmot messages are materialized with an empty signature.
        assertFalse(ShareRelayPolicy.canPush(textNote(signature = "")))
    }

    @Test
    fun aNoteDeliveredInsideAnEnvelopeIsNeverPushed() {
        val note = noteOf(textNote())
        note.recordRumorHost(GiftWrapEvent("d".repeat(64), author, 1_700_000_000L, arrayOf(arrayOf("p", author)), "ciphertext", sig))

        assertFalse(ShareRelayPolicy.canPush(note))
    }

    @Test
    fun aNoteWithoutItsEventIsNotPushed() {
        assertFalse(ShareRelayPolicy.canPush(Note(id)))
    }

    @Test
    fun aRelayGroupPostIsNeverPushed() {
        // NIP-29: the group's relay decides who reads it.
        assertFalse(ShareRelayPolicy.canPush(textNote(arrayOf(arrayOf("h", "my-group")))))
    }

    @Test
    fun aProtectedEventIsNeverPushed() {
        // NIP-70: only the author may publish it to a relay.
        assertFalse(ShareRelayPolicy.canPush(textNote(arrayOf(arrayOf("-")))))
    }

    @Test
    fun envelopesOfPrivateContentAreNeverPushed() {
        val pTag = arrayOf(arrayOf("p", author))
        assertFalse(ShareRelayPolicy.canPush(GiftWrapEvent(id, author, 1L, pTag, "x", sig)))
        assertFalse(ShareRelayPolicy.canPush(SealEvent(id, author, 1L, emptyArray(), "x", sig)))
        assertFalse(ShareRelayPolicy.canPush(EncryptedDmEvent(id, author, 1L, pTag, "x?iv=y", sig)))
        assertFalse(ShareRelayPolicy.canPush(GroupEvent(id, author, 1L, arrayOf(arrayOf("h", "group")), "x", sig)))
        assertFalse(ShareRelayPolicy.canPush(DraftWrapEvent(id, author, 1L, arrayOf(arrayOf("d", "draft")), "x", sig)))
    }

    private val profile = MetadataEvent("e".repeat(64), author, 1L, emptyArray(), "{\"name\":\"bob\"}", sig)
    private val relayList = AdvertisedRelayListEvent("f".repeat(64), author, 1L, arrayOf(arrayOf("r", "wss://nos.lol")), "", sig)

    private fun authoredNote(event: TextNoteEvent): Note {
        val user = User(author) { address -> AddressableNote(address) }
        user.nip65RelayListNote.event = relayList
        return Note(event.id).also { it.loadEvent(event, user, emptyList()) }
    }

    @Test
    fun sharingANoteCopiesItThenItsAuthorsProfileAndRelayList() {
        val event = textNote()
        val pushed = ShareRelayPolicy.eventsToPush(authoredNote(event)) { profile }

        assertEquals(listOf<Event>(event, profile, relayList), pushed)
    }

    @Test
    fun aPrivateNoteCopiesNothingNotEvenItsAuthorsProfile() {
        val pushed = ShareRelayPolicy.eventsToPush(authoredNote(textNote(signature = ""))) { profile }

        assertTrue(pushed.isEmpty())
    }

    @Test
    fun sharingAProfileCopiesWhatIsCachedOfIt() {
        val user = User(author) { address -> AddressableNote(address) }

        assertTrue(ShareRelayPolicy.eventsToPush(user) { null }.isEmpty())
        assertEquals(listOf<Event>(profile), ShareRelayPolicy.eventsToPush(user) { profile })
    }

    @Test
    fun theShareRelayIsBrainstorms() {
        assertEquals(setOf(Constants.brainstorm), ShareRelayPolicy.RELAYS)
        assertEquals("wss://search.brainstorm.world/", Constants.brainstorm.url)
    }
}
