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
package com.vitorpamplona.quartz.nip51Lists.relayLists

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.RelayTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relays
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag as RelaySetRelayTag

/**
 * Every NIP-51 relay list shares the same update path, so every one of them used to move the
 * relays another client had left in plain tags into the encrypted content -- blanking the list
 * out for that client. See https://github.com/vitorpamplona/amethyst/issues/4075
 */
class RelayListPublicEntriesTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    private val damus = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    private val nosLol = RelayUrlNormalizer.normalize("wss://nos.lol")

    private suspend inline fun <reified T : Event> publicListFromAnotherClient(
        kind: Int,
        tagName: String = RelayTag.TAG_NAME,
    ): T =
        signer.sign(
            EventTemplate<T>(
                createdAt = 1740669816,
                kind = kind,
                tags = arrayOf(arrayOf(tagName, damus.url), arrayOf(tagName, nosLol.url)),
                content = "",
            ),
        )

    @Test
    fun relayFeedsListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<RelayFeedsListEvent>(RelayFeedsListEvent.KIND)
            val after = RelayFeedsListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun blockedRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<BlockedRelayListEvent>(BlockedRelayListEvent.KIND)
            val after = BlockedRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun trustedRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<TrustedRelayListEvent>(TrustedRelayListEvent.KIND)
            val after = TrustedRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun broadcastRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<BroadcastRelayListEvent>(BroadcastRelayListEvent.KIND)
            val after = BroadcastRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun indexerRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<IndexerRelayListEvent>(IndexerRelayListEvent.KIND)
            val after = IndexerRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun proxyRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<ProxyRelayListEvent>(ProxyRelayListEvent.KIND)
            val after = ProxyRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun searchRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<SearchRelayListEvent>(SearchRelayListEvent.KIND)
            val after = SearchRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun privateOutboxRelayListKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<PrivateOutboxRelayListEvent>(PrivateOutboxRelayListEvent.KIND)
            val after = PrivateOutboxRelayListEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.tags.relays())
        }

    @Test
    fun relaySetKeepsTheSurvivorPublic() =
        runTest {
            val before = publicListFromAnotherClient<RelaySetEvent>(RelaySetEvent.KIND, RelaySetRelayTag.TAG_NAME)
            val after = RelaySetEvent.updateRelayList(before, listOf(damus), signer, 1740669817)
            assertEquals(listOf(damus), after.relays())
        }
}
