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
package com.vitorpamplona.amethyst.commons.model.trustedAssertions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreProviderEditsTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")
    private val oldProvider = "a".repeat(64)
    private val newProvider = "b".repeat(64)
    private val eventProvider = ServiceProviderTag(ServiceType(30383, "rank"), "c".repeat(64), relay)

    private suspend fun existing() =
        TrustProviderListEvent.create(
            publicProviders = listOf(ServiceProviderTag(ProviderTypes.rank, oldProvider, relay), eventProvider),
            privateProviders = listOf(ServiceProviderTag(ProviderTypes.followerCount, oldProvider, relay)),
            signer = signer,
        )

    @Test
    fun replacesTheScoreProviderAndKeepsEverythingElse() =
        runBlocking {
            val updated = withScoreProvider(existing(), newProvider, relay, isPrivate = false, signer = signer)

            val public = updated.serviceProviders()
            assertTrue(eventProvider in public)
            assertEquals(newProvider, public.single { it.service == ProviderTypes.rank }.pubkey)
            assertEquals(newProvider, public.single { it.service == ProviderTypes.followerCount }.pubkey)
            // The old private followers entry is gone; nothing else was private.
            assertTrue(updated.privateTags(signer).orEmpty().isEmpty())
            assertEquals(newProvider, updated.rankProvider(signer)?.pubkey)
        }

    @Test
    fun privateEntriesStayPrivate() =
        runBlocking {
            val updated = withScoreProvider(null, newProvider, relay, isPrivate = true, signer = signer)
            assertTrue(updated.serviceProviders().isEmpty())
            assertNull(updated.rankProvider(null))
            assertEquals(newProvider, updated.rankProvider(signer)?.pubkey)
        }

    @Test
    fun removingTheProviderKeepsOtherEntries() =
        runBlocking {
            val removed = assertNotNull(withoutScoreProvider(existing(), signer))
            assertEquals(listOf(eventProvider), removed.serviceProviders())
            assertNull(removed.rankProvider(signer))
            assertNull(withoutScoreProvider(removed, signer))
        }
}
