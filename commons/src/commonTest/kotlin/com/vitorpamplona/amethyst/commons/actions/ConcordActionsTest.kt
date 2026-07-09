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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcordActionsTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun createFoldSendReadRoundTrip() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Test Server", createdAt = 1L, relays = listOf("wss://r.example"))

            // Fold genesis -> live state
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.ownerPubKey)
            assertEquals("Test Server", state.metadata?.name)
            assertTrue(state.channels.containsKey(community.generalChannelIdHex))

            // Send + read a channel message
            val channel = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            val wrap = ConcordActions.buildChannelMessage(owner, channel, community.generalChannelIdHex, community.rootEpoch, "hello world", createdAt = 2L)
            val msgs = ConcordActions.channelMessages(listOf(wrap), channel, community.generalChannelIdHex, community.rootEpoch)
            assertEquals(1, msgs.size)
            assertEquals("hello world", msgs[0].content)
            assertEquals(owner.pubKey, msgs[0].author)
        }

    @Test
    fun inviteMintParseAndOpen() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val invite =
                ConcordActions.inviteFor(
                    communityIdHex = community.communityIdHex,
                    ownerPubKey = community.ownerPubKey,
                    ownerSaltHex = community.ownerSalt.toHex(),
                    communityRootHex = community.communityRoot.toHex(),
                    rootEpoch = community.rootEpoch,
                    name = "Nostrichs",
                    relays = listOf("wss://r.example"),
                )
            val minted = ConcordActions.mintInviteLink("https://vector.chat", invite, createdAt = 1L)

            val parsed = ConcordActions.parseInviteLink(minted.url)
            assertNotNull(parsed)
            val opened = ConcordActions.openBundle(minted.bundleEvent, parsed.fragment.token)
            assertNotNull(opened)
            assertEquals(community.communityIdHex, opened.communityId)

            // The joiner can derive the control plane and read the genesis.
            val controlPlane = ConcordActions.controlPlaneFor(opened)
            val state = ConcordActions.foldCommunity(community.genesisWraps, controlPlane, opened.owner)
            assertEquals("Nostrichs", state.metadata?.name)
        }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
