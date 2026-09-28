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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIP-51: kind 10012 may point to kind:30002 relay sets and kind 10015 to kind:30015 interest
 * sets. Those `a` pointers must be readable and must survive edits of the relays/hashtags.
 */
class SetPointersInStandardListsTest {
    private val keyPair = "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair()
    private val signer = NostrSignerInternal(keyPair)

    private val damus = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    private val nosLol = RelayUrlNormalizer.normalize("wss://nos.lol")

    private val relaySet = "30002:" + "a".repeat(64) + ":my-relays"
    private val interestSet = "30015:" + "b".repeat(64) + ":nostr"
    private val notASet = "30023:" + "b".repeat(64) + ":article"

    @Test
    fun favoriteRelayListReadsAndKeepsRelaySetPointers() =
        runTest {
            val before =
                signer.sign(
                    EventTemplate<FavoriteRelayListEvent>(
                        1740669816,
                        FavoriteRelayListEvent.KIND,
                        arrayOf(arrayOf("relay", damus.url), arrayOf("a", relaySet), arrayOf("a", notASet)),
                        "",
                    ),
                )
            assertEquals(listOf(Address.parse(relaySet)), before.publicRelaySets().map { it.address })

            val after = FavoriteRelayListEvent.updateRelayList(before, listOf(damus, nosLol), signer, 1740669817)
            assertEquals(listOf(Address.parse(relaySet)), after.decryptRelaySets(signer).map { it.address })
            assertEquals(setOf(damus, nosLol), after.decryptRelays(signer).toSet())
        }

    @Test
    fun interestListReadsAndKeepsInterestSetPointers() =
        runTest {
            val before =
                signer.sign(
                    EventTemplate<InterestListEvent>(
                        1740669816,
                        InterestListEvent.KIND,
                        arrayOf(arrayOf("t", "nostr"), arrayOf("a", interestSet), arrayOf("a", notASet)),
                        "",
                    ),
                )
            assertEquals(listOf(Address.parse(interestSet)), before.publicInterestSets().map { it.address })

            val added = InterestListEvent.add(before, "bitcoin", isPrivate = false, signer = signer, createdAt = 1740669817)
            val removed = InterestListEvent.remove(added, "nostr", signer, 1740669818)
            assertEquals(listOf(Address.parse(interestSet)), removed.publicInterestSets().map { it.address })
            assertEquals(listOf("bitcoin"), removed.publicHashtags())
        }

    @Test
    fun syncInterestListCreateKeepsPublicAndPrivateApart() =
        runTest {
            val event = InterestListEvent.create(listOf("public"), listOf("private"), NostrSignerSync(keyPair), 1740669816)
            assertEquals(listOf("public"), event.publicHashtags())
            assertEquals(listOf("private"), event.privateTags(signer)?.mapNotNull { if (it[0] == "t") it[1] else null })
        }
}
