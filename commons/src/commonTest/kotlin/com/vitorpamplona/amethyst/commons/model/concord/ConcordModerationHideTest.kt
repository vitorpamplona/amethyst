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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Moderation hides (CORD-03 §3) end to end, from the wrap to the store: a hide reaches only the
 * Chat rumors of its own community's channel, applies to a target that arrives after it, applies
 * once the hider's Grant folds, and keeps a hidden message out of the store across re-projections.
 */
class ConcordModerationHideTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val mod = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())

    private fun entryOf(c: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = c.communityIdHex,
            owner = c.ownerPubKey,
            ownerSalt = c.ownerSalt.toHexKey(),
            root = c.communityRoot.toHexKey(),
            rootEpoch = c.rootEpoch,
            controlPk = c.controlPkHex,
            relays = listOf("wss://r.example"),
            name = "Guild",
        )

    /** A session whose sink lands rumors in [cache] and sweeps on a delete, as the account's does. */
    private fun sessionInto(
        cache: EventCache,
        c: NewConcordCommunity,
    ): ConcordCommunitySession {
        lateinit var session: ConcordCommunitySession
        session =
            ConcordCommunitySession(entryOf(c), owner.pubKey) { communityId, channelIdHex, rumor, seen ->
                cache.consumeConcordRumor(communityId, channelIdHex, rumor, seen)
                if (rumor is DeletionRequestEvent) ConcordModerationHideSweep.sweep(cache, session, channelIdHex, rumor.deleteEventIds())
            }
        return session
    }

    private fun general(c: NewConcordCommunity): GroupKey = ConcordActions.publicChannel(c.communityRoot, c.generalChannelId, c.rootEpoch)

    private fun message(
        c: NewConcordCommunity,
        text: String,
        createdAt: Long,
    ): Event = ChannelChat.message(member.pubKey, c.generalChannelIdHex, c.rootEpoch, text, createdAt)

    private suspend fun wrap(
        rumor: Event,
        plane: GroupKey,
        signer: NostrSigner,
    ): Event = ConcordStreamEnvelope.wrap(rumor, plane, signer, encrypted = true)

    /** A kind 5 by [hider] on [c]'s general channel naming [targetIds], with [citation] as its `vac`. */
    private suspend fun hide(
        c: NewConcordCommunity,
        hider: NostrSigner,
        targetIds: List<String>,
        createdAt: Long,
        citation: AuthorityCitation? = null,
    ): Event {
        val base = ChannelChat.delete(hider.pubKey, c.generalChannelIdHex, c.rootEpoch, targetIds.map { ChannelChat.message(hider.pubKey, c.generalChannelIdHex, c.rootEpoch, it, 1L) }, createdAt)
        val tags = base.tags.filter { it[0] != "e" }.toTypedArray() + targetIds.map { arrayOf("e", it) } + listOfNotNull(citation?.let(VacTag::assemble))
        val rumor = RumorAssembler.assembleRumor<DeletionRequestEvent>(hider.pubKey, createdAt, DeletionRequestEvent.KIND, tags, "")
        return wrap(rumor, general(c), hider)
    }

    private fun inChannel(
        cache: EventCache,
        c: NewConcordCommunity,
        id: String,
    ): Boolean =
        cache
            .getOrCreateConcordChannel(ConcordChannelId(c.communityIdHex, c.generalChannelIdHex))
            .notes
            .get(id) != null

    @Test
    fun aHideReachesOnlyItsOwnChannelsRumors() =
        runTest {
            val cache = EventCache()
            val a = ConcordCommunityFactory.create(owner, "A", createdAt = 1L, relays = listOf("wss://r.example"))
            val b = ConcordCommunityFactory.create(owner, "B", createdAt = 1L, relays = listOf("wss://r.example"))
            val sessionA = sessionInto(cache, a)
            a.genesisWraps.forEach { sessionA.ingest(it) }

            val inA = message(a, "in A", 10L)
            sessionA.ingest(wrap(inA, general(a), member))
            assertTrue(inChannel(cache, a, inA.id))

            // The member's notes elsewhere: a public note, a public NIP-C7 chat, a NIP-17 DM, and a
            // message of another community the owner also moderates.
            val publicNote = member.sign(TextNoteEvent.build("a public note"))
            val publicChat = member.sign(ChatEvent.build("a public chat"))
            val dm = RumorAssembler.assembleRumor<ChatMessageEvent>(member.pubKey, ChatMessageEvent.build("a dm", listOf(PTag(owner.pubKey))))
            cache.justConsume(publicNote, null, true)
            cache.justConsume(publicChat, null, true)
            cache.justConsume(dm, null, true)
            val inB = message(b, "in B", 10L)
            cache.consumeConcordRumor(b.communityIdHex, b.generalChannelIdHex, inB)
            val elsewhere = listOf(publicNote.id, publicChat.id, dm.id, inB.id)
            elsewhere.forEach { assertNotNull(cache.getNoteIfExists(it)?.event, "precondition: $it is cached") }

            // The owner outranks the member, so the hide is honored, but only for A's own message.
            sessionA.ingest(hide(a, owner, elsewhere + inA.id, 20L))

            assertFalse(inChannel(cache, a, inA.id), "the channel's own message is hidden")
            elsewhere.forEach { assertNotNull(cache.getNoteIfExists(it)?.event, "a hide on A's channel must not touch $it") }
        }

    @Test
    fun aHideThatArrivesBeforeItsTargetStillApplies() =
        runTest {
            val cache = EventCache()
            val c = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = sessionInto(cache, c)
            c.genesisWraps.forEach { session.ingest(it) }

            val target = message(c, "later", 10L)
            session.ingest(hide(c, owner, listOf(target.id), 20L))
            session.ingest(wrap(target, general(c), member))

            assertNull(cache.getNoteIfExists(target.id)?.event, "the message never reaches the store")
            assertFalse(inChannel(cache, c, target.id))

            // An unrelated message of the same author is untouched.
            val other = message(c, "fine", 11L)
            session.ingest(wrap(other, general(c), member))
            assertTrue(inChannel(cache, c, other.id))
        }

    @Test
    fun aHideAppliesOnceItsHidersGrantFolds() =
        runTest {
            val cache = EventCache()
            val c = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = sessionInto(cache, c)
            c.genesisWraps.forEach { session.ingest(it) }

            val controlWraps = c.genesisWraps.toMutableList()
            val roleId = ByteArray(32) { 7 }
            val role = RoleEntity(name = "Mod", position = 5, permissions = ConcordPermissions.of(ConcordPermissions.MANAGE_MESSAGES).toWire())
            controlWraps += ConcordModeration.defineRole(owner, c.controlPlane, c.communityId, roleId, role, ConcordActions.controlEditions(controlWraps, c.controlPlane), 2L, owner = c.ownerPubKey)
            val grant = ConcordModeration.grant(owner, c.controlPlane, c.communityId, mod.pubKey, listOf(roleId.toHexKey()), ConcordActions.controlEditions(controlWraps, c.controlPlane), 3L, owner = c.ownerPubKey)
            controlWraps += grant
            val citation = ConcordCommunityState.fold(ConcordActions.controlEditions(controlWraps, c.controlPlane), c.communityId, c.ownerPubKey).authority.citationFor(mod.pubKey)
            assertNotNull(citation)

            val target = message(c, "rude", 10L)
            session.ingest(wrap(target, general(c), member))
            session.ingest(hide(c, mod, listOf(target.id), 20L, citation))
            assertTrue(inChannel(cache, c, target.id), "the moderator's Grant has not folded yet")

            // The Role and Grant fold; the next sweep (the account runs it on every fold) hides it.
            controlWraps.drop(c.genesisWraps.size).forEach { session.ingest(it) }
            assertTrue(session.isHiddenByModeration(c.generalChannelIdHex, target.id, member.pubKey))
            // A kind 5 naming something else sweeps only what it names.
            assertTrue(ConcordModerationHideSweep.sweep(cache, session, c.generalChannelIdHex, listOf("f".repeat(64))).isEmpty())
            assertTrue(inChannel(cache, c, target.id))
            ConcordModerationHideSweep.sweep(cache, session)
            assertFalse(inChannel(cache, c, target.id))
            assertNull(cache.getNoteIfExists(target.id)?.event)

            // Delivered again (a backfill page, a re-projection): still hidden.
            session.ingest(wrap(target, general(c), member))
            assertNull(cache.getNoteIfExists(target.id)?.event)
        }

    @Test
    fun aMemberCannotHideAnotherMembersMessage() =
        runTest {
            val cache = EventCache()
            val c = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = sessionInto(cache, c)
            c.genesisWraps.forEach { session.ingest(it) }
            val target = message(c, "mine", 10L)
            session.ingest(wrap(target, general(c), member))
            session.ingest(hide(c, mod, listOf(target.id), 20L))
            assertTrue(inChannel(cache, c, target.id))
        }

    @Test
    fun aReplacementSessionKeepsTheHides() =
        runTest {
            val cache = EventCache()
            val c = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val old = sessionInto(cache, c)
            c.genesisWraps.forEach { old.ingest(it) }
            val target = message(c, "gone", 10L)
            old.ingest(hide(c, owner, listOf(target.id), 20L))

            val fresh = sessionInto(cache, c)
            fresh.carryModerationHides(old.heldModerationHides())
            c.genesisWraps.forEach { fresh.ingest(it) }
            fresh.ingest(wrap(target, general(c), member))
            assertNull(cache.getNoteIfExists(target.id)?.event)
        }

    @Test
    fun aHideComesUndoneWhenItsHiderLosesTheBit() =
        runTest {
            val cache = EventCache()
            val c = ConcordCommunityFactory.create(owner, "Guild", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = sessionInto(cache, c)
            c.genesisWraps.forEach { session.ingest(it) }

            val controlWraps = c.genesisWraps.toMutableList()
            val roleId = ByteArray(32) { 7 }
            val role = RoleEntity(name = "Mod", position = 5, permissions = ConcordPermissions.of(ConcordPermissions.MANAGE_MESSAGES).toWire())
            controlWraps += ConcordModeration.defineRole(owner, c.controlPlane, c.communityId, roleId, role, ConcordActions.controlEditions(controlWraps, c.controlPlane), 2L, owner = c.ownerPubKey)
            controlWraps += ConcordModeration.grant(owner, c.controlPlane, c.communityId, mod.pubKey, listOf(roleId.toHexKey()), ConcordActions.controlEditions(controlWraps, c.controlPlane), 3L, owner = c.ownerPubKey)
            controlWraps.drop(c.genesisWraps.size).forEach { session.ingest(it) }
            val citation =
                session.state.value!!
                    .authority
                    .citationFor(mod.pubKey)
            assertNotNull(citation)

            // One target was in the store before the hide (swept out), one arrives after it (never stored).
            val before = message(c, "rude", 10L)
            session.ingest(wrap(before, general(c), member))
            val after = message(c, "ruder", 11L)
            session.ingest(hide(c, mod, listOf(before.id, after.id), 20L, citation))
            session.ingest(wrap(after, general(c), member))
            assertFalse(inChannel(cache, c, before.id))
            assertFalse(inChannel(cache, c, after.id))

            // The owner strips the moderator's Role: both messages come back (CORD-03 §3).
            session.ingest(ConcordModeration.grant(owner, c.controlPlane, c.communityId, mod.pubKey, emptyList(), ConcordActions.controlEditions(controlWraps, c.controlPlane), 30L, owner = c.ownerPubKey))
            assertFalse(session.isHiddenByModeration(c.generalChannelIdHex, before.id, member.pubKey))
            assertTrue(inChannel(cache, c, before.id), "a hide by someone stripped of the bit comes undone")
            assertTrue(inChannel(cache, c, after.id), "also for a target that arrived after the hide")
        }
}
