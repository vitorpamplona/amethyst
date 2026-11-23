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

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Before the user's kind 10040 comes from the relays (and with no backup of it: a transient login
 * keeps none), the rank provider is unknown, not "none": "none" made the trust network on disk be
 * deleted and downloaded again a second later, on every launch.
 */
class TrustProviderListWaitTest {
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")

    private fun state(
        keys: KeyPair,
        scope: CoroutineScope,
        waitMs: Long,
    ): TrustProviderListState {
        val signer = NostrSignerInternal(keys)
        return TrustProviderListState(signer, LocalCache, TrustProviderListDecryptionCache(signer), scope, AccountSettings(keys), listWaitMs = waitMs)
    }

    @Test
    fun aListThatArrivesWhileWaitingResolvesToItsProvider() {
        val keys = KeyPair()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                val lists = state(keys, scope, waitMs = 60_000)
                Thread.sleep(300)
                assertNull(lists.resolvedRankProvider.value, "not loaded yet must read as unknown, not as no provider")

                val provider = ServiceProviderTag(ProviderTypes.rank, KeyPair().pubKey.toHexKeyString(), relay)
                LocalCache.justConsumeMyOwnEvent(TrustProviderListEvent.create(provider, isPrivate = false, signer = NostrSignerInternal(keys)))

                val resolved = withTimeout(5_000) { lists.resolvedRankProvider.first { it != null } }
                assertEquals(provider, resolved?.provider)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aUserWithNoListResolvesToNoneAfterTheWait() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                val lists = state(KeyPair(), scope, waitMs = 200)
                val resolved = withTimeout(5_000) { lists.resolvedRankProvider.first { it != null } }
                assertNull(resolved?.provider, "a user with no list resolves to no provider")
            }
        } finally {
            scope.cancel()
        }
    }
}

private fun ByteArray.toHexKeyString(): String = joinToString("") { "%02x".format(it) }
