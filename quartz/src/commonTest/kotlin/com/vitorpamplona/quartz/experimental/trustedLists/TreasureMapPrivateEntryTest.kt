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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.TrustedListProviderTag
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.publicTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.removeTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.replaceTrustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.trustedListProvider
import com.vitorpamplona.quartz.experimental.trustedLists.treasureMap.trustedListProviders
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A 10040 keeps half its delegations NIP-44 encrypted in `content` -- who you
 * trust to rank the network is itself sensitive -- so a Trusted List entry has
 * to work in both halves, and the one-entry-per-kind invariant has to hold
 * across them.
 */
class TreasureMapPrivateEntryTest {
    private val publisher = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val otherPublisher = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val nip85 = RelayUrlNormalizer.normalizeOrNull("wss://nip85.brainstorm.world")!!
    private val scores = RelayUrlNormalizer.normalizeOrNull("wss://scores.brainstorm.world")!!

    private val userList = TrustedListProviderTag(UserTrustedListEvent.KIND, null, publisher, nip85)

    private suspend fun emptyMap(signer: NostrSignerInternal) =
        TrustProviderListEvent.create(
            publicProviders = listOf(ServiceProviderTag(ProviderTypes.rank, publisher, scores)),
            privateProviders = listOf(ServiceProviderTag(ProviderTypes.followerCount, publisher, scores)),
            signer = signer,
        )

    @Test
    fun aPrivateEntryIsWrittenAndReadBack() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            val map = emptyMap(signer).replaceTrustedListProvider(userList, isPrivate = true, signer = signer)

            assertEquals(publisher, map.trustedListProvider(UserTrustedListEvent.KIND, signer)?.pubkey)
            // and it is genuinely private: the public half does not carry it
            assertNull(map.publicTrustedListProvider(UserTrustedListEvent.KIND))
        }

    @Test
    fun aPrivateEntryIsInvisibleWithoutTheOwnersSigner() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val stranger = NostrSignerInternal(KeyPair())

            val map = emptyMap(signer).replaceTrustedListProvider(userList, isPrivate = true, signer = signer)

            // the public half alone, rather than a failure -- same contract as
            // TrustProviderListEvent.privateTags
            assertNull(map.trustedListProvider(UserTrustedListEvent.KIND, stranger))
            assertEquals(emptyList(), map.trustedListProviders(stranger))
        }

    @Test
    fun movingADelegationBetweenHalvesLeavesNoStaleTwin() =
        runTest {
            // the invariant is one generic entry per kind across the WHOLE Map.
            // A twin left in the other half would be shadowed on read and
            // republished forever after
            val signer = NostrSignerInternal(KeyPair())

            val public = emptyMap(signer).replaceTrustedListProvider(userList, isPrivate = false, signer = signer)
            assertEquals(publisher, public.publicTrustedListProvider(UserTrustedListEvent.KIND)?.pubkey)

            val moved = public.replaceTrustedListProvider(userList, isPrivate = true, signer = signer)
            assertNull(moved.publicTrustedListProvider(UserTrustedListEvent.KIND), "the public twin must be gone")
            assertEquals(publisher, moved.trustedListProvider(UserTrustedListEvent.KIND, signer)?.pubkey)
            assertEquals(1, moved.trustedListProviders(signer).size)

            val back = moved.replaceTrustedListProvider(userList, isPrivate = false, signer = signer)
            assertEquals(publisher, back.publicTrustedListProvider(UserTrustedListEvent.KIND)?.pubkey)
            assertEquals(1, back.trustedListProviders(signer).size, "the private twin must be gone")
        }

    @Test
    fun switchingThePrivatePublisherReplacesRatherThanAccumulates() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            val map =
                emptyMap(signer)
                    .replaceTrustedListProvider(userList, isPrivate = true, signer = signer)
                    .replaceTrustedListProvider(userList.copy(pubkey = otherPublisher), isPrivate = true, signer = signer)

            assertEquals(listOf(otherPublisher), map.trustedListProviders(signer).map { it.pubkey })
        }

    @Test
    fun theOtherHalfSurvivesTheWriteVerbatim() =
        runTest {
            // 10040 is replaceable: whatever this write drops is gone for good
            val signer = NostrSignerInternal(KeyPair())

            val map = emptyMap(signer).replaceTrustedListProvider(userList, isPrivate = true, signer = signer)

            val providers = map.tags.toList().map { it.toList() } + (map.privateTags(signer) ?: emptyArray()).toList().map { it.toList() }
            assertTrue(providers.contains(listOf("30382:rank", publisher, scores.url)), "the public NIP-85 entry survived")
            assertTrue(providers.contains(listOf("30382:followers", publisher, scores.url)), "the private NIP-85 entry survived")
        }

    @Test
    fun removingClearsTheEntryFromEitherHalf() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            val map =
                emptyMap(signer)
                    .replaceTrustedListProvider(userList, isPrivate = true, signer = signer)
                    .replaceTrustedListProvider(
                        TrustedListProviderTag(EventTrustedListEvent.KIND, null, publisher, nip85),
                        isPrivate = false,
                        signer = signer,
                    )

            val cleared =
                map
                    .removeTrustedListProvider(UserTrustedListEvent.KIND, signer)
                    .removeTrustedListProvider(EventTrustedListEvent.KIND, signer)

            assertEquals(emptyList(), cleared.trustedListProviders(signer))
            // the NIP-85 delegations in both halves are untouched
            assertEquals(1, cleared.serviceProviders().size)
            assertEquals(1, (cleared.privateTags(signer) ?: emptyArray()).size)
        }

    @Test
    fun aPublicWriteRefusesRatherThanStrandAPrivateTwinItCannotRead() =
        runTest {
            // we cannot drop a private twin we cannot open, and writing anyway
            // would republish a Map that breaks the invariant
            val signer = NostrSignerInternal(KeyPair())
            val stranger = NostrSignerInternal(KeyPair())

            val map = emptyMap(signer)

            assertFailsWith<SignerExceptions.UnauthorizedDecryptionException> {
                map.replaceTrustedListProvider(userList, isPrivate = false, signer = stranger)
            }
        }

    @Test
    fun aMapWithNoPrivateHalfNeedsNoDecryption() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())

            val map =
                TrustProviderListEvent.create(
                    publicProviders = listOf(ServiceProviderTag(ProviderTypes.rank, publisher, scores)),
                    privateProviders = emptyList(),
                    signer = signer,
                )

            val updated = map.replaceTrustedListProvider(userList, isPrivate = false, signer = signer)

            assertEquals(publisher, updated.publicTrustedListProvider(UserTrustedListEvent.KIND)?.pubkey)
        }
}
