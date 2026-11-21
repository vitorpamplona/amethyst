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
import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelAccess
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A member's session over Armada's Roles v2 fixtures (`fixtures/cord25`, via Quartz): the access
 * plane's key deliveries (CORD-03 §5) are opened once and settled for good, and a restricted
 * Channel's writer-address posts (CORD-03 §1) land and stay pinnable.
 */
class ConcordRolesV2FixtureSessionTest {
    private class Fixture(
        name: String,
    ) {
        val json = load("$name.json")
        val actors = load("$name.actors.json")
        val community = json["community"]!!.jsonObject
        val controlPk = community["control_pk"]!!.jsonPrimitive.content
        val events: List<Event> = json["events"]!!.jsonArray.map { Event.fromJson(it.toString()) }.filter { it.kind == 1059 }

        fun actor(key: String) = actors[key]!!.jsonPrimitive.content

        val entry =
            ConcordCommunityListEntry(
                id = community["id"]!!.jsonPrimitive.content,
                owner = community["owner"]!!.jsonPrimitive.content,
                ownerSalt = community["owner_salt"]!!.jsonPrimitive.content,
                root = community["root"]!!.jsonPrimitive.content,
                rootEpoch = community["root_epoch"]!!.jsonPrimitive.long,
                controlPk = controlPk,
                relays = listOf("wss://r.example"),
                name = name,
            )

        private fun load(file: String): JsonObject = Json.parseToJsonElement(javaClass.getResourceAsStream("/concord/cord25/$file")!!.bufferedReader().readText()).jsonObject
    }

    @Test
    fun opensEachAccessDeliveryOnceAndSettlesItForGood() =
        runTest {
            val f = Fixture("members")
            val member = f.actor("member")
            val lounge = f.actor("lounge")
            val session = ConcordCommunitySession(f.entry, member)
            f.events.sortedByDescending { it.pubKey == f.controlPk }.forEach { session.ingest(it) }
            assertTrue(session.state.value!!.isRolesV2)

            val pending = session.pendingAccessDeliveries()
            assertTrue(pending.isNotEmpty(), "the lounge's access plane carries deliveries")
            val opens = session.accessOpens
            // A second drain (every structural tick runs one) opens nothing again.
            session.pendingAccessDeliveries()
            assertEquals(opens, session.accessOpens)

            val state = session.state.value!!
            val forMe =
                pending.filter { p ->
                    ConcordAccessDrain.verdict(p.delivery, p.channelIdHex, member, heldEpoch = null) {
                        ConcordChannelAccess.deliveryTrusted(state, p.channelIdHex, member, p.delivery.deliverer)
                    } == ConcordAccessDrain.Verdict.OPEN
                }
            val lounges = forMe.filter { it.channelIdHex == lounge }
            assertTrue(lounges.isNotEmpty(), "a lounge delivery is for this member and trusted")
            val signer = NostrSignerInternal(KeyPair(f.actor("member_secret").hexToByteArray()))
            assertNotNull(ConcordChannelAccess.openAccess(lounges.first().delivery, signer, lounge.hexToByteArray()))

            // Someone else's view: no blob at their locator, so nothing to wait for.
            val stranger = KeyPair().pubKey.toHexKey()
            lounges.forEach { p ->
                assertEquals(ConcordAccessDrain.Verdict.NOT_FOR_ME, ConcordAccessDrain.verdict(p.delivery, p.channelIdHex, stranger, null) { true })
            }
            // An epoch already held is stale.
            val first = lounges.first()
            assertEquals(ConcordAccessDrain.Verdict.STALE, ConcordAccessDrain.verdict(first.delivery, first.channelIdHex, member, first.delivery.newEpoch) { true })
            // Untrusted for now: kept for a later fold.
            assertEquals(ConcordAccessDrain.Verdict.WAIT, ConcordAccessDrain.verdict(first.delivery, first.channelIdHex, member, null) { false })

            // Settled deliveries leave the buffer, and a re-delivery is not buffered again.
            pending.forEach { session.retireAccessWrap(it.wrapId) }
            assertTrue(session.pendingAccessDeliveries().isEmpty())
            f.events.forEach { session.ingest(it) }
            assertTrue(session.pendingAccessDeliveries().isEmpty())
        }

    @Test
    fun landsAWritersPostAndKeepsItPinnable() =
        runTest {
            val f = Fixture("writers")
            val member = f.actor("member")
            val general = f.actor("general")
            val landed = mutableListOf<Event>()
            val session = ConcordCommunitySession(f.entry, member) { _, channel, rumor, _ -> if (channel == general) landed += rumor }
            f.events.sortedByDescending { it.pubKey == f.controlPk }.forEach { session.ingest(it) }

            val post = landed.firstOrNull { it.content == "a writer's post" }
            assertNotNull(post, "the writer's post stands and lands")
            // Pinning reopens the wrap that carried it, on its writer plane.
            assertNotNull(session.pinSource(general, post.id), "a writer-address post can be pinned")
        }

    @Test
    fun opensAnOlderWritersPostUnderTheReadKeyItWasPostedUnderAfterARefounding() =
        runTest {
            val f = Fixture("writers")
            val member = f.actor("member")
            val general = f.actor("general")
            val session = ConcordCommunitySession(f.entry, member)
            f.events.sortedByDescending { it.pubKey == f.controlPk }.forEach { session.ingest(it) }
            val state = session.state.value!!
            val window =
                state.writers
                    .getValue(general)
                    .windows
                    .single()
            val atWriter = f.events.filter { it.pubKey == window.writerPubKey }

            // A Refounding moved the root to epoch 1; the root of epoch 0, which the post was
            // encrypted under (CORD-03 §1: the read key in force when posted), is held.
            val refounded =
                ConcordCommunityListEntry(
                    id = f.entry.id,
                    owner = f.entry.owner,
                    ownerSalt = f.entry.ownerSalt,
                    root = KeyPair().privKey!!.toHexKey(),
                    rootEpoch = 1,
                    heldRoots = listOf(HeldRoot(epoch = 0, key = f.entry.root)),
                    relays = f.entry.relays,
                )
            val planes = ConcordActions.writerPlanes(refounded, state).filter { it.key.publicKeyHex == window.writerPubKey }
            val opened =
                atWriter.mapNotNull { wrap ->
                    planes.firstNotNullOfOrNull { plane ->
                        ConcordActions.openChannelRumorAnyExpiry(wrap, plane.key, plane.channelIdHex, plane.epoch, writerEpoch = plane.writerEpoch)
                    }
                }
            assertTrue(opened.any { it.content == "a writer's post" }, "the epoch-0 writer post still opens once the root moved on")
            // The current read key still serves the address too: a writer posting before the fresh
            // epoch is minted encrypts under the root now in force.
            assertTrue(planes.any { it.epoch == 1L }, "the address is also read under the current root")
            // One subscription per address, however many read keys open it.
            val subs = ConcordSubscriptionPlanner.channelPlaneSubs(refounded, state).filter { it.pubKeyHex == window.writerPubKey }
            assertEquals(1, subs.size)
        }

    @Test
    fun showsAWriterTypingAtTheWriterAddress() =
        runTest {
            val f = Fixture("writers")
            val member = f.actor("member")
            val general = f.actor("general")
            // The owner watches; the member, a writer, types where a restricted Channel's posts go.
            val session = ConcordCommunitySession(f.entry, f.actor("owner"))
            f.events.sortedByDescending { it.pubKey == f.controlPk }.forEach { session.ingest(it) }

            val writerEpoch = f.actors["writer_epoch"]!!.jsonPrimitive.long
            val writer = ConcordKeyDerivation.channelWriterKey(f.actor("writer_secret").hexToByteArray(), general.hexToByteArray(), writerEpoch)
            val read = ConcordActions.currentChannelPlane(f.entry, general, isPrivate = false)!!
            val stream = GroupKey(writer.secretKey, writer.publicKey, read.key.conversationKey)
            val now = TimeUtils.now()
            val typing = ChannelChat.typing(member, general, read.epoch, now)
            val tags = typing.tags + arrayOf(arrayOf("wepoch", writerEpoch.toString()), arrayOf("wcert", f.actor("writer_cert")))
            val rumor = RumorAssembler.assembleRumor<Event>(member, now, typing.kind, tags, "")
            val signer = NostrSignerInternal(KeyPair(f.actor("member_secret").hexToByteArray()))
            val wrap = ConcordStreamEnvelope.wrap(rumor, stream, signer, encrypted = true, ephemeral = true, createdAt = now)

            session.ingest(wrap)
            assertEquals(setOf(member), session.typing.value[general]?.keys, "a writer typing at the writer address shows")
        }
}
