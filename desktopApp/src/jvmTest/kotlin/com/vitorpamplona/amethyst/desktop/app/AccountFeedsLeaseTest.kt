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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.viewmodels.AccountFeeds
import com.vitorpamplona.marmot.quic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame

/** Leasing the feeds of an account whose scope already ended (a logout racing a screen). */
class AccountFeedsLeaseTest {
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = NostrClient(BasicOkHttpWebSocket.Builder(httpClient = { OkHttpClient() }, canDial = { false }), clientScope)

    private fun account(scope: CoroutineScope): Account {
        val me = KeyPair()
        return Account(
            settings = AccountSettings(keyPair = me),
            signer = NostrSignerInternal(me),
            geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.LackPermission) },
            nwcFilterAssembler = { NWCPaymentFilterAssembler(client) },
            cashuMintDirectoryFilterAssembler = { CashuMintDirectoryFilterAssembler(client) },
            cashuMintTransport = OkHttpMintTransport { OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = client,
            scope = scope,
            appVersion = "test",
            encryptionKeyCache = EncryptionKeyCache(),
            saveSettings = {},
            marmotNotifier = { MarmotGroupNotifier.None },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = InMemoryGeohashIdentityStore(),
            marmotStreamTransportFactory = { QuicAgentTextStreamTransport(parentScope = it, certificateValidator = JdkCertificateValidator()) },
        )
    }

    @AfterTest
    fun tearDown() = clientScope.cancel()

    @Test
    fun aLoggedOutAccountStillGetsALease() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val account = account(scope)
        scope.cancel()

        // Used to spin forever: the feeds' job was born complete, so they went cold before the
        // registry stored them, and every retry built another set.
        val lease = CompletableFuture.supplyAsync { AccountFeeds.lease(account, emptyFlow()) }.get(10, TimeUnit.SECONDS)
        lease.release()
    }

    @Test
    fun twoLeasesOnALiveAccountShareTheFeeds() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val account = account(scope)
        val first = AccountFeeds.lease(account, emptyFlow())
        val second = AccountFeeds.lease(account, emptyFlow())
        assertSame(first.states, second.states)
        first.release()
        second.release()
        scope.cancel()
    }
}
