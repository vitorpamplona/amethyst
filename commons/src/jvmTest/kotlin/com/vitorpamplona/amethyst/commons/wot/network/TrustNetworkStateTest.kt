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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCodec
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustNetworkStateTest {
    private val random = Random(7)
    private val providerKey = hex()
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")
    private val provider = ServiceProviderTag(ProviderTypes.rank, providerKey, relay)
    private val dir: Path = (System.getProperty("java.io.tmpdir") + "/wot-state-" + System.nanoTime()).toPath()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val trusted = hex()
    private val lowRank = hex()

    private fun hex() = Hex.encode(random.nextBytes(32))

    private fun card(
        subject: String,
        rank: Int,
    ) = Event(hex(), providerKey, 1000, 30382, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "", "0".repeat(128))

    private fun writeIndex(providerOnDisk: String = providerKey) {
        val builder = TrustNetworkBuilder(providerOnDisk)
        builder.add(card(trusted, 40).let { Event(it.id, providerOnDisk, it.createdAt, it.kind, it.tags, it.content, it.sig) })
        builder.add(card(lowRank, 2).let { Event(it.id, providerOnDisk, it.createdAt, it.kind, it.tags, it.content, it.sig) })
        val (index, ids) = builder.build()
        val now = System.currentTimeMillis() / 1000
        FileSystem.SYSTEM.createDirectories(dir)
        FileSystem.SYSTEM.write(dir / TrustNetworkState.IDS_FILE) { write(TrustNetworkCodec.encodeIds(ids)) }
        FileSystem.SYSTEM.write(dir / TrustNetworkState.INDEX_FILE) {
            write(TrustNetworkCodec.encodeIndex(TrustNetworkHeader(providerOnDisk, relay.url, 1000, now, now), index))
        }
    }

    private fun state(
        rankProvider: MutableStateFlow<ServiceProviderTag?>,
        minScore: MutableStateFlow<Int> = MutableStateFlow(5),
    ) = TrustNetworkState(
        rankProvider = rankProvider,
        minTrustScore = minScore,
        directory = dir,
        // No client: these tests cover loading and querying, not syncing.
        clientBuilder = null,
        scope = scope,
        providerGraceMs = 50,
    )

    @AfterTest
    fun cleanup() {
        scope.cancel()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test
    fun loadsTheIndexOnDiskAndAppliesTheMinimumScore() =
        runBlocking {
            writeIndex()
            val minScore = MutableStateFlow(5)
            val wot = state(MutableStateFlow(provider), minScore)
            wot.awaitLoaded()

            assertTrue(wot.isActive)
            assertTrue(wot.passes(trusted))
            assertFalse(wot.passes(lowRank))
            assertFalse(wot.passes(hex()))
            assertEquals(40, wot.rankOf(trusted))

            minScore.value = 1
            assertTrue(wot.passes(lowRank))
        }

    @Test
    fun keepsTheIndexWhileTheProviderListIsStillLoading() =
        runBlocking {
            writeIndex()
            val rankProvider = MutableStateFlow<ServiceProviderTag?>(null)
            val wot = state(rankProvider)
            wot.awaitLoaded()
            // Startup: the 10040 has not been read yet, so a push can still be filtered.
            assertTrue(wot.isActive)

            // The provider never shows up: filtering turns off after the grace period.
            withTimeout(5_000) { wot.network.first { it == null } }
            assertFalse(wot.isActive)
            assertFalse(wot.passes(trusted))
        }

    @Test
    fun ignoresAnIndexFromAnotherProvider() =
        runBlocking {
            writeIndex(providerOnDisk = hex())
            val wot = state(MutableStateFlow(provider))
            wot.awaitLoaded()
            withTimeout(5_000) { wot.network.first { it == null } }
            assertNull(wot.rankOf(trusted))
        }

    @Test
    fun withoutAFileNothingIsFiltered() =
        runBlocking {
            val wot = state(MutableStateFlow(provider))
            wot.awaitLoaded()
            assertFalse(wot.isActive)
            assertFalse(wot.passes(trusted))
        }

    @Test
    fun explainsEveryVerdict() =
        runBlocking {
            writeIndex()
            val wot = state(MutableStateFlow(provider))
            wot.awaitReady()
            val me = hex()
            val friend = hex()
            assertEquals(TrustVerdict.SELF, wot.explain(me, me, setOf(friend)))
            assertEquals(TrustVerdict.FOLLOW, wot.explain(friend, me, setOf(friend)))
            assertEquals(TrustVerdict.TRUSTED, wot.explain(trusted, me, emptySet()))
            assertEquals(TrustVerdict.BELOW_MIN_SCORE, wot.explain(lowRank, me, emptySet()))
            assertEquals(TrustVerdict.NOT_IN_NETWORK, wot.explain(hex(), me, emptySet()))
            assertEquals(false, TrustVerdict.NOT_IN_NETWORK.isKnown)
        }

    @Test
    fun withoutANetworkTheVerdictIsNeutral() =
        runBlocking {
            val wot = state(MutableStateFlow(provider))
            wot.awaitReady()
            assertEquals(TrustVerdict.NO_NETWORK, wot.explain(hex(), hex(), emptySet()))
            assertNull(TrustVerdict.NO_NETWORK.isKnown)
        }
}
