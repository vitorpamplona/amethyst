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

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.nip64Chess.dal.ChessLobbyFeedFilter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.marmot.quic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The chess lobby is kept current, not rebuilt each time its screen comes back: a move reaches the
 * lobby as its game (ChessLobbyFeedFilter.applyFilter maps a move to its start), and the update
 * sorts the list again by activity, so a game someone just moved in rises without a rebuild.
 */
class ChessLobbyKeptCurrentTest {
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = NostrClient(BasicOkHttpWebSocket.Builder(httpClient = { OkHttpClient() }, canDial = { false }), clientScope)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val me = KeyPair()
    private val signer = NostrSignerInternal(me)

    private val account =
        Account(
            settings = AccountSettings(keyPair = me),
            signer = signer,
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

    @AfterTest
    fun tearDown() {
        scope.cancel()
        clientScope.cancel()
    }

    private fun publish(event: JesterEvent) = LocalCache.justConsume(event, null, true)

    private fun FeedContentState.shown(): List<String>? =
        (feedContent.value as? FeedState.Loaded)
            ?.feed
            ?.value
            ?.list
            ?.map { it.idHex }

    private fun waitUntil(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    @Test
    fun aMoveOnAnOlderGameRaisesItWithoutARebuild() {
        val now = TimeUtils.now()
        val older = runBlocking { signer.sign(JesterEvent.buildStart(Color.WHITE, createdAt = now - 300)) }
        val newer = runBlocking { signer.sign(JesterEvent.buildStart(Color.WHITE, createdAt = now - 200)) }
        publish(older)
        publish(newer)

        // "Mine", the default list: both games are the account's own.
        val feed = FeedContentState(ChessLobbyFeedFilter(account), scope, LocalCache)
        feed.invalidateData()
        waitUntil("the first build") { feed.shown() == listOf(newer.id, older.id) }

        val move = runBlocking { signer.sign(JesterEvent.buildMove(older.id, older.id, "e4", "fen", listOf("e4"), "", now - 100)) }
        publish(move)
        feed.updateFeedWith(setOf(LocalCache.getOrCreateNote(move.id)))

        waitUntil("the moved game to rise") { feed.shown() == listOf(older.id, newer.id) }
        assertEquals(listOf(older.id, newer.id), feed.shown())
    }
}
