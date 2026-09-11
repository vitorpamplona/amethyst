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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayFeedsListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    private val damus = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    private val nosLol = RelayUrlNormalizer.normalize("wss://nos.lol")
    private val primal = RelayUrlNormalizer.normalize("wss://relay.primal.net")

    @Test
    fun kindMatchesSpec() {
        assertEquals(10012, RelayFeedsListEvent.KIND)
    }

    /**
     * Reproduces https://github.com/vitorpamplona/amethyst/issues/4075: a 10012 written by
     * another client (public `relay` tags) must not be emptied out when Amethyst removes one
     * entry from it.
     */
    @Test
    fun removingOneRelayKeepsTheOtherPublicEntriesPublic() =
        runTest {
            val fromAnotherClient =
                signer.sign<RelayFeedsListEvent>(
                    RelayFeedsListEvent.build(
                        publicRelays = listOf(damus, nosLol),
                        signer = signer,
                        createdAt = 1740669816,
                    ),
                )

            assertEquals(listOf(damus, nosLol), fromAnotherClient.publicRelays())

            val afterRemoval =
                RelayFeedsListEvent.updateRelayList(
                    earlierVersion = fromAnotherClient,
                    relays = listOf(damus),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(
                listOf(damus),
                afterRemoval.publicRelays(),
                "the surviving relay must stay in the public tags so other clients still see it",
            )
            assertEquals(listOf(damus), afterRemoval.decryptRelays(signer))
        }

    @Test
    fun privateRelaysStayPrivate() =
        runTest {
            val mixed =
                signer.sign<RelayFeedsListEvent>(
                    RelayFeedsListEvent.build(
                        publicRelays = listOf(damus),
                        privateRelays = listOf(nosLol, primal),
                        signer = signer,
                        createdAt = 1740669816,
                    ),
                )

            val afterRemoval =
                RelayFeedsListEvent.updateRelayList(
                    earlierVersion = mixed,
                    relays = listOf(damus, nosLol),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(listOf(damus), afterRemoval.publicRelays())
            assertEquals(listOf(nosLol), afterRemoval.decryptPrivateRelays(signer))
        }

    @Test
    fun newRelaysFollowTheConventionOfTheList() =
        runTest {
            val publicOnly =
                signer.sign<RelayFeedsListEvent>(
                    RelayFeedsListEvent.build(
                        publicRelays = listOf(damus),
                        signer = signer,
                        createdAt = 1740669816,
                    ),
                )

            val afterAdd =
                RelayFeedsListEvent.updateRelayList(
                    earlierVersion = publicOnly,
                    relays = listOf(damus, nosLol),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertEquals(
                listOf(damus, nosLol),
                afterAdd.publicRelays(),
                "a list another client keeps public should keep receiving public entries",
            )

            val privateOnly =
                signer.sign<RelayFeedsListEvent>(
                    RelayFeedsListEvent.build(
                        privateRelays = listOf(damus),
                        signer = signer,
                        createdAt = 1740669816,
                    ),
                )

            val afterPrivateAdd =
                RelayFeedsListEvent.updateRelayList(
                    earlierVersion = privateOnly,
                    relays = listOf(damus, nosLol),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertTrue(afterPrivateAdd.publicRelays().isEmpty(), "a private list must stay private")
            assertEquals(listOf(damus, nosLol), afterPrivateAdd.decryptPrivateRelays(signer))
        }

    @Test
    fun keepsUnrelatedTags() =
        runTest {
            val withTitle =
                signer.sign<RelayFeedsListEvent>(
                    RelayFeedsListEvent.build(
                        publicRelays = listOf(damus, nosLol),
                        signer = signer,
                        createdAt = 1740669816,
                    ) {
                        add(arrayOf("title", "My Feeds"))
                    },
                )

            val afterRemoval =
                RelayFeedsListEvent.updateRelayList(
                    earlierVersion = withTitle,
                    relays = listOf(nosLol),
                    signer = signer,
                    createdAt = 1740669817,
                )

            assertTrue(
                afterRemoval.tags.any { it.size >= 2 && it[0] == "title" && it[1] == "My Feeds" },
                "tags this client doesn't understand must survive the update",
            )
        }
}
