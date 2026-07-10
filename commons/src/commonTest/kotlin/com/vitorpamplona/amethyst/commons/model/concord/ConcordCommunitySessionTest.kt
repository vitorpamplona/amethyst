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

            val session = ConcordCommunitySession(entry, owner.pubKey)
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

            // A channel message wrap routes to #general's flow.
            val msgWrap = ConcordActions.buildChannelMessage(owner, general, community.generalChannelIdHex, community.rootEpoch, "gm all", 2L)
            assertTrue(session.ingest(msgWrap))
            val msgs = session.messagesFlow(community.generalChannelIdHex).value
            assertEquals(1, msgs.size)
            assertEquals("gm all", msgs[0].content)
            assertEquals(owner.pubKey, msgs[0].author)

            // A stray wrap from a different community is ignored.
            val outsider = ConcordCommunityFactory.create(owner, "Other", createdAt = 1L, relays = listOf("wss://r.example"))
            assertTrue(!session.ingest(outsider.genesisWraps.first()))
        }
}
