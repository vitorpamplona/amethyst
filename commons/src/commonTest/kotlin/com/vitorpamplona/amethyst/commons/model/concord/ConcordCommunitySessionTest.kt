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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcordCommunitySessionTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun ingestsControlThenChannelWrapsIntoFlows() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )

            val captured = mutableListOf<Triple<String, String, com.vitorpamplona.quartz.nip01Core.core.Event>>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { communityId, channelIdHex, rumor -> captured += Triple(communityId, channelIdHex, rumor) }
            assertEquals(community.controlPlane.publicKeyHex, session.controlPlaneAddress)

            // Feed the genesis control wraps → state folds, channels + membership resolve.
            community.genesisWraps.forEach { assertTrue(session.ingest(it)) }
            val state = session.state.value
            assertEquals("Nostrichs", state?.metadata?.name)
            assertTrue(state!!.channels.containsKey(community.generalChannelIdHex))
            assertEquals(ConcordMembership.OWNER, session.membership())

            // The #general channel plane is now a known address.
            val general = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            assertTrue(session.channelAddresses().contains(general.publicKeyHex))

            // A channel message wrap decrypts and is emitted to the sink for #general.
            val msgWrap = ConcordActions.buildChannelMessage(owner, general, community.generalChannelIdHex, community.rootEpoch, "gm all", 2L)
            assertTrue(session.ingest(msgWrap))
            val general9 = captured.filter { it.second == community.generalChannelIdHex && it.third.content == "gm all" }
            assertEquals(1, general9.size)
            assertEquals(community.communityIdHex, general9[0].first)
            assertEquals(owner.pubKey, general9[0].third.pubKey)
            val message = general9[0].third

            // A reaction to that message decrypts as a kind-7 bound to the channel, e-tagging the target.
            val reactionWrap = ConcordActions.buildChannelReaction(owner, general, community.generalChannelIdHex, community.rootEpoch, message, "🤙", 3L)
            assertTrue(session.ingest(reactionWrap))
            val reaction = captured.map { it.third }.first { it.kind == 7 }
            assertEquals("🤙", reaction.content)
            assertEquals(message.id, reaction.tags.first { it[0] == "e" }[1])

            // A reply decrypts as a kind-1111 NIP-22 thread comment: uppercase `E` at the thread
            // root and lowercase `e` at the immediate parent (both the message here), still bound
            // to the channel so it groups into the message's thread — the shape Armada threads.
            val replyWrap = ConcordActions.buildChannelReply(owner, general, community.generalChannelIdHex, community.rootEpoch, message, "gm back", 4L)
            assertTrue(session.ingest(replyWrap))
            val reply = captured.map { it.third }.first { it.content == "gm back" }
            assertEquals(1111, reply.kind)
            assertEquals(message.id, reply.tags.first { it[0] == "E" }[1])
            assertEquals(message.id, reply.tags.first { it[0] == "e" }[1])
            assertTrue(ChannelChat.isBoundTo(reply, community.generalChannelIdHex, community.rootEpoch))

            // A stray wrap from a different community is ignored.
            val outsider = ConcordCommunityFactory.create(owner, "Other", createdAt = 1L, relays = listOf("wss://r.example"))
            assertTrue(!session.ingest(outsider.genesisWraps.first()))
        }
}
