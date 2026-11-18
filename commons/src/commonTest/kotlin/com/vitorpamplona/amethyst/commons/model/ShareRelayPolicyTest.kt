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
import com.vitorpamplona.amethyst.commons.util.ShareSite
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
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

    @Test
    fun privateRepositoriesAndGroupsAreNeverPushed() {
        assertFalse(ShareRelayPolicy.canPush(GitRepositoryEvent(id, author, 1L, arrayOf(arrayOf("d", "repo"), arrayOf("private", "true")), "", sig)))
        assertTrue(ShareRelayPolicy.canPush(GitRepositoryEvent(id, author, 1L, arrayOf(arrayOf("d", "repo")), "", sig)))
        assertFalse(ShareRelayPolicy.canPush(GroupMetadataEvent(id, author, 1L, arrayOf(arrayOf("d", "g"), arrayOf("private")), "", sig)))
        assertFalse(ShareRelayPolicy.canPush(GroupMetadataEvent(id, author, 1L, arrayOf(arrayOf("d", "g"), arrayOf("hidden")), "", sig)))
        assertTrue(ShareRelayPolicy.canPush(GroupMetadataEvent(id, author, 1L, arrayOf(arrayOf("d", "g"), arrayOf("public")), "", sig)))
    }

    private val publicRelay = RelayUrlNormalizer.normalize("wss://nos.lol")
    private val privateRelay = RelayUrlNormalizer.normalize("wss://my.private.relay")
    private val isPublic = { relay: NormalizedRelayUrl -> relay != privateRelay }

    private val profileNote: AddressableNote =
        run {
            val profile = MetadataEvent("e".repeat(64), author, 1L, emptyArray(), "{\"name\":\"bob\"}", sig)
            AddressableNote(MetadataEvent.createAddress(author)).also {
                it.event = profile
                it.addRelay(publicRelay)
            }
        }
    private val relayList = AdvertisedRelayListEvent("f".repeat(64), author, 1L, arrayOf(arrayOf("r", "wss://nos.lol")), "", sig)

    private fun author(): User {
        val user = User(author) { address -> AddressableNote(address) }
        user.nip65RelayListNote.event = relayList
        user.nip65RelayListNote.addRelay(publicRelay)
        return user
    }

    private fun seenOn(
        event: Event,
        vararg relays: NormalizedRelayUrl,
        replyTo: List<Note> = emptyList(),
    ): Note = Note(event.id).also { note -> note.loadEvent(event, author(), replyTo) }.also { note -> relays.forEach { note.addRelay(it) } }

    @Test
    fun sharingANoteCopiesItThenItsAuthorsProfileAndRelayList() {
        val event = textNote()
        val pushed = ShareRelayPolicy.eventsToPush(seenOn(event, publicRelay), { profileNote }, isPublic)

        assertEquals(listOf(event, profileNote.event, relayList), pushed)
    }

    @Test
    fun aPrivateNoteCopiesNothingNotEvenItsAuthorsProfile() {
        val pushed = ShareRelayPolicy.eventsToPush(seenOn(textNote(signature = ""), publicRelay), { profileNote }, isPublic)

        assertTrue(pushed.isEmpty())
    }

    @Test
    fun aNoteSeenOnlyOnPrivateRelaysIsNotCopied() {
        // Shared with someone who has access to that relay: copying it would publish it to everyone.
        assertTrue(ShareRelayPolicy.eventsToPush(seenOn(textNote(), privateRelay), { profileNote }, isPublic).isEmpty())
        // Never seen on any relay (a local cache, a store): no proof it is public.
        assertTrue(ShareRelayPolicy.eventsToPush(seenOn(textNote()), { profileNote }, isPublic).isEmpty())
        // Seen on a public relay as well: it is public.
        assertEquals(id, ShareRelayPolicy.eventsToPush(seenOn(textNote(), privateRelay, publicRelay), { null }, isPublic).first().id)
    }

    @Test
    fun whatTheShareRelayAlreadyHasIsNotSentAgain() {
        val event = textNote()
        profileNote.addRelay(Constants.brainstorm)
        val pushed = ShareRelayPolicy.eventsToPush(seenOn(event, publicRelay, Constants.brainstorm), { profileNote }, isPublic)

        // The note and the profile came from (or were accepted by) Brainstorm's relay; the relay list was not.
        assertEquals(listOf<Event>(relayList), pushed)
    }

    @Test
    fun aRepostAlsoCopiesTheNoteItReposts() {
        val original = seenOn(TextNoteEvent("1".repeat(64), author, 1L, emptyArray(), "original", sig), publicRelay)
        val repost = RepostEvent("2".repeat(64), author, 2L, arrayOf(arrayOf("e", original.idHex)), "", sig)

        val pushed = ShareRelayPolicy.eventsToPush(seenOn(repost, publicRelay, replyTo = listOf(original)), { null }, isPublic)

        assertEquals(listOf(repost, original.event, relayList), pushed)
    }

    @Test
    fun aReplyDoesNotCopyAParentThatMustStayPrivate() {
        val parent = seenOn(TextNoteEvent("1".repeat(64), author, 1L, emptyArray(), "members only", sig), privateRelay)
        val reply = TextNoteEvent("2".repeat(64), author, 2L, arrayOf(arrayOf("e", parent.idHex)), "reply", sig)

        val pushed = ShareRelayPolicy.eventsToPush(seenOn(reply, publicRelay, replyTo = listOf(parent)), { null }, isPublic)

        assertEquals(listOf(reply, relayList), pushed)
    }

    @Test
    fun sharingAProfileCopiesWhatIsCachedOfIt() {
        assertEquals(listOf(relayList), ShareRelayPolicy.eventsToPush(author(), { null }, isPublic))
        assertEquals(listOf(profileNote.event, relayList), ShareRelayPolicy.eventsToPush(author(), { profileNote }, isPublic))
    }

    @Test
    fun theShareRelayIsBrainstorms() {
        assertEquals(setOf(Constants.brainstorm), ShareSite.RELAYS)
        assertEquals("wss://search.brainstorm.world/", Constants.brainstorm.url)
    }

    @Test
    fun shareLinksAreBareBech32OnBrainstorm() {
        assertEquals("https://brainstorm.world/nevent1abc", ShareSite.link("nevent1abc"))
        assertEquals("https://brainstorm.world/nprofile1abc", ShareSite.link("nprofile1abc"))
    }
}
