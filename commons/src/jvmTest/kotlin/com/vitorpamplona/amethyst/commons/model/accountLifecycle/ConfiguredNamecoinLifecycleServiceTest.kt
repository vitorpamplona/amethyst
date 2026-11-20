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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.amethyst.commons.service.namecoin.ConfiguredNamecoinLifecycleService
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguredNamecoinLifecycleServiceTest {
    @Test
    fun configuredBackendPreservesRecordAndProvenance() =
        runTest {
            val server = ElectrumxServer("electrum.example", 50_002)
            var requestedName: String? = null
            var requestedServers = emptyList<ElectrumxServer>()
            var calls = 0
            val backend =
                object : IElectrumXClient {
                    override suspend fun nameShowWithFallback(
                        identifier: String,
                        servers: List<ElectrumxServer>,
                    ): NameShowResult {
                        calls++
                        requestedName = identifier
                        requestedServers = servers
                        return NameShowResult(
                            name = identifier,
                            value = AccountLifecycleTestFixtures.compromised,
                            txid = "b".repeat(64),
                            height = 123_456,
                            expiresIn = 42_000,
                        )
                    }
                }
            val settings = MutableStateFlow(NamecoinSettings(backend = NamecoinBackend.NAMECOIN_CORE_RPC))
            val service =
                ConfiguredNamecoinLifecycleService(
                    backendProvider = { backend },
                    serverListProvider = { listOf(server) },
                    settings = settings,
                    store = InMemoryLifecycleAssociationStore(),
                    scope = backgroundScope,
                )
            val request =
                AccountLifecycleRequest(
                    viewerNamespace = "viewer",
                    identifier = "d/example",
                    subjectPubkey = AccountLifecycleTestFixtures.old,
                    oldEvents = listOf(AccountLifecycleTestFixtures.event("old_root")),
                )
            val result = service.accountLifecycle.resolve(request, AccountLifecycleTestFixtures.metadata).result
            assertTrue(result.isCompromiseWarning)
            assertEquals("d/example", requestedName)
            assertEquals(listOf(server), requestedServers)
            assertEquals("b".repeat(64), result.provenance?.txid)
            assertEquals(123_456, result.provenance?.height)
            assertEquals(42_000, result.provenance?.expiresIn)
            assertEquals("NAMECOIN_CORE_RPC", result.provenance?.configuredBackend)
            assertEquals(listOf("d/example"), result.provenance?.dependencies)

            settings.value = settings.value.copy(customServers = listOf("other.example:50002"))
            runCurrent()
            service.accountLifecycle.resolve(request, AccountLifecycleTestFixtures.metadata)
            assertEquals(2, calls, "settings changes invalidate the positive raw cache")
        }
}
